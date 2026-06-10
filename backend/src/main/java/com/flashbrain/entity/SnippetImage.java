package com.flashbrain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("snippet_image")
@Data
@NoArgsConstructor
public class SnippetImage {
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("snippet_id")
    private String snippetId;

    @TableField("original_filename")
    private String originalFilename;

    @TableField("stored_filename")
    private String storedFilename;

    @TableField("url")
    private String url;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
