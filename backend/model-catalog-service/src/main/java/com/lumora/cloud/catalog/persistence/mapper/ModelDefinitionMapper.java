package com.lumora.cloud.catalog.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.catalog.persistence.entity.ModelDefinitionEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ModelDefinitionMapper extends BaseMapper<ModelDefinitionEntity> {

    @Select("SELECT * FROM model_definition WHERE code = #{code} LIMIT 1 FOR UPDATE")
    ModelDefinitionEntity findByCodeForUpdate(@Param("code") String code);

    @Select("SELECT * FROM model_definition WHERE id = #{id} LIMIT 1 FOR UPDATE")
    ModelDefinitionEntity findByIdForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE model_definition
            SET status = #{status}, revision = revision + 1
            WHERE id = #{id} AND revision = #{expectedRevision}
            """)
    int updateStatusOptimistic(
            @Param("id") Long id,
            @Param("expectedRevision") long expectedRevision,
            @Param("status") String status
    );

    @Update("UPDATE model_definition SET revision = revision + 1 WHERE id = #{id}")
    int touch(@Param("id") Long id);
}
