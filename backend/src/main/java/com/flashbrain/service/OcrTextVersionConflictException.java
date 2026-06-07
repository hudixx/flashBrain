package com.flashbrain.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class OcrTextVersionConflictException extends RuntimeException {
    public OcrTextVersionConflictException() {
        super("OCR 原文已被修改，请刷新后再上传文件");
    }
}
