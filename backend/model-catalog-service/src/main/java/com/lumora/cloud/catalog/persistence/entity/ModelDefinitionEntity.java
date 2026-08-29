package com.lumora.cloud.catalog.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumora.cloud.catalog.domain.CatalogTypes.ModelStatus;

import java.time.Instant;

@TableName("model_definition")
public class ModelDefinitionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String status;
    private Long revision;
    private Instant createdAt;
    private Instant updatedAt;

    public ModelDefinitionEntity() {
    }

    public static ModelDefinitionEntity create(String code) {
        ModelDefinitionEntity entity = new ModelDefinitionEntity();
        entity.code = code;
        entity.status = ModelStatus.ACTIVE.name();
        entity.revision = 0L;
        return entity;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getStatus() { return status; }
    public Long getRevision() { return revision; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
