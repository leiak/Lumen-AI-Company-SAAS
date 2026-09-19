package com.lumen.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.contract.entity.PaymentPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PaymentPlanMapper extends BaseMapper<PaymentPlan> {

    /**
     * Find overdue payment plans — planned_date < now AND status != 'completed'.
     * Status filter is intentionally string-based to keep the SQL portable.
     */
    @Select("SELECT * FROM ctr_payment_plan WHERE planned_date < #{now} "
        + "AND status IN ('pending','partial') AND tenant_id = #{tenantId} "
        + "AND deleted = 0 ORDER BY planned_date ASC")
    List<PaymentPlan> findOverdue(@Param("now") LocalDate now,
                                   @Param("tenantId") Long tenantId);
}
