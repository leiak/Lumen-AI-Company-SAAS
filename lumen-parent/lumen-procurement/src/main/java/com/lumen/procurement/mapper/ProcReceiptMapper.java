package com.lumen.procurement.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.procurement.entity.ProcReceipt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProcReceiptMapper extends BaseMapper<ProcReceipt> {

    /**
     * 按 order_id 查询所有收货单。
     */
    @Select("SELECT * FROM proc_receipt WHERE tenant_id = #{tenantId} "
        + "AND order_id = #{orderId} AND deleted = 0 ORDER BY id DESC")
    List<ProcReceipt> findByOrder(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);

    /**
     * 按 code 查唯一。
     */
    @Select("SELECT * FROM proc_receipt WHERE tenant_id = #{tenantId} AND code = #{code} AND deleted = 0 LIMIT 1")
    ProcReceipt findByCode(@Param("tenantId") Long tenantId, @Param("code") String code);

    /**
     * 待处理收货单 (dashboard)。status=pending。
     */
    default List<ProcReceipt> findPending(Long tenantId) {
        return selectList(new LambdaQueryWrapper<ProcReceipt>()
            .eq(ProcReceipt::getTenantId, tenantId)
            .eq(ProcReceipt::getStatus, "pending")
            .orderByDesc(ProcReceipt::getId));
    }
}