package org.example.trans_java_demo.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Enumeration;

@Service
public class ProxyService {

    private final WebClient webClient;

    public ProxyService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(16 * 1024 * 1024)) // 16MB buffer
                .build();
    }

    public ResponseEntity<String> forwardRequest(HttpServletRequest request, String body) {
        String targetUrl = getTargetUrl(request);
        HttpHeaders headers = extractHeaders(request);
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        try {
            WebClient.RequestBodySpec requestSpec = webClient
                    .method(method)
                    .uri(targetUrl)
                    .headers(h -> h.addAll(headers));

            if (body != null && !body.isEmpty()) {
                requestSpec.bodyValue(body);
            }

            ResponseEntity<byte[]> response = requestSpec
                    .retrieve()
                    .toEntity(byte[].class)
                    .block();

            if (response == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body("No response from target server");
            }

            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.putAll(response.getHeaders());
            responseHeaders.remove(HttpHeaders.TRANSFER_ENCODING);
            responseHeaders.remove(HttpHeaders.CONNECTION);

            byte[] responseBody = response.getBody();
            String responseString = responseBody != null ? new String(responseBody) : "";

            return ResponseEntity
                    .status(response.getStatusCode())
                    .headers(responseHeaders)
                    .body(responseString);

        } catch (WebClientResponseException e) {
            HttpHeaders errorHeaders = new HttpHeaders();
            errorHeaders.putAll(e.getHeaders());
            errorHeaders.remove(HttpHeaders.TRANSFER_ENCODING);
            errorHeaders.remove(HttpHeaders.CONNECTION);

            return ResponseEntity.status(e.getStatusCode())
                    .headers(errorHeaders)
                    .body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Proxy error: " + e.getMessage());
        }
    }

    public Flux<ServerSentEvent<String>> forwardStreamRequest(HttpServletRequest request) {
        return forwardStreamRequest(request, null);
    }

    public Flux<ServerSentEvent<String>> forwardStreamRequest(HttpServletRequest request, String body) {
        String targetUrl = getTargetUrl(request);
        HttpHeaders headers = extractHeaders(request);

        WebClient.RequestBodySpec requestSpec = webClient
                .method(HttpMethod.valueOf(request.getMethod()))
                .uri(targetUrl)
                .headers(h -> h.addAll(headers));

        if (body != null && !body.isEmpty()) {
            requestSpec.bodyValue(body);
        }

        return requestSpec
                .retrieve()
                .bodyToFlux(String.class)
                .map(data -> ServerSentEvent.<String>builder()
                        .data(data)
                        .build())
                .onErrorResume(e -> Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("error")
                                .data("Stream error: " + e.getMessage())
                                .build()
                ));
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

    private HttpHeaders extractHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();

            if (shouldForwardHeader(headerName)) {
                headers.put(headerName, Collections.list(request.getHeaders(headerName)));
            }
        }

        return headers;
    }

    private boolean shouldForwardHeader(String headerName) {
        String lowerName = headerName.toLowerCase();
        return !lowerName.equals("host")
                && !lowerName.equals("x-target-url")
                && !lowerName.startsWith("x-forwarded");
    }
}
