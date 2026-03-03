package org.example.trans_java_demo.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ProxyControllerMultipartTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testMultipartFormData() throws Exception {
        // 手动构造 multipart 请求体
        String boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW";
        String multipartBody = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"test.txt\"\r\n" +
                "Content-Type: text/plain\r\n" +
                "\r\n" +
                "Hello World\r\n" +
                "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"field\"\r\n" +
                "\r\n" +
                "field value\r\n" +
                "--" + boundary + "--\r\n";

        MvcResult result = mockMvc.perform(post("/proxy/test")
                        .contentType("multipart/form-data; boundary=" + boundary)
                        .content(multipartBody.getBytes(StandardCharsets.UTF_8))
                        .header("X-Target-URL", "https://httpbin.org/post"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = new String(result.getResponse().getContentAsByteArray());
        System.out.println("Response: " + responseBody);

        // httpbin.org 会返回接收到的数据，验证文件内容是否在响应中
        assertTrue(responseBody.contains("Hello World") || responseBody.contains("test.txt"),
                "Response should contain uploaded file data. Response: " + responseBody);
    }
}
