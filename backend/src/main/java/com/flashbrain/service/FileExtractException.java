package com.flashbrain.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class FileExtractException extends RuntimeException {
    public FileExtractException(String message) {
        super(message);
    }

    public FileExtractException(String message, Throwable cause) {
        super(message, cause);
    }
}
