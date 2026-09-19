package com.lumen.procurement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.procurement.entity.ProcBidding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProcBiddingMapper extends BaseMapper<ProcBidding> {

    /**
     * 按 status 查询招投标。
     */
    @Select("SELECT * FROM proc_bidding WHERE tenant_id = #{tenantId} AND status = #{status} "
        + "AND deleted = 0 ORDER BY id DESC")
    List<ProcBidding> findByStatus(@Param("tenantId") Long tenantId, @Param("status") String status);

    /**
     * 按 code 查唯一。
     */
    @Select("SELECT * FROM proc_bidding WHERE tenant_id = #{tenantId} AND code = #{code} AND deleted = 0 LIMIT 1")
    ProcBidding findByCode(@Param("tenantId") Long tenantId, @Param("code") String code);
}