package com.flashbrain.controller;

import com.flashbrain.security.UserPrincipal;
import com.flashbrain.service.OcrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/api/ocr")
@Slf4j
public class OcrController {

    @Autowired
    private OcrService ocrService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadAndRecognize(@RequestParam("file") MultipartFile file,
                                                     @RequestParam("snippetId") Long snippetId,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Received OCR upload request for: {}, snippet: {}", file.getOriginalFilename(), snippetId);
        try {
            ocrService.recognizeTextAsync(file, snippetId, principal.getId());
            return ResponseEntity.ok("图片上传成功，OCR 正在后台识别");
        } catch (IOException e) {
            log.error("OCR upload failed", e);
            return ResponseEntity.internalServerError().body("OCR 任务提交失败: " + e.getMessage());
        }
    }
}
