package com.lumen.finance.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.finance.entity.FinExpenseReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FinExpenseReportMapper extends BaseMapper<FinExpenseReport> {

    /**
     * 按申请人 + status 查报销单。
     */
    @Select("SELECT * FROM fin_expense_report WHERE applicant_id = #{applicantId} AND status = #{status} "
        + "AND deleted = 0 ORDER BY submitted_at DESC, id DESC")
    List<FinExpenseReport> findByApplicantAndStatus(@Param("applicantId") Long applicantId,
                                                  @Param("status") String status);

    /**
     * 列出某申请人的全部报销单（用于 my-list）。
     */
    default List<FinExpenseReport> listByApplicant(Long applicantId) {
        return selectList(new LambdaQueryWrapper<FinExpenseReport>()
            .eq(FinExpenseReport::getApplicantId, applicantId)
            .orderByDesc(FinExpenseReport::getId));
    }
}
