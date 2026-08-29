package com.lumora.cloud.user.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.user.persistence.entity.UserRoleEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Set;

public interface UserRoleMapper extends BaseMapper<UserRoleEntity> {

    @Select("""
            SELECT r.code
            FROM user_role ur
            JOIN role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
            ORDER BY r.code
            """)
    Set<String> findRoleCodesByUserId(@Param("userId") Long userId);
}
