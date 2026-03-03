package org.example.trans_java_demo.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/test")
public class TestController {

    @PostMapping("/multipart")
    public ResponseEntity<Map<String, Object>> receiveMultipart(HttpServletRequest request) throws Exception {
        Map<String, Object> result = new HashMap<>();

        if (request instanceof MultipartHttpServletRequest multipartRequest) {
            // 获取所有文件
            Map<String, Object> files = new HashMap<>();
            for (Map.Entry<String, MultipartFile> entry : multipartRequest.getFileMap().entrySet()) {
                MultipartFile file = entry.getValue();
                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("originalFilename", file.getOriginalFilename());
                fileInfo.put("contentType", file.getContentType());
                fileInfo.put("size", file.getSize());
                fileInfo.put("content", new String(file.getBytes()));
                files.put(entry.getKey(), fileInfo);
            }
            result.put("files", files);

            // 获取所有表单字段
            Map<String, String> form = new HashMap<>();
            for (Map.Entry<String, String[]> entry : multipartRequest.getParameterMap().entrySet()) {
                form.put(entry.getKey(), entry.getValue()[0]);
            }
            result.put("form", form);
        } else {
            result.put("error", "Not a multipart request");
            result.put("requestClass", request.getClass().getName());
        }

        return ResponseEntity.ok(result);
    }
}
