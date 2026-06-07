package com.flashbrain.controller;

import com.flashbrain.dto.UploadResult;
import com.flashbrain.security.UserPrincipal;
import com.flashbrain.service.FileExtractException;
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
    public ResponseEntity<UploadResult> uploadAndRecognize(@RequestParam("file") MultipartFile file,
                                                           @RequestParam("snippetId") Long snippetId,
                                                           @RequestParam(value = "ocrTextVersion", required = false) Long ocrTextVersion,
                                                           @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Received file upload request for: {}, snippet: {}", file.getOriginalFilename(), snippetId);
        try {
            return ResponseEntity.ok(ocrService.uploadAndExtract(file, snippetId, principal.getId(), ocrTextVersion));
        } catch (IOException e) {
            log.error("File upload failed", e);
            throw new FileExtractException("文件上传失败: " + e.getMessage(), e);
        }
    }
}
