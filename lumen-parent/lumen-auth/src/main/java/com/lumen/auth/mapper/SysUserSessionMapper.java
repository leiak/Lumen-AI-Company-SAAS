package com.lumen.auth.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumen.auth.entity.SysUserSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserSessionMapper extends BaseMapper<SysUserSession> {
    default SysUserSession findBySessionId(String sessionId) {
        return selectOne(new LambdaQueryWrapper<SysUserSession>()
            .eq(SysUserSession::getSessionId, sessionId)
            .last("LIMIT 1"));
    }
}