package com.lumora.cloud.user.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.user.persistence.entity.RoleEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface RoleMapper extends BaseMapper<RoleEntity> {

    @Select("SELECT id, code, name FROM role WHERE code = #{code} LIMIT 1")
    RoleEntity findByCode(@Param("code") String code);

    @Select("SELECT id, code, name FROM role WHERE code = #{code} LIMIT 1 FOR UPDATE")
    RoleEntity findByCodeForUpdate(@Param("code") String code);
}
