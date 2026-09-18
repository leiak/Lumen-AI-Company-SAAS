package com.lumen.common.mybatis.interceptor;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.lumen.common.security.context.UserContextHolder;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class FieldFillHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        Long uid = UserContextHolder.getUserId();
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createBy", Long.class, uid == null ? UserContextHolder.SYSTEM_USER_ID : uid);
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateBy", Long.class, uid == null ? UserContextHolder.SYSTEM_USER_ID : uid);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Long uid = UserContextHolder.getUserId();
        strictUpdateFill(metaObject, "updateBy", Long.class, uid == null ? UserContextHolder.SYSTEM_USER_ID : uid);
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}