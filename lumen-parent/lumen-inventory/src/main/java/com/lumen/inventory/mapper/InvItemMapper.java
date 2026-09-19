package com.lumen.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.inventory.entity.InvItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InvItemMapper extends BaseMapper<InvItem> {

    @Select("SELECT * FROM inv_item WHERE tenant_id = #{tenantId} AND code = #{code} AND deleted = 0 LIMIT 1")
    InvItem findByCode(@Param("tenantId") Long tenantId, @Param("code") String code);

    @Select("SELECT * FROM inv_item WHERE tenant_id = #{tenantId} AND barcode = #{barcode} AND deleted = 0 LIMIT 1")
    InvItem findByBarcode(@Param("tenantId") Long tenantId, @Param("barcode") String barcode);

    @Select("SELECT * FROM inv_item WHERE tenant_id = #{tenantId} AND category = #{category} AND deleted = 0 ORDER BY id")
    List<InvItem> findByCategory(@Param("tenantId") Long tenantId, @Param("category") String category);
}