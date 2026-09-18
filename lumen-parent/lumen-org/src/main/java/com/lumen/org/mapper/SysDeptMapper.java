package com.lumen.org.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.org.entity.SysDept;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysDeptMapper extends BaseMapper<SysDept> {
    default List<SysDept> listAllActive() {
        return selectList(new LambdaQueryWrapper<SysDept>()
            .eq(SysDept::getStatus, "0")
            .orderByAsc(SysDept::getAncestors, SysDept::getOrderNum));
    }
}