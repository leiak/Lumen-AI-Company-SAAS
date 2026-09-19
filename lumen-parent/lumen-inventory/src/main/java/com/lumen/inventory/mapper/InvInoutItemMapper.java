package com.lumen.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.inventory.entity.InvInoutItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InvInoutItemMapper extends BaseMapper<InvInoutItem> {

    @Select("SELECT * FROM inv_inout_item WHERE tenant_id = #{tenantId} "
        + "AND inout_id = #{inoutId} AND deleted = 0 ORDER BY id")
    List<InvInoutItem> findByInout(@Param("tenantId") Long tenantId, @Param("inoutId") Long inoutId);
}