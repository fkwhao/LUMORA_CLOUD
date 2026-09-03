package com.lumora.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.user.domain.entity.UserSessionEntity;
import com.lumora.cloud.user.domain.projection.ActiveSessionCountView;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

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

    @Select("""
            SELECT * FROM user_session
            WHERE user_id = #{userId}
            ORDER BY created_at DESC
            LIMIT 100
            """)
    List<UserSessionEntity> findRecentByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT * FROM user_session
            WHERE user_id = #{userId} AND status = 'ACTIVE'
            FOR UPDATE
            """)
    List<UserSessionEntity> findActiveByUserIdForUpdate(@Param("userId") Long userId);

    @Select("""
            <script>
            SELECT user_id, COUNT(*) AS active_sessions
            FROM user_session
            WHERE status = 'ACTIVE'
              AND expires_at &gt; #{now}
              AND user_id IN
            <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                #{userId}
            </foreach>
            GROUP BY user_id
            </script>
            """)
    @Results({
            @Result(column = "user_id", property = "userId"),
            @Result(column = "active_sessions", property = "activeSessions")
    })
    List<ActiveSessionCountView> countActiveByUserIds(
            @Param("userIds") List<Long> userIds,
            @Param("now") Instant now
    );

    @Update("""
            UPDATE user_session
            SET status = 'REVOKED', revoked_at = #{revokedAt}
            WHERE user_id = #{userId} AND status = 'ACTIVE'
            """)
    int revokeActiveByUserId(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);
}
