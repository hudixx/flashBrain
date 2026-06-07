package com.flashbrain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("snippet")
@Data
@NoArgsConstructor
public class Snippet {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("subject_id")
    private Long subjectId;

    @TableField("user_id")
    private Long userId;

    @TableField(value = "title", updateStrategy = FieldStrategy.ALWAYS)
    private String title;

    @TableField("image_path")
    private String imagePath;

    @TableField(value = "ocr_text", updateStrategy = FieldStrategy.ALWAYS)
    private String ocrText;

    @TableField("ocr_text_version")
    private Long ocrTextVersion = 0L;

    @TableField(value = "note_content", updateStrategy = FieldStrategy.ALWAYS)
    private String noteContent;

    @TableField("sort_order")
    private Double sortOrder;

    @TableField("is_pinned")
    private Boolean isPinned = false;

    @TableField("is_mastered")
    private Boolean isMastered = false;
}
