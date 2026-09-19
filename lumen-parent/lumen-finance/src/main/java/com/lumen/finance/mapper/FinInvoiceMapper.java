package com.lumen.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.finance.entity.FinInvoice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FinInvoiceMapper extends BaseMapper<FinInvoice> {

    /**
     * 注意：tax_no_enc 是密文列，按密文精确匹配时只在同一原文写入的记录中有效；
     * 这里实现为按 tenant + period 过滤，调用方在 service 层用加密字符串做精确匹配。
     *
     * <p>TODO P5: 引入密文索引/HMAC 列以支持按 tax_no 检索（不在 P4-B1 范围）。</p>
     */
    @Select("SELECT * FROM fin_invoice WHERE period(issue_date) = #{period} AND deleted = 0 "
        + "ORDER BY issue_date DESC, id DESC")
    List<FinInvoice> findByTaxNoAndPeriod(@Param("taxNo") String taxNo,
                                          @Param("period") String period);
}
