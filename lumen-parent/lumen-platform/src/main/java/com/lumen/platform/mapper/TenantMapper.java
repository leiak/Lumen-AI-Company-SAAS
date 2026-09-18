package com.lumen.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.platform.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {
    default Tenant findByCode(String code) {
        return selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tenant>()
            .eq(Tenant::getCode, code)
            .eq(Tenant::getDeleted, 0));
    }
}
