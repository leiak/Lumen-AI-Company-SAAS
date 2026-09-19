package com.lumen.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.finance.entity.FinVoucherEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 凭证明细 mapper。
 */
@Mapper
public interface FinVoucherEntryMapper extends BaseMapper<FinVoucherEntry> {

    /**
     * 列出某凭证的全部明细。
     */
    @Select("SELECT * FROM fin_voucher_entry WHERE voucher_id = #{voucherId} AND deleted = 0 ORDER BY id")
    List<FinVoucherEntry> findByVoucher(@Param("voucherId") Long voucherId);
}
