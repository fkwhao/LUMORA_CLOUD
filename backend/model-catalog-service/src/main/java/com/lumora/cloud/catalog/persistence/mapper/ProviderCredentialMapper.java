package com.lumora.cloud.catalog.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.catalog.persistence.entity.ProviderCredentialEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProviderCredentialMapper extends BaseMapper<ProviderCredentialEntity> {

    @Select("SELECT * FROM model_provider_credential WHERE provider_id = #{providerId} LIMIT 1 FOR UPDATE")
    ProviderCredentialEntity findByProviderIdForUpdate(@Param("providerId") Long providerId);

    @Select("SELECT * FROM model_provider_credential WHERE credential_reference = #{reference} LIMIT 1")
    ProviderCredentialEntity findByReference(@Param("reference") String reference);

    @Update("""
            UPDATE model_provider_credential
            SET credential_reference = #{reference}, encrypted_secret = #{encryptedSecret},
                encryption_nonce = #{nonce}, encryption_key_version = #{keyVersion},
                secret_fingerprint = #{fingerprint}, secret_hint = #{hint},
                revision = revision + 1, rotated_at = CURRENT_TIMESTAMP(6)
            WHERE id = #{id} AND revision = #{expectedRevision} AND status = 'ACTIVE'
            """)
    int rotate(
            @Param("id") String id,
            @Param("expectedRevision") long expectedRevision,
            @Param("reference") String reference,
            @Param("encryptedSecret") byte[] encryptedSecret,
            @Param("nonce") byte[] nonce,
            @Param("keyVersion") int keyVersion,
            @Param("fingerprint") String fingerprint,
            @Param("hint") String hint
    );
}
