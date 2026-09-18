package com.lumen.common.security.context;

public class UserContextHolder {

    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    public static void set(UserContext ctx) {
        CONTEXT.set(ctx);
    }

    public static UserContext get() {
        return CONTEXT.get();
    }

    public static Long getUserId() {
        UserContext ctx = CONTEXT.get();
        return ctx == null ? null : ctx.getUserId();
    }

    public static Long getTenantId() {
        UserContext ctx = CONTEXT.get();
        return ctx == null ? null : ctx.getTenantId();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}