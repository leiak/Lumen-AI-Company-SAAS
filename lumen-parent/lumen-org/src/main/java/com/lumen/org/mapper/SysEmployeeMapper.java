package com.lumen.org.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.org.entity.SysEmployee;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysEmployeeMapper extends BaseMapper<SysEmployee> {
    default SysEmployee findByUserId(Long userId) {
        return selectOne(new LambdaQueryWrapper<SysEmployee>()
            .eq(SysEmployee::getUserId, userId)
            .last("LIMIT 1"));
    }
}