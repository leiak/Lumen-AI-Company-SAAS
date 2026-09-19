package com.lumen.payroll.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.payroll.entity.PaySlipItem;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 工资条明细 mapper。
 */
@Mapper
public interface PaySlipItemMapper extends BaseMapper<PaySlipItem> {

    /**
     * 按 slip 列出所有 item。
     */
    default List<PaySlipItem> findBySlip(Long slipId) {
        return selectList(new LambdaQueryWrapper<PaySlipItem>()
            .eq(PaySlipItem::getSlipId, slipId)
            .eq(PaySlipItem::getDeleted, 0)
            .orderByAsc(PaySlipItem::getId));
    }
}