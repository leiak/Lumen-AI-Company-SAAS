package com.lumen.common.core;

import com.lumen.common.core.constant.CommonConstants;
import com.lumen.common.core.domain.R;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RTest {

    @Test
    void ok_shouldReturnSuccessCode() {
        R<String> r = R.ok("hello");
        assertEquals(CommonConstants.SUCCESS_CODE, r.getCode());
        assertEquals("hello", r.getData());
        assertEquals("操作成功", r.getMsg());
    }

    @Test
    void ok_withoutData_shouldReturnNullData() {
        R<Void> r = R.ok();
        assertEquals(CommonConstants.SUCCESS_CODE, r.getCode());
        assertNull(r.getData());
    }

    @Test
    void fail_shouldReturnFailCode() {
        R<Void> r = R.fail("出错了");
        assertEquals(CommonConstants.FAIL_CODE, r.getCode());
        assertEquals("出错了", r.getMsg());
    }

    @Test
    void fail_withCode_shouldUseCustomCode() {
        R<Void> r = R.fail(403, "无权限");
        assertEquals(403, r.getCode());
        assertEquals("无权限", r.getMsg());
    }
}