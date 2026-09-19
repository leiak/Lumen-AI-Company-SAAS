package com.lumen.procurement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.procurement.entity.ProcBiddingParticipant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProcBiddingParticipantMapper extends BaseMapper<ProcBiddingParticipant> {

    /**
     * 按 bidding 查询参与方。
     */
    @Select("SELECT * FROM proc_bidding_participant WHERE tenant_id = #{tenantId} "
        + "AND bidding_id = #{biddingId} AND deleted = 0 ORDER BY id ASC")
    List<ProcBiddingParticipant> findByBidding(@Param("tenantId") Long tenantId,
                                                @Param("biddingId") Long biddingId);
}