package com.flashbrain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilePreviewResult {
    private String fileId;
    private String snippetId;
    private String originalFilename;
    private String fileType;
    private String text;
    private String url;
}
