package com.lumen.common.mybatis.interceptor;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.lumen.common.security.context.UserContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class TenantInterceptor implements TenantLineHandler {

    private static final Set<String> IGNORE_TABLES = new HashSet<>(Arrays.asList(
        "sys_config", "sys_dict_type", "sys_dict_data",
        "tenant", "tenant_package", "lumen_application",
        "common_seq"
    ));

    @Override
    public Expression getTenantId() {
        Long tid = UserContextHolder.getTenantId();
        return tid == null ? new NullValue() : new LongValue(tid);
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return IGNORE_TABLES.contains(tableName.toLowerCase())
            || InterceptorIgnoreHelper.willIgnoreTenantLine("tenantLine");
    }
}