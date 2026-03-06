package org.example.trans_java_demo.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.trans_java_demo.service.ProxyService;
import org.example.trans_java_demo.util.MultipartRequestHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@RestController
@RequestMapping("/proxy")
public class ProxyController {

    private final ProxyService proxyService;

    public ProxyController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @RequestMapping(value = "/**", method = {
            RequestMethod.GET,
            RequestMethod.POST,
            RequestMethod.PUT,
            RequestMethod.DELETE,
            RequestMethod.PATCH,
            RequestMethod.HEAD,
            RequestMethod.OPTIONS
    })
    public ResponseEntity<byte[]> proxyRequest(HttpServletRequest request) throws Exception {
        byte[] body;

        // 针对 multipart 请求特殊处理
        if (MultipartRequestHandler.isMultipartRequest(request)) {
            // 重建 multipart body 并获取新的 boundary
            MultipartRequestHandler.MultipartBody multipartBody =
                    MultipartRequestHandler.rebuildMultipartBodyWithBoundary(request);
            body = multipartBody.getBody();
            // 将新的 Content-Type（包含 boundary）传递给 ProxyService
            return proxyService.forwardRequest(request, body, multipartBody.getContentType());
        } else {
            // 其他类型请求直接读取 InputStream
            body = request.getInputStream().readAllBytes();
            return proxyService.forwardRequest(request, body);
        }
    }

    @GetMapping(value = "/stream/**")
    public ResponseEntity<StreamingResponseBody> proxyStreamRequest(HttpServletRequest request) {
        return handleStreamRequest(request, null, null);
    }

    @PostMapping(value = "/stream/**")
    public ResponseEntity<StreamingResponseBody> proxyStreamPostRequest(HttpServletRequest request) throws Exception {
        byte[] body;
        String overrideContentType = null;

        // 针对 multipart 请求特殊处理
        if (MultipartRequestHandler.isMultipartRequest(request)) {
            MultipartRequestHandler.MultipartBody multipartBody =
                    MultipartRequestHandler.rebuildMultipartBodyWithBoundary(request);
            body = multipartBody.getBody();
            overrideContentType = multipartBody.getContentType();
        } else {
            body = request.getInputStream().readAllBytes();
        }

        return handleStreamRequest(request, body, overrideContentType);
    }

    private ResponseEntity<StreamingResponseBody> handleStreamRequest(
            HttpServletRequest request,
            byte[] body,
            String overrideContentType
    ) {
        try {
            ProxyService.StreamResponse streamResponse =
                    proxyService.forwardStreamRequest(request, body, overrideContentType);
            boolean isSseResponse = isSseResponse(streamResponse.getHeaders());

            StreamingResponseBody responseBody = outputStream -> {
                try (var inputStream = streamResponse.getInputStream()) {
                    if (isSseResponse) {
                        writeSseEventByEvent(inputStream, outputStream);
                    } else {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                            outputStream.flush();
                        }
                    }
                }
            };

            HttpHeaders headers = new HttpHeaders();
            headers.putAll(streamResponse.getHeaders());
            if (isSseResponse) {
                headers.remove(HttpHeaders.CONTENT_LENGTH);
                headers.setCacheControl("no-cache");
                headers.set("X-Accel-Buffering", "no");
            }

            return ResponseEntity
                    .status(streamResponse.getStatusCode())
                    .headers(headers)
                    .body(responseBody);
        } catch (Exception e) {
            byte[] errorBody = ("Proxy stream error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(outputStream -> outputStream.write(errorBody));
        }
    }

    private boolean isSseResponse(HttpHeaders headers) {
        String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
        return contentType != null && contentType.toLowerCase().startsWith("text/event-stream");
    }

    private void writeSseEventByEvent(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] readBuffer = new byte[2048];
        byte[] pending = new byte[0];
        int bytesRead;

        while ((bytesRead = inputStream.read(readBuffer)) != -1) {
            pending = appendBytes(pending, readBuffer, bytesRead);

            int delimiterEndIndex;
            while ((delimiterEndIndex = findSseEventDelimiterEndIndex(pending)) != -1) {
                outputStream.write(pending, 0, delimiterEndIndex);
                outputStream.flush();
                pending = Arrays.copyOfRange(pending, delimiterEndIndex, pending.length);
            }
        }

        if (pending.length > 0) {
            outputStream.write(pending);
            if (!endsWithSseDelimiter(pending)) {
                writeMissingSseDelimiterSuffix(outputStream, pending);
            }
            outputStream.flush();
        }
    }

    private byte[] appendBytes(byte[] source, byte[] append, int appendLength) {
        byte[] merged = Arrays.copyOf(source, source.length + appendLength);
        System.arraycopy(append, 0, merged, source.length, appendLength);
        return merged;
    }

    private int findSseEventDelimiterEndIndex(byte[] data) {
        for (int i = 0; i < data.length - 1; i++) {
            if (data[i] == '\n' && data[i + 1] == '\n') {
                return i + 2;
            }
            if (i + 3 < data.length
                    && data[i] == '\r'
                    && data[i + 1] == '\n'
                    && data[i + 2] == '\r'
                    && data[i + 3] == '\n') {
                return i + 4;
            }
            if (data[i] == '\r' && data[i + 1] == '\r') {
                return i + 2;
            }
        }
        return -1;
    }

    private boolean endsWithSseDelimiter(byte[] data) {
        if (data.length >= 2 && data[data.length - 2] == '\n' && data[data.length - 1] == '\n') {
            return true;
        }
        if (data.length >= 4
                && data[data.length - 4] == '\r'
                && data[data.length - 3] == '\n'
                && data[data.length - 2] == '\r'
                && data[data.length - 1] == '\n') {
            return true;
        }
        return data.length >= 2 && data[data.length - 2] == '\r' && data[data.length - 1] == '\r';
    }

    private void writeMissingSseDelimiterSuffix(OutputStream outputStream, byte[] data) throws IOException {
        if (data.length >= 2 && data[data.length - 2] == '\r' && data[data.length - 1] == '\n') {
            outputStream.write('\r');
            outputStream.write('\n');
            return;
        }
        if (data.length >= 1 && data[data.length - 1] == '\n') {
            outputStream.write('\n');
            return;
        }
        if (data.length >= 1 && data[data.length - 1] == '\r') {
            outputStream.write('\r');
            return;
        }
        outputStream.write('\n');
        outputStream.write('\n');
    }
}
