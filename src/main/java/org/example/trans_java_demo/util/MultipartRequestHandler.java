package org.example.trans_java_demo.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

/**
 * Multipart 请求处理工具类
 * 用于从已解析的 MultipartHttpServletRequest 中重建原始 multipart body
 */
public class MultipartRequestHandler {

    /**
     * Multipart body 结果对象
     */
    public static class MultipartBody {
        private final byte[] body;
        private final String contentType;

        public MultipartBody(byte[] body, String contentType) {
            this.body = body;
            this.contentType = contentType;
        }

        public byte[] getBody() {
            return body;
        }

        public String getContentType() {
            return contentType;
        }
    }

    /**
     * 判断是否是 multipart 请求
     */
    public static boolean isMultipartRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }

    /**
     * 从 MultipartHttpServletRequest 重建 multipart body 并返回新的 Content-Type
     */
    public static MultipartBody rebuildMultipartBodyWithBoundary(HttpServletRequest request) throws Exception {
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();

        byte[] body;
        if (!(request instanceof MultipartHttpServletRequest)) {
            body = rebuildFromServletParts(request, boundary);
        } else {
            body = rebuildFromBody(request, boundary);
        }

        String contentType = "multipart/form-data; boundary=" + boundary;
        return new MultipartBody(body, contentType);
    }

    /**
     * 从 MultipartHttpServletRequest 重建原始 multipart body
     */
    public static byte[] rebuildMultipartBody(HttpServletRequest request) throws Exception {
        return rebuildMultipartBodyWithBoundary(request).getBody();
    }

    /**
     * 从 MultipartHttpServletRequest 重建 body
     */
    private static byte[] rebuildFromBody(HttpServletRequest request, String boundary) throws Exception {
        MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // 处理所有文件
        Map<String, MultipartFile> fileMap = multipartRequest.getFileMap();
        for (Map.Entry<String, MultipartFile> entry : fileMap.entrySet()) {
            MultipartFile file = entry.getValue();
            writePart(outputStream, boundary, entry.getKey(),
                     file.getOriginalFilename(), file.getContentType(), file.getBytes());
        }

        // 处理所有普通字段
        Map<String, String[]> parameterMap = multipartRequest.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String name = entry.getKey();
            for (String value : entry.getValue()) {
                writeFormField(outputStream, boundary, name, value);
            }
        }

        // 写入结束边界
        outputStream.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        return outputStream.toByteArray();
    }

    /**
     * 使用 Servlet 3.0 Part API 重建 multipart body
     */
    private static byte[] rebuildFromServletParts(HttpServletRequest request, String boundary) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Collection<Part> parts = request.getParts();

        for (Part part : parts) {
            String fileName = part.getSubmittedFileName();
            byte[] content = part.getInputStream().readAllBytes();

            if (fileName != null && !fileName.isEmpty()) {
                // 文件字段
                writePart(outputStream, boundary, part.getName(),
                         fileName, part.getContentType(), content);
            } else {
                // 普通字段
                String value = new String(content, StandardCharsets.UTF_8);
                writeFormField(outputStream, boundary, part.getName(), value);
            }
        }

        // 写入结束边界
        outputStream.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        return outputStream.toByteArray();
    }

    /**
     * 写入文件字段
     */
    private static void writePart(ByteArrayOutputStream outputStream, String boundary,
                                   String name, String fileName, String contentType, byte[] content)
            throws IOException {
        outputStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.write(("Content-Disposition: form-data; name=\"" + name + "\"").getBytes(StandardCharsets.UTF_8));

        if (fileName != null && !fileName.isEmpty()) {
            outputStream.write(("; filename=\"" + fileName + "\"").getBytes(StandardCharsets.UTF_8));
        }

        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));

        if (contentType != null && !contentType.isEmpty()) {
            outputStream.write(("Content-Type: " + contentType + "\r\n").getBytes(StandardCharsets.UTF_8));
        }

        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
        outputStream.write(content);
        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 写入普通表单字段
     */
    private static void writeFormField(ByteArrayOutputStream outputStream, String boundary,
                                        String name, String value) throws IOException {
        outputStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
        outputStream.write(value.getBytes(StandardCharsets.UTF_8));
        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 从 Content-Type 中提取 boundary
     */
    private static String extractBoundary(String contentType) {
        if (contentType == null) {
            return null;
        }

        String[] parts = contentType.split(";");
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("boundary=")) {
                String boundary = part.substring("boundary=".length()).trim();
                // 移除可能的引号
                if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
                    boundary = boundary.substring(1, boundary.length() - 1);
                }
                return boundary;
            }
        }

        return null;
    }
}
