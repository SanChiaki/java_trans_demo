package org.example.trans_java_demo.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestSseController {

    @GetMapping(value = "/test/sse/raw", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<String> rawSse() {
        String body = "data: hello\n\n" +
                "event: update\n" +
                "data: world\n\n";

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-cache");
        headers.setContentType(MediaType.TEXT_EVENT_STREAM);

        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }

    @GetMapping(value = "/test/sse/raw/missing-tail-delimiter", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<String> rawSseMissingTailDelimiter() {
        String body = "data: hello\n\n" +
                "data: world\n";

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-cache");
        headers.setContentType(MediaType.TEXT_EVENT_STREAM);

        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }
}
