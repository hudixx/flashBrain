package com.flashbrain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class OcrService {

    @Autowired
    private RestTemplate ocrRestTemplate;

    @Autowired
    private SnippetService snippetService;

    @Autowired
    private SnippetImageService snippetImageService;

    @Value("${ocr.server.url}")
    private String ocrServerUrl;

    public void recognizeTextAsync(MultipartFile file, Long snippetId, Long userId) throws IOException {
        snippetImageService.saveImage(snippetId, userId, file);

        byte[] fileBytes = file.getBytes();
        String filename = file.getOriginalFilename();

        CompletableFuture.runAsync(() -> {
            try {
                String text = recognizeText(fileBytes, filename);
                snippetService.updateOcr(snippetId, userId, text);
                log.info("OCR result saved for snippet: {}", snippetId);
            } catch (Exception e) {
                log.error("Async OCR failed for snippet: {}", snippetId, e);
            }
        });
    }

    public String recognizeText(MultipartFile file) {
        try {
            return recognizeText(file.getBytes(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read upload file: " + e.getMessage());
        }
    }

    private String recognizeText(byte[] fileBytes, String filename) {
        log.info("Sending OCR request for file: {}", filename);

        // 构造 Multipart 请求
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);

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
