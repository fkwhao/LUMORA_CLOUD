package com.lumora.cloud.user.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.user.persistence.entity.RefreshTokenEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

public interface RefreshTokenMapper extends BaseMapper<RefreshTokenEntity> {

    @Select("SELECT * FROM refresh_token WHERE token_hash = #{tokenHash} LIMIT 1 FOR UPDATE")
    RefreshTokenEntity findByHashForUpdate(@Param("tokenHash") String tokenHash);

    @Update("""
            UPDATE refresh_token
            SET status = 'USED', used_at = #{usedAt}
            WHERE id = #{tokenId} AND status = 'ACTIVE'
            """)
    int markUsed(@Param("tokenId") String tokenId, @Param("usedAt") Instant usedAt);

    @Update("""
            UPDATE refresh_token
            SET status = 'REVOKED', revoked_at = #{revokedAt}
            WHERE session_id = #{sessionId} AND status = 'ACTIVE'
            """)
    int revokeActiveBySessionId(@Param("sessionId") String sessionId, @Param("revokedAt") Instant revokedAt);
}
