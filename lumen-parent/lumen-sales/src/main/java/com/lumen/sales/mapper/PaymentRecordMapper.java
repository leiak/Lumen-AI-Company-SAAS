package com.lumen.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.sales.entity.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {

    @Select("SELECT * FROM sal_payment_record "
        + "WHERE receivable_id = #{receivableId} AND tenant_id = #{tenantId} AND deleted = 0 "
        + "ORDER BY paid_at ASC")
    List<PaymentRecord> findByReceivable(@Param("receivableId") Long receivableId,
                                         @Param("tenantId") Long tenantId);
}