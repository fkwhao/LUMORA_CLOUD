package com.lumora.cloud.catalog.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.catalog.persistence.entity.ProviderEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProviderMapper extends BaseMapper<ProviderEntity> {

    @Select("SELECT * FROM model_provider WHERE code = #{code} LIMIT 1 FOR UPDATE")
    ProviderEntity findByCodeForUpdate(@Param("code") String code);

    @Select("SELECT * FROM model_provider WHERE id = #{id} LIMIT 1 FOR UPDATE")
    ProviderEntity findByIdForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE model_provider
            SET name = #{name}, protocol_type = #{protocolType}, base_url = #{baseUrl},
                status = #{status}, revision = revision + 1
            WHERE id = #{id} AND revision = #{expectedRevision}
            """)
    int updateOptimistic(
            @Param("id") Long id,
            @Param("expectedRevision") long expectedRevision,
            @Param("name") String name,
            @Param("protocolType") String protocolType,
            @Param("baseUrl") String baseUrl,
            @Param("status") String status
    );

    @Update("""
            UPDATE model_provider
            SET credential_reference = #{credentialReference}, revision = revision + 1
            WHERE id = #{id} AND revision = #{expectedRevision}
            """)
    int rotateCredentialReference(
            @Param("id") Long id,
            @Param("expectedRevision") long expectedRevision,
            @Param("credentialReference") String credentialReference
    );
}
