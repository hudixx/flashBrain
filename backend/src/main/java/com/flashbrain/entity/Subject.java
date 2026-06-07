package com.flashbrain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("subject")
@Data
@NoArgsConstructor
public class Subject {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("parent_id")
    private Long parentId;

    @TableField("user_id")
    private Long userId;

    @TableField("icon")
    private String icon;

    @TableField("is_deleted")
    private Boolean isDeleted = false;

    @TableField(value = "deleted_at", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime deletedAt;

    @TableField(exist = false)
    private Integer deletedSnippetCount;

    @TableField(exist = false)
    private LocalDateTime recycleDeletedAt;
}
