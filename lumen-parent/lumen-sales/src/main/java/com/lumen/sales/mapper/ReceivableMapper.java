package com.lumen.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.sales.entity.Receivable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ReceivableMapper extends BaseMapper<Receivable> {

    @Select("SELECT * FROM sal_receivable "
        + "WHERE customer_id = #{customerId} AND status = #{status} "
        + "AND tenant_id = #{tenantId} AND deleted = 0 ORDER BY id DESC")
    List<Receivable> findByCustomer(@Param("customerId") Long customerId,
                                    @Param("status") String status,
                                    @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM sal_receivable "
        + "WHERE status IN ('pending','partial') AND due_date < #{now} "
        + "AND tenant_id = #{tenantId} AND deleted = 0 ORDER BY due_date ASC")
    List<Receivable> findOverdue(@Param("now") LocalDate now,
                                 @Param("tenantId") Long tenantId);

    /** Total receivable amount for a customer. */
    @Select("SELECT COALESCE(SUM(amount),0) FROM sal_receivable "
        + "WHERE customer_id = #{customerId} AND tenant_id = #{tenantId} AND deleted = 0")
    BigDecimal sumAmountByCustomer(@Param("customerId") Long customerId,
                                   @Param("tenantId") Long tenantId);

    /** Total collected amount for a customer (across all receivables). */
    @Select("SELECT COALESCE(SUM(collected_amount),0) FROM sal_receivable "
        + "WHERE customer_id = #{customerId} AND tenant_id = #{tenantId} AND deleted = 0")
    BigDecimal sumCollectedByCustomer(@Param("customerId") Long customerId,
                                      @Param("tenantId") Long tenantId);
}