package com.lumen.procurement.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.procurement.entity.ProcOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ProcOrderMapper extends BaseMapper<ProcOrder> {

    /**
     * 按 supplier + status 查询。
     */
    @Select("SELECT * FROM proc_order WHERE tenant_id = #{tenantId} "
        + "AND supplier_id = #{supplierId} AND status = #{status} AND deleted = 0 ORDER BY id DESC")
    List<ProcOrder> findBySupplier(@Param("tenantId") Long tenantId,
                                   @Param("supplierId") Long supplierId,
                                   @Param("status") String status);

    /**
     * 按 status 查询 (dashboard 用)。
     */
    @Select("SELECT * FROM proc_order WHERE tenant_id = #{tenantId} "
        + "AND status = #{status} AND deleted = 0 ORDER BY id DESC")
    List<ProcOrder> findByStatus(@Param("tenantId") Long tenantId, @Param("status") String status);

    /**
     * 按 code 查唯一。
     */
    @Select("SELECT * FROM proc_order WHERE tenant_id = #{tenantId} AND code = #{code} AND deleted = 0 LIMIT 1")
    ProcOrder findByCode(@Param("tenantId") Long tenantId, @Param("code") String code);

    /**
     * 今日订单数 (dashboard)。
     */
    default long countToday(Long tenantId, LocalDate today) {
        return selectCount(new LambdaQueryWrapper<ProcOrder>()
            .eq(ProcOrder::getTenantId, tenantId)
            .eq(ProcOrder::getOrderDate, today)).longValue();
    }

    /**
     * 本周订单金额 (dashboard) — 简化用 order_date in [weekStart, today]。
     */
    default java.math.BigDecimal sumAmountBetween(Long tenantId, LocalDate from, LocalDate to) {
        List<ProcOrder> rows = selectList(new LambdaQueryWrapper<ProcOrder>()
            .eq(ProcOrder::getTenantId, tenantId)
            .between(ProcOrder::getOrderDate, from, to));
        java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
        for (ProcOrder o : rows) {
            if (o.getTotalAmount() != null) sum = sum.add(o.getTotalAmount());
        }
        return sum;
    }
}