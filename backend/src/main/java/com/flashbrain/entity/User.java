package com.flashbrain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("app_user")
@Data
@NoArgsConstructor
public class User {
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("username")
    private String username;

    @TableField("email")
    private String email;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("display_name")
    private String displayName;

    @TableField("enabled")
    private Boolean enabled = true;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
