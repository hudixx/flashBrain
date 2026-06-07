package com.flashbrain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadResult {
    private String status;
    private Long snippetId;
    private String fileType;
    private String text;
    private String message;
}
