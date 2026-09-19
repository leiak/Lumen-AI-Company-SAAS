package com.lumen.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.inventory.entity.InvTransfer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InvTransferMapper extends BaseMapper<InvTransfer> {

    @Select("SELECT * FROM inv_transfer WHERE tenant_id = #{tenantId} "
        + "AND status = #{status} AND deleted = 0 ORDER BY id DESC")
    List<InvTransfer> findByStatus(@Param("tenantId") Long tenantId, @Param("status") String status);

    @Select("SELECT * FROM inv_transfer WHERE tenant_id = #{tenantId} "
        + "AND (from_warehouse_id = #{warehouseId} OR to_warehouse_id = #{warehouseId}) "
        + "AND deleted = 0 ORDER BY id DESC")
    List<InvTransfer> findByFromOrTo(@Param("tenantId") Long tenantId, @Param("warehouseId") Long warehouseId);
}