package com.lumen.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.contract.entity.SignTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SignTaskMapper extends BaseMapper<SignTask> {

    @Select("SELECT * FROM ctr_sign_task WHERE contract_id = #{contractId} "
        + "AND tenant_id = #{tenantId} AND deleted = 0 ORDER BY id ASC")
    List<SignTask> findByContract(@Param("contractId") Long contractId,
                                   @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM ctr_sign_task WHERE signer_user_id = #{signerUserId} "
        + "AND status = #{status} AND tenant_id = #{tenantId} AND deleted = 0 "
        + "ORDER BY id DESC")
    List<SignTask> findBySigner(@Param("signerUserId") Long signerUserId,
                                 @Param("status") String status,
                                 @Param("tenantId") Long tenantId);
}
