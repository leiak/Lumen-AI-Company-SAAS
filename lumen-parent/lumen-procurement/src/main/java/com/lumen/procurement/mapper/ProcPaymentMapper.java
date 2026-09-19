package com.lumen.procurement.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.procurement.entity.ProcPayment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProcPaymentMapper extends BaseMapper<ProcPayment> {

    /**
     * 按 source_type + source_id 查询。
     */
    @Select("SELECT * FROM proc_payment WHERE tenant_id = #{tenantId} "
        + "AND source_type = #{sourceType} AND source_id = #{sourceId} AND deleted = 0 ORDER BY id DESC")
    List<ProcPayment> findBySource(@Param("tenantId") Long tenantId,
                                   @Param("sourceType") String sourceType,
                                   @Param("sourceId") Long sourceId);

    /**
     * 按 status 查询。
     */
    @Select("SELECT * FROM proc_payment WHERE tenant_id = #{tenantId} "
        + "AND status = #{status} AND deleted = 0 ORDER BY id DESC")
    List<ProcPayment> findByStatus(@Param("tenantId") Long tenantId, @Param("status") String status);

    /**
     * 按 payment_no 查唯一。
     */
    @Select("SELECT * FROM proc_payment WHERE tenant_id = #{tenantId} "
        + "AND payment_no = #{paymentNo} AND deleted = 0 LIMIT 1")
    ProcPayment findByPaymentNo(@Param("tenantId") Long tenantId, @Param("paymentNo") String paymentNo);

    /**
     * 未付款总额 (dashboard) — 状态非 paid/rejected。
     */
    default java.math.BigDecimal sumUnpaid(Long tenantId) {
        List<ProcPayment> rows = selectList(new LambdaQueryWrapper<ProcPayment>()
            .eq(ProcPayment::getTenantId, tenantId)
            .notIn(ProcPayment::getStatus, "paid", "rejected"));
        java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
        for (ProcPayment p : rows) {
            if (p.getAmount() != null) sum = sum.add(p.getAmount());
        }
        return sum;
    }
}