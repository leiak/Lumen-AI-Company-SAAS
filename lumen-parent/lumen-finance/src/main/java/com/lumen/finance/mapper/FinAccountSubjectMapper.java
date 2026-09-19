package com.lumen.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.finance.entity.FinAccountSubject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 科目 mapper。tenant_id 由 TenantLineInnerInterceptor 自动注入。
 */
@Mapper
public interface FinAccountSubjectMapper extends BaseMapper<FinAccountSubject> {

    /**
     * 按 parentId 列出所有直接子节点（tenant 自动收窄）。
     */
    @Select("SELECT * FROM fin_account_subject WHERE parent_id = #{parentId} AND deleted = 0 ORDER BY code")
    List<FinAccountSubject> listByParent(@Param("parentId") Long parentId);

    /**
     * 列出整棵树（service 层在内存中组装）。maxLevel 用于防止过深查询。
     */
    @Select("SELECT * FROM fin_account_subject WHERE deleted = 0 ORDER BY level, code")
    List<FinAccountSubject> listAllActive();
}
