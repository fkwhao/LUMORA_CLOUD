package com.lumora.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.user.domain.entity.UserRoleEntity;
import com.lumora.cloud.user.domain.projection.UserRoleCodeView;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;
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

    @Select("""
            <script>
            SELECT ur.user_id AS user_id, r.code AS role_code
            FROM user_role ur
            JOIN role r ON r.id = ur.role_id
            WHERE ur.user_id IN
            <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                #{userId}
            </foreach>
            ORDER BY ur.user_id DESC, r.code
            </script>
            """)
    @Results({
            @Result(column = "user_id", property = "userId"),
            @Result(column = "role_code", property = "roleCode")
    })
    List<UserRoleCodeView> findRoleCodesByUserIds(@Param("userIds") List<Long> userIds);

    @Delete("DELETE FROM user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT COUNT(DISTINCT ua.id)
            FROM user_account ua
            JOIN user_role ur ON ur.user_id = ua.id
            JOIN role r ON r.id = ur.role_id
            WHERE r.code = #{roleCode} AND ua.status = 'ACTIVE' AND ua.deleted = 0
            """)
    long countActiveUsersByRole(@Param("roleCode") String roleCode);
}
