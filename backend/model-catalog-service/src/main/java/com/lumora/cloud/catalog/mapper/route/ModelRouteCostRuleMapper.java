package com.lumora.cloud.catalog.mapper.route;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumora.cloud.catalog.domain.entity.route.ModelRouteCostRuleEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ModelRouteCostRuleMapper extends BaseMapper<ModelRouteCostRuleEntity> {

    @Select("SELECT * FROM model_route_cost_pricing_rule WHERE route_id = #{routeId} ORDER BY rule_order")
    List<ModelRouteCostRuleEntity> findByRouteId(@Param("routeId") String routeId);

    @Delete("DELETE FROM model_route_cost_pricing_rule WHERE route_id = #{routeId}")
    int deleteByRouteId(@Param("routeId") String routeId);
}
