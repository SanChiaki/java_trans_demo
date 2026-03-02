package org.example.trans_java_demo.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.trans_java_demo.service.ProxyService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

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
    public ResponseEntity<String> proxyRequest(
            HttpServletRequest request,
            @RequestBody(required = false) String body) {
        return proxyService.forwardRequest(request, body);
    }

    @GetMapping(value = "/stream/**", produces = "text/event-stream")
    public Flux<ServerSentEvent<String>> proxyStreamRequest(HttpServletRequest request) {
        return proxyService.forwardStreamRequest(request);
    }

    @PostMapping(value = "/stream/**", produces = "text/event-stream")
    public Flux<ServerSentEvent<String>> proxyStreamPostRequest(
            HttpServletRequest request,
            @RequestBody(required = false) String body) {
        return proxyService.forwardStreamRequest(request, body);
    }
}
