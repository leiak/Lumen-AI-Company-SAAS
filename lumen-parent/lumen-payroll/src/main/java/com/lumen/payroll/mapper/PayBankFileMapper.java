package com.lumen.payroll.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.payroll.entity.PayBankFile;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 银行报盘文件 mapper。
 */
@Mapper
public interface PayBankFileMapper extends BaseMapper<PayBankFile> {

    /**
     * 按 period 列出。
     */
    default List<PayBankFile> findByPeriod(String period) {
        return selectList(new LambdaQueryWrapper<PayBankFile>()
            .eq(PayBankFile::getPeriod, period)
            .eq(PayBankFile::getDeleted, 0)
            .orderByDesc(PayBankFile::getId));
    }

    /**
     * 按 bankCode 列出。
     */
    default List<PayBankFile> findByBankCode(String bankCode) {
        return selectList(new LambdaQueryWrapper<PayBankFile>()
            .eq(PayBankFile::getBankCode, bankCode)
            .eq(PayBankFile::getDeleted, 0)
            .orderByDesc(PayBankFile::getId));
    }

    /**
     * 按 (period, bankCode) 查唯一。UNIQUE(tenant_id, period, bank_code, deleted).
     */
    default PayBankFile findByPeriodAndBankCode(String period, String bankCode) {
        return selectOne(new LambdaQueryWrapper<PayBankFile>()
            .eq(PayBankFile::getPeriod, period)
            .eq(PayBankFile::getBankCode, bankCode)
            .eq(PayBankFile::getDeleted, 0));
    }
}