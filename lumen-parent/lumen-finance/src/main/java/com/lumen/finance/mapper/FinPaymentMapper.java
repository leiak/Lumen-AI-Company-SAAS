package com.lumen.finance.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.finance.entity.FinPayment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FinPaymentMapper extends BaseMapper<FinPayment> {

    /**
     * 按 sourceType + sourceId 列出付款单。
     */
    @Select("SELECT * FROM fin_payment WHERE source_type = #{sourceType} AND source_id = #{sourceId} "
        + "AND deleted = 0 ORDER BY paid_at DESC, id DESC")
    List<FinPayment> findBySource(@Param("sourceType") String sourceType,
                                  @Param("sourceId") Long sourceId);

    /**
     * 列出某付款单号的所有付款（用于 VoucherNo 唯一性校验类比）。
     */
    default List<FinPayment> listByPaymentNo(String paymentNo) {
        return selectList(new LambdaQueryWrapper<FinPayment>()
            .eq(FinPayment::getPaymentNo, paymentNo)
            .eq(FinPayment::getDeleted, 0));
    }
}
