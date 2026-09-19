package com.lumen.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.contract.entity.Fulfillment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FulfillmentMapper extends BaseMapper<Fulfillment> {

    @Select("SELECT * FROM ctr_fulfillment WHERE contract_id = #{contractId} "
        + "AND tenant_id = #{tenantId} AND deleted = 0 ORDER BY planned_date ASC")
    List<Fulfillment> findByContract(@Param("contractId") Long contractId,
                                      @Param("tenantId") Long tenantId);
}
