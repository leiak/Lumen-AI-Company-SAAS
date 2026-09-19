package com.lumen.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.inventory.entity.InvTransferItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InvTransferItemMapper extends BaseMapper<InvTransferItem> {

    @Select("SELECT * FROM inv_transfer_item WHERE tenant_id = #{tenantId} "
        + "AND transfer_id = #{transferId} AND deleted = 0 ORDER BY id")
    List<InvTransferItem> findByTransfer(@Param("tenantId") Long tenantId, @Param("transferId") Long transferId);
}