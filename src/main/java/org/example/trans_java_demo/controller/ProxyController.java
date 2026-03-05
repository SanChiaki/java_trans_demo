package org.example.trans_java_demo.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.trans_java_demo.service.ProxyService;
import org.example.trans_java_demo.util.MultipartRequestHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

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

            StreamingResponseBody responseBody = outputStream -> {
                try (var inputStream = streamResponse.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        outputStream.flush();
                    }
                }
            };

            HttpHeaders headers = new HttpHeaders();
            headers.putAll(streamResponse.getHeaders());

            return ResponseEntity
                    .status(streamResponse.getStatusCode())
                    .headers(headers)
                    .body(responseBody);
        } catch (Exception e) {
            byte[] errorBody = ("Proxy stream error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(outputStream -> outputStream.write(errorBody));
        }
    }
}
