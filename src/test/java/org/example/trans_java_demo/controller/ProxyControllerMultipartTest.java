package org.example.trans_java_demo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class ProxyControllerMultipartTest {

    @Autowired
    private MockMvc mockMvc;

    @LocalServerPort
    private int port;

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

        // 通过 proxy 转发到本地的 /test/multipart 接口
        String targetUrl = "http://localhost:" + port + "/test/multipart";

        MvcResult result = mockMvc.perform(multipart("/proxy/test")
                        .file(file)
                        .file(field)
                        .header("X-Target-URL", targetUrl))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = new String(result.getResponse().getContentAsByteArray());
        System.out.println("Response: " + responseBody);

        // 解析返回的 JSON
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        // 验证文件信息
        assertTrue(response.containsKey("files"), "Response should contain 'files' key");
        Map<String, Object> files = (Map<String, Object>) response.get("files");
        assertTrue(files.containsKey("file"), "Files should contain 'file' key");

        Map<String, Object> fileInfo = (Map<String, Object>) files.get("file");
        assertEquals("test.txt", fileInfo.get("originalFilename"), "File name should match");
        assertEquals("Hello World", fileInfo.get("content"), "File content should match");

        // 验证表单字段
        assertTrue(response.containsKey("form"), "Response should contain 'form' key");
        Map<String, String> form = (Map<String, String>) response.get("form");
        assertTrue(form.containsKey("field"), "Form should contain 'field' key");
        assertEquals("field value", form.get("field"), "Field value should match");

        System.out.println("✓ Multipart proxy test passed!");
    }
}
