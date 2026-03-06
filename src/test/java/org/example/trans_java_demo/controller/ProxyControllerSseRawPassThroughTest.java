package org.example.trans_java_demo.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ProxyControllerSseRawPassThroughTest {

    @LocalServerPort
    private int port;

    @Test
    public void shouldPassThroughSseWithoutReEncoding() throws Exception {
        String expected = "data: hello\n\n" +
                "event: update\n" +
                "data: world\n\n";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/proxy/stream/raw"))
                .timeout(Duration.ofSeconds(15))
                .header("X-Target-URL", "http://localhost:" + port + "/test/sse/raw")
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "SSE proxy status should match upstream");
        assertEquals(expected, response.body(), "SSE body should be forwarded as-is");
        assertFalse(response.body().contains("data: data:"), "SSE data should not be wrapped again");
        assertTrue(
                response.headers().firstValue("content-type").orElse("").startsWith("text/event-stream"),
                "SSE Content-Type should be forwarded"
        );
    }

    @Test
    public void shouldAppendMissingTailDelimiterForSse() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/proxy/stream/raw"))
                .timeout(Duration.ofSeconds(15))
                .header("X-Target-URL", "http://localhost:" + port + "/test/sse/raw/missing-tail-delimiter")
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "SSE proxy status should match upstream");
        assertEquals(
                "data: hello\n\ndata: world\n\n",
                response.body(),
                "Proxy should ensure the final SSE event is terminated by a blank line"
        );
    }
}
