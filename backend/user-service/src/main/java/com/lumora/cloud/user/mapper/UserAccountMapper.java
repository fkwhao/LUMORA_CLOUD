package com.lumora.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.user.domain.entity.UserAccountEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface UserAccountMapper extends BaseMapper<UserAccountEntity> {

    @Select("SELECT * FROM user_account WHERE id = #{userId} AND deleted = 0 LIMIT 1 FOR UPDATE")
    UserAccountEntity findByIdForUpdate(@Param("userId") Long userId);

    @Update("""
            UPDATE user_account
            SET status = #{status}, token_version = token_version + 1
            WHERE id = #{userId} AND deleted = 0
            """)
    int updateStatusAndTokenVersion(@Param("userId") Long userId, @Param("status") String status);

    @Update("""
            UPDATE user_account
            SET token_version = token_version + 1
            WHERE id = #{userId} AND deleted = 0
            """)
    int incrementTokenVersion(@Param("userId") Long userId);
}
