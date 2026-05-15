package com.flashbrain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
@Slf4j
public class OcrService {

    @Autowired
    private RestTemplate ocrRestTemplate;

    @Value("${ocr.server.url}")
    private String ocrServerUrl;

    public String recognizeText(MultipartFile file) {
        log.info("Sending OCR request for file: {}", file.getOriginalFilename());

        // 构造 Multipart 请求
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = ocrRestTemplate.postForEntity(ocrServerUrl, requestEntity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (String) response.getBody().get("text");
            } else {
                throw new RuntimeException("OCR service returned error: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to call OCR service", e);
            throw new RuntimeException("OCR process failed: " + e.getMessage());
        }
    }
}
