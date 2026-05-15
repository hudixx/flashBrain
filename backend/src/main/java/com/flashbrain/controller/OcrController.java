package com.flashbrain.controller;

import com.flashbrain.service.OcrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ocr")
@Slf4j
public class OcrController {

    @Autowired
    private OcrService ocrService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadAndRecognize(@RequestParam("file") MultipartFile file) {
        log.info("Received OCR upload request for: {}", file.getOriginalFilename());
        try {
            String text = ocrService.recognizeText(file);
            return ResponseEntity.ok(text);
        } catch (Exception e) {
            log.error("OCR upload failed", e);
            return ResponseEntity.internalServerError().body("OCR 识别失败: " + e.getMessage());
        }
    }
}
