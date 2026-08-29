package com.lumora.cloud.user.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.user.persistence.entity.UserSessionEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

public interface UserSessionMapper extends BaseMapper<UserSessionEntity> {

    @Select("SELECT * FROM user_session WHERE id = #{sessionId} LIMIT 1 FOR UPDATE")
    UserSessionEntity findByIdForUpdate(@Param("sessionId") String sessionId);

    @Update("""
            UPDATE user_session
            SET last_seen_at = #{lastSeenAt}, ip_address = #{ipAddress}, user_agent = #{userAgent}
            WHERE id = #{sessionId} AND status = 'ACTIVE'
            """)
    int touch(
            @Param("sessionId") String sessionId,
            @Param("lastSeenAt") Instant lastSeenAt,
            @Param("ipAddress") String ipAddress,
            @Param("userAgent") String userAgent
    );

    @Update("""
            UPDATE user_session
            SET status = 'REVOKED', revoked_at = #{revokedAt}
            WHERE id = #{sessionId} AND status = 'ACTIVE'
            """)
    int revoke(@Param("sessionId") String sessionId, @Param("revokedAt") Instant revokedAt);
}
