package com.lumora.cloud.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("user_role")
public class UserRoleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long roleId;

    public UserRoleEntity() {
    }

    public static UserRoleEntity create(Long userId, Long roleId) {
        UserRoleEntity entity = new UserRoleEntity();
        entity.userId = userId;
        entity.roleId = roleId;
        return entity;
    }
}
