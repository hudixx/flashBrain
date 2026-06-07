package com.flashbrain.service;

import com.flashbrain.dto.UploadResult;
import com.flashbrain.entity.Snippet;
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

    @Autowired
    private FileTextExtractor fileTextExtractor;

    @Value("${ocr.server.url}")
    private String ocrServerUrl;

    public UploadResult uploadAndExtract(MultipartFile file, Long snippetId, Long userId, Long expectedOcrTextVersion) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new FileExtractException("上传文件不能为空");
        }

        byte[] fileBytes = file.getBytes();
        String filename = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        FileKind kind = fileTextExtractor.detectKind(filename, file.getContentType(), fileBytes);
        if (kind == FileKind.UNKNOWN) {
            throw new FileExtractException("不支持的文件类型，请上传图片、txt、docx、doc、pdf 或 ofd 文件");
        }

        if (kind == FileKind.IMAGE) {
            return recognizeTextAsync(file, snippetId, userId);
        }

        snippetImageService.saveUploadedFile(snippetId, userId, file);
        String text = fileTextExtractor.extractText(kind, fileBytes, filename, this::recognizeText);
        snippetService.replaceOcrIfVersionMatches(snippetId, userId, text, expectedOcrTextVersion);
        return new UploadResult(
                ExtractStatus.TEXT_EXTRACTED.name(),
                snippetId,
                kind.name(),
                text,
                "文件内容已读取到 OCR 原文区"
        );
    }

    public UploadResult recognizeTextAsync(MultipartFile file, Long snippetId, Long userId) throws IOException {
        Snippet snippet = snippetService.getSnippet(snippetId, userId);
        Long expectedVersion = snippet.getOcrTextVersion();
        snippetImageService.saveImage(snippetId, userId, file);

        byte[] fileBytes = file.getBytes();
        String filename = file.getOriginalFilename();

        CompletableFuture.runAsync(() -> {
            try {
                String text = recognizeText(fileBytes, filename);
                boolean updated = snippetService.replaceOcrIfVersionStillMatches(snippetId, userId, text, expectedVersion);
                if (updated) {
                    log.info("OCR result saved for snippet: {}", snippetId);
                } else {
                    log.info("Skip OCR result for snippet: {} because OCR text was modified", snippetId);
                }
            } catch (Exception e) {
                log.error("Async OCR failed for snippet: {}", snippetId, e);
            }
        });

        return new UploadResult(
                ExtractStatus.OCR_PROCESSING.name(),
                snippetId,
                FileKind.IMAGE.name(),
                null,
                "图片上传成功，OCR 正在后台识别"
        );
    }

    public String recognizeText(MultipartFile file) {
        try {
            return recognizeText(file.getBytes(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read upload file: " + e.getMessage());
        }
    }

    public String recognizeText(byte[] fileBytes, String filename) {
        log.info("Sending OCR request for file: {}", filename);

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
                Object text = response.getBody().get("text");
                return text == null ? "" : text.toString();
            } else {
                throw new RuntimeException("OCR service returned error: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to call OCR service", e);
            throw new RuntimeException("OCR process failed: " + e.getMessage());
        }
    }
}
