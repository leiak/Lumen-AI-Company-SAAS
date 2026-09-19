package com.lumen.finance.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.finance.entity.FinReceivable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FinReceivableMapper extends BaseMapper<FinReceivable> {

    /**
     * 按 customer + status 查询应收单。
     */
    @Select("SELECT * FROM fin_receivable WHERE customer_id = #{customerId} AND status = #{status} "
        + "AND deleted = 0 ORDER BY due_date ASC, id DESC")
    List<FinReceivable> findByCustomerAndStatus(@Param("customerId") Long customerId,
                                               @Param("status") String status);

    /**
     * 按 tenant 列出 pending 或 partial 应收单（用于 period.close 校验）。
     */
    default List<FinReceivable> listOpenForCloseCheck() {
        return selectList(new LambdaQueryWrapper<FinReceivable>()
            .in(FinReceivable::getStatus, "pending", "partial")
            .eq(FinReceivable::getDeleted, 0));
    }
}
