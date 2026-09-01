package com.lumora.cloud.user.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("role")
public class RoleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;

    public RoleEntity() {
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
}
