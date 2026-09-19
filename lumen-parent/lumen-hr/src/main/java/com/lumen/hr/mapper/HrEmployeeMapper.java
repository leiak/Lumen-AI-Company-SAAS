package com.lumen.hr.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.hr.entity.HrEmployee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HrEmployeeMapper extends BaseMapper<HrEmployee> {

    /**
     * 按 sys_user.user_id 查找员工档案（含已软删）。
     * 租户隔离由 TenantLineInnerInterceptor 自动注入 WHERE tenant_id = ?。
     */
    default HrEmployee findByUserId(@Param("userId") Long userId) {
        return selectOne(new LambdaQueryWrapper<HrEmployee>()
            .eq(HrEmployee::getUserId, userId)
            .last("LIMIT 1"));
    }

    /**
     * 按 deptId + status 查询员工列表。
     * 租户隔离由 TenantLineInnerInterceptor 自动注入。
     */
    @Select("SELECT * FROM hr_employee WHERE dept_id = #{deptId} AND status = #{status} " +
        "AND deleted = 0 ORDER BY id ASC")
    List<HrEmployee> findByDeptAndStatus(@Param("deptId") Long deptId,
                                         @Param("status") Integer status);
}
