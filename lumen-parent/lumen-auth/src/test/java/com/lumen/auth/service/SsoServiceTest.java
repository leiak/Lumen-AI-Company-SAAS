package com.lumen.auth.service;

import com.lumen.auth.entity.SysUser;
import com.lumen.auth.entity.SysUserSession;
import com.lumen.auth.mapper.SysUserMapper;
import com.lumen.common.core.constant.CommonConstants;
import com.lumen.common.core.domain.R;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.jwt.JwtProperties;
import com.lumen.common.security.jwt.JwtTokenProvider;
import com.lumen.common.sso.TicketManager;
import com.lumen.common.sso.TicketManager.TicketPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * SsoService 单元测试：覆盖签发、消费、双花、过期、空值等关键路径。
 * <p>
 * 不依赖 Spring 上下文，也不连真实数据库/Redis——所有外部协作者均为 mock。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class SsoServiceTest {

    @Mock
    private TicketManager ticketManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private SsoService ssoService;

    private static final Long USER_ID = 1001L;
    private static final Long TENANT_ID = 1L;
    private static final String APP_ID = "lumen-admin";
    private static final String TICKET = "stub-ticket-abc123";

    @BeforeEach
    void setUp() {
        // 仅在 happy path 中使用；其他测试短路返回；用 lenient() 避免 StrictStubbing 报错。
        lenient().when(jwtProperties.getAccessExpireSeconds()).thenReturn(7200L);
    }

    // ---------------------------------------------------------------
    // issue
    // ---------------------------------------------------------------

    @Test
    @DisplayName("issue: 票据签发成功，返回 R.ok 且 ticket 不为空")
    void issue_returnsOkWithTicket() {
        when(ticketManager.issue(USER_ID, TENANT_ID, APP_ID, null)).thenReturn(TICKET);

        R<SsoService.IssueResult> result = ssoService.issue(USER_ID, TENANT_ID, APP_ID);

        assertNotNull(result);
        assertEquals(CommonConstants.SUCCESS_CODE, result.getCode());
        assertNotNull(result.getData());
        assertEquals(TICKET, result.getData().ticket());
        assertNotNull(result.getData().expiresAt());
        verify(ticketManager, times(1)).issue(USER_ID, TENANT_ID, APP_ID, null);
    }

    @Test
    @DisplayName("issue: 非法入参由 manager 抛出，由 service 翻译为 R.fail")
    void issue_rejectsInvalidUserId() {
        // manager.validateUserId 会拒绝 userId<=0；SsoService.issue 把 IllegalArgumentException
        // 翻译为 R.fail（CRITICAL M9：避免与 manager 重复校验）。
        when(ticketManager.issue(eq(0L), eq(TENANT_ID), eq(APP_ID), any()))
            .thenThrow(new IllegalArgumentException("userId must be a positive number, got 0"));

        R<SsoService.IssueResult> result = ssoService.issue(0L, TENANT_ID, APP_ID);
        assertEquals(CommonConstants.FAIL_CODE, result.getCode());
    }

    @Test
    @DisplayName("issue: appId 为空时使用默认 lumen-default")
    void issue_defaultsAppId() {
        when(ticketManager.issue(USER_ID, TENANT_ID, "lumen-default", null)).thenReturn(TICKET);
        R<SsoService.IssueResult> result = ssoService.issue(USER_ID, TENANT_ID, null);
        assertEquals(CommonConstants.SUCCESS_CODE, result.getCode());
        verify(ticketManager).issue(USER_ID, TENANT_ID, "lumen-default", null);
    }

    // ---------------------------------------------------------------
    // exchange
    // ---------------------------------------------------------------

    @Test
    @DisplayName("exchange: 票据消费成功，返回 token + sessionId 并复用 SessionService")
    void exchange_happyPath() {
        LocalDateTime exp = LocalDateTime.now().plusMinutes(5);
        TicketPrincipal principal = new TicketPrincipal(USER_ID, TENANT_ID, APP_ID, exp);
        when(ticketManager.consume(TICKET, APP_ID)).thenReturn(principal);
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("jwt.token.value");

        SysUser stub = new SysUser();
        stub.setUserId(USER_ID);
        stub.setTenantId(TENANT_ID);
        stub.setUserName("alice");
        stub.setNickName("Alice");
        stub.setDeptId(42L);
        stub.setDataScope(3);
        when(userMapper.findByUserId(USER_ID)).thenReturn(stub);

        SysUserSession session = new SysUserSession();
        session.setSessionId("sess-uuid");
        when(sessionService.createSession(eq(USER_ID), eq(TENANT_ID), eq("Alice"),
            any(), any(), eq("SSO"))).thenReturn(session);

        R<SsoService.ExchangeResult> result = ssoService.exchange(TICKET, APP_ID, "1.2.3.4", "ua/1.0");

        assertNotNull(result);
        assertEquals(CommonConstants.SUCCESS_CODE, result.getCode());
        SsoService.ExchangeResult body = result.getData();
        assertNotNull(body);
        assertEquals("jwt.token.value", body.token());
        assertEquals("sess-uuid", body.sessionId());
        assertEquals(USER_ID, body.userId());
        assertEquals(TENANT_ID, body.tenantId());
        assertEquals("alice", body.userName());

        // JWT 必须包含 deptId/dataScope（M8/I8）。
        ArgumentCaptor<UserContext> ctxCap = ArgumentCaptor.forClass(UserContext.class);
        verify(jwtTokenProvider).generateAccessToken(ctxCap.capture());
        UserContext used = ctxCap.getValue();
        assertEquals(42L, used.getDeptId());
        assertEquals(3, used.getDataScope());
        assertEquals("sess-uuid", used.getTokenId());

        // 必须复用 SessionService.createSession（C3）。
        verify(sessionService).createSession(eq(USER_ID), eq(TENANT_ID), eq("Alice"),
            eq("1.2.3.4"), eq("ua/1.0"), eq("SSO"));
        // SsoService 自己不再写 Redis——Redis 由 SessionService 负责。
    }

    @Test
    @DisplayName("exchange: 已消费票据返回 401 (双花保护)")
    void exchange_doubleConsumeFails() {
        when(ticketManager.consume(TICKET, APP_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> ssoService.exchange(TICKET, APP_ID, "ip", "ua"));
        assertEquals(CommonConstants.UNAUTHORIZED, ex.getCode());
        verify(jwtTokenProvider, never()).generateAccessToken(any());
        verifyNoInteractions(sessionService);
    }

    @Test
    @DisplayName("exchange: 过期票据返回 401（与双花走同一路径）")
    void exchange_expiredTicketFails() {
        // 过期由 consume 内部 SQL WHERE 过滤：UPDATE 影响 0 行 → 返回 null。
        when(ticketManager.consume(TICKET, APP_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> ssoService.exchange(TICKET, APP_ID, "ip", "ua"));
        assertEquals(CommonConstants.UNAUTHORIZED, ex.getCode());
        verify(jwtTokenProvider, never()).generateAccessToken(any());
        verifyNoInteractions(sessionService);
    }

    @Test
    @DisplayName("exchange: ticket 为空抛出 400")
    void exchange_rejectsBlankTicket() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> ssoService.exchange(null, APP_ID, "ip", "ua"));
        assertEquals(400, ex.getCode());
        verifyNoInteractions(ticketManager);

        ServiceException ex2 = assertThrows(ServiceException.class,
            () -> ssoService.exchange("   ", APP_ID, "ip", "ua"));
        assertEquals(400, ex2.getCode());
    }

    @Test
    @DisplayName("exchange: appId 为空抛出 400")
    void exchange_rejectsBlankAppId() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> ssoService.exchange(TICKET, null, "ip", "ua"));
        assertEquals(400, ex.getCode());
        verifyNoInteractions(ticketManager);

        ServiceException ex2 = assertThrows(ServiceException.class,
            () -> ssoService.exchange(TICKET, "  ", "ip", "ua"));
        assertEquals(400, ex2.getCode());
    }

    @Test
    @DisplayName("exchange: user 记录不存在时仍可兑换（昵称字段为 null）")
    void exchange_worksWhenUserRecordMissing() {
        LocalDateTime exp = LocalDateTime.now().plusMinutes(5);
        TicketPrincipal principal = new TicketPrincipal(USER_ID, TENANT_ID, APP_ID, exp);
        when(ticketManager.consume(TICKET, APP_ID)).thenReturn(principal);
        when(userMapper.findByUserId(USER_ID)).thenReturn(null);
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("jwt.token.value");

        SysUserSession session = new SysUserSession();
        session.setSessionId("sess-uuid");
        when(sessionService.createSession(eq(USER_ID), eq(TENANT_ID), isNull(),
            any(), any(), eq("SSO"))).thenReturn(session);

        R<SsoService.ExchangeResult> result = ssoService.exchange(TICKET, APP_ID, "ip", "ua");

        assertEquals(CommonConstants.SUCCESS_CODE, result.getCode());
        assertEquals(USER_ID, result.getData().userId());
        assertNull(result.getData().userName());
        verify(sessionService).createSession(eq(USER_ID), eq(TENANT_ID), isNull(),
            eq("ip"), eq("ua"), eq("SSO"));
    }
}