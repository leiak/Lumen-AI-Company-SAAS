package com.lumen.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.contract.entity.ChangeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChangeLogMapper extends BaseMapper<ChangeLog> {

    @Select("SELECT * FROM ctr_change_log WHERE contract_id = #{contractId} "
        + "AND tenant_id = #{tenantId} AND deleted = 0 ORDER BY operated_at DESC")
    List<ChangeLog> findByContract(@Param("contractId") Long contractId,
                                    @Param("tenantId") Long tenantId);
}
