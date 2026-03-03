package org.example.trans_java_demo.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ProxyControllerMultipartTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testMultipartFormData() throws Exception {
        // 使用 MockMultipartFile 创建文件
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "Hello World".getBytes()
        );

        MockMultipartFile field = new MockMultipartFile(
                "field",
                "",
                "text/plain",
                "field value".getBytes()
        );

        MvcResult result = mockMvc.perform(multipart("/proxy/test")
                        .file(file)
                        .file(field)
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
