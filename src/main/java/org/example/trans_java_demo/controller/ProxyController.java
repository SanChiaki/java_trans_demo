package org.example.trans_java_demo.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.trans_java_demo.service.ProxyService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/proxy")
public class ProxyController {

    private final ProxyService proxyService;
    private final ExecutorService executorService;

    public ProxyController(ProxyService proxyService) {
        this.proxyService = proxyService;
        this.executorService = Executors.newCachedThreadPool();
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
    public ResponseEntity<byte[]> proxyRequest(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return proxyService.forwardRequest(request, body);
    }

    @GetMapping(value = "/stream/**", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter proxyStreamRequest(HttpServletRequest request) {
        return handleStreamRequest(request, null);
    }

    @PostMapping(value = "/stream/**", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter proxyStreamPostRequest(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return handleStreamRequest(request, body);
    }

    private SseEmitter handleStreamRequest(HttpServletRequest request, byte[] body) {
        SseEmitter emitter = new SseEmitter(300000L); // 5 minutes timeout

        executorService.execute(() -> {
            try {
                ProxyService.StreamResponse streamResponse = proxyService.forwardStreamRequest(request, body);

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(streamResponse.getInputStream(), StandardCharsets.UTF_8))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isEmpty()) {
                            emitter.send(SseEmitter.event()
                                    .data(line)
                                    .build());
                        }
                    }
                    emitter.complete();
                }
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("Stream error: " + e.getMessage())
                            .build());
                } catch (Exception ignored) {
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}

