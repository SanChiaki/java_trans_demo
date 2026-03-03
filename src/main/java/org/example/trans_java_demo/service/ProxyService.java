package org.example.trans_java_demo.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProxyService {

    private final HttpClient httpClient;

    public ProxyService() {
        try {
            // 创建信任所有证书的TrustManager
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
            };

            // 创建SSLContext并初始化
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // 构建HttpClient，关闭SSL校验
            this.httpClient = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize HttpClient with SSL bypass", e);
        }
    }

    public ResponseEntity<byte[]> forwardRequest(HttpServletRequest request, byte[] body) {
        return forwardRequest(request, body, null);
    }

    public ResponseEntity<byte[]> forwardRequest(HttpServletRequest request, byte[] body, String overrideContentType) {
        try {
            String targetUrl = getTargetUrl(request);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(60));

            addHeaders(requestBuilder, request, overrideContentType);

            String method = request.getMethod().toUpperCase();
            byte[] requestBody = body != null ? body : new byte[0];
            switch (method) {
                case "GET":
                    requestBuilder.GET();
                    break;
                case "POST":
                    requestBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(requestBody));
                    break;
                case "PUT":
                    requestBuilder.PUT(HttpRequest.BodyPublishers.ofByteArray(requestBody));
                    break;
                case "DELETE":
                    if (requestBody.length > 0) {
                        requestBuilder.method("DELETE", HttpRequest.BodyPublishers.ofByteArray(requestBody));
                    } else {
                        requestBuilder.DELETE();
                    }
                    break;
                case "PATCH":
                    requestBuilder.method("PATCH", HttpRequest.BodyPublishers.ofByteArray(requestBody));
                    break;
                case "HEAD":
                    requestBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody());
                    break;
                case "OPTIONS":
                    requestBuilder.method("OPTIONS", HttpRequest.BodyPublishers.noBody());
                    break;
                default:
                    requestBuilder.method(method, HttpRequest.BodyPublishers.ofByteArray(requestBody));
            }

            HttpResponse<byte[]> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );

            HttpHeaders responseHeaders = new HttpHeaders();
            response.headers().map().forEach((key, values) -> {
                if (shouldForwardResponseHeader(key)) {
                    responseHeaders.put(key, values);
                }
            });

            return ResponseEntity
                    .status(response.statusCode())
                    .headers(responseHeaders)
                    .body(response.body());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(("Proxy error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    public StreamResponse forwardStreamRequest(HttpServletRequest request, byte[] body) {
        try {
            String targetUrl = getTargetUrl(request);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofMinutes(5));

            addHeaders(requestBuilder, request);

            String method = request.getMethod().toUpperCase();
            byte[] requestBody = body != null ? body : new byte[0];
            if ("POST".equals(method) || "PUT".equals(method)) {
                requestBuilder.method(method, HttpRequest.BodyPublishers.ofByteArray(requestBody));
            } else {
                requestBuilder.GET();
            }

            HttpResponse<java.io.InputStream> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            return new StreamResponse(response.body(), response.statusCode());

        } catch (Exception e) {
            throw new RuntimeException("Stream proxy error: " + e.getMessage(), e);
        }
    }

    private void addHeaders(HttpRequest.Builder requestBuilder, HttpServletRequest request, String overrideContentType) {
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (shouldForwardHeader(headerName)) {
                // 如果是 Content-Type 且有覆盖值，则使用覆盖值
                if ("content-type".equalsIgnoreCase(headerName) && overrideContentType != null) {
                    continue; // 跳过原始的，后面单独添加
                }
                Enumeration<String> headerValues = request.getHeaders(headerName);
                while (headerValues.hasMoreElements()) {
                    requestBuilder.header(headerName, headerValues.nextElement());
                }
            }
        }
        // 添加覆盖的 Content-Type
        if (overrideContentType != null) {
            requestBuilder.header("Content-Type", overrideContentType);
        }
    }

    private void addHeaders(HttpRequest.Builder requestBuilder, HttpServletRequest request) {
        addHeaders(requestBuilder, request, null);
    }

    private String getTargetUrl(HttpServletRequest request) {
        String targetUrl = request.getHeader("X-Target-URL");

        if (targetUrl == null || targetUrl.isEmpty()) {
            targetUrl = request.getParameter("targetUrl");
        }

        if (targetUrl == null || targetUrl.isEmpty()) {
            throw new IllegalArgumentException("Target URL not specified. Use X-Target-URL header or targetUrl parameter.");
        }

        return targetUrl;
    }

    private boolean shouldForwardHeader(String headerName) {
        String lowerName = headerName.toLowerCase();
        return !lowerName.equals("host")
                && !lowerName.equals("cookie")
                && !lowerName.equals("connection")
                && !lowerName.equals("content-length")
                && !lowerName.equals("expect")
                && !lowerName.equals("upgrade")
                && !lowerName.equals("x-target-url")
                && !lowerName.startsWith("x-forwarded");
    }

    private boolean shouldForwardResponseHeader(String headerName) {
        String lowerName = headerName.toLowerCase();
        return !lowerName.equals("transfer-encoding")
                && !lowerName.equals("connection")
                && !lowerName.startsWith("access-control-");
    }

    public static class StreamResponse {
        private final java.io.InputStream inputStream;
        private final int statusCode;

        public StreamResponse(java.io.InputStream inputStream, int statusCode) {
            this.inputStream = inputStream;
            this.statusCode = statusCode;
        }

        public java.io.InputStream getInputStream() {
            return inputStream;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}

