package com.lumen.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.contract.entity.Contract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ContractMapper extends BaseMapper<Contract> {

    @Select("SELECT * FROM ctr_contract WHERE status = #{status} "
        + "AND tenant_id = #{tenantId} AND deleted = 0 ORDER BY id DESC")
    List<Contract> findByStatus(@Param("status") String status,
                                 @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM ctr_contract WHERE type = #{type} AND status = #{status} "
        + "AND tenant_id = #{tenantId} AND deleted = 0 ORDER BY id DESC")
    List<Contract> findByTypeAndStatus(@Param("type") String type,
                                       @Param("status") String status,
                                       @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM ctr_contract WHERE drafter_id = #{drafterId} "
        + "AND tenant_id = #{tenantId} AND deleted = 0 ORDER BY id DESC")
    List<Contract> findByDrafter(@Param("drafterId") Long drafterId,
                                  @Param("tenantId") Long tenantId);
}
