package com.lumen.finance.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.finance.entity.FinVoucher;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 凭证 mapper。
 */
@Mapper
public interface FinVoucherMapper extends BaseMapper<FinVoucher> {

    /**
     * 按期间（yyyy-MM）查询凭证。
     */
    @Select("SELECT * FROM fin_voucher WHERE period = #{period} AND deleted = 0 ORDER BY voucher_date DESC, id DESC")
    List<FinVoucher> findByPeriod(@Param("period") String period);

    /**
     * 按 tenant + voucherNo 查唯一未删凭证（用于 VoucherNo 唯一性校验）。
     */
    default FinVoucher findByNo(String voucherNo) {
        return selectOne(new LambdaQueryWrapper<FinVoucher>()
            .eq(FinVoucher::getVoucherNo, voucherNo)
            .eq(FinVoucher::getDeleted, 0));
    }

    /**
     * 列出某期间所有 non-draft 凭证（用于 period.close 校验）。
     */
    default List<FinVoucher> listNonDraftByPeriod(String period) {
        return selectList(new LambdaQueryWrapper<FinVoucher>()
            .eq(FinVoucher::getPeriod, period)
            .ne(FinVoucher::getStatus, "draft")
            .eq(FinVoucher::getDeleted, 0));
    }
}
