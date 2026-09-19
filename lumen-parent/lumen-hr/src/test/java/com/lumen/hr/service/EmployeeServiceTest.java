package com.lumen.hr.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.hr.dto.SaveEmployeeRequest;
import com.lumen.hr.entity.HrEmployee;
import com.lumen.hr.entity.HrTransfer;
import com.lumen.hr.mapper.HrEmployeeMapper;
import com.lumen.hr.mapper.HrTransferMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Employee lifecycle + security tests. Coverage:
 * <ol>
 *   <li>save create / update flows</li>
 *   <li>transfer writes audit row + updates employee + publishes event</li>
 *   <li>cross-tenant read returns 404 (existence hidden)</li>
 *   <li>profile lookup from UserContextHolder</li>
 *   <li>missing tenant context returns 401</li>
 *   <li>soft-delete via mapper.deleteById</li>
 *   <li>encrypted fields pass through the service unchanged</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmployeeServiceTest {

    @Mock private HrEmployeeMapper employeeMapper;
    @Mock private HrTransferMapper transferMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private EmployeeService employeeService;

    private static final long UID = 100L;
    private static final long TID = 1L;
    private static final long OTHER_TID = 2L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice")
            .roles(new HashSet<>(Set.of("hr_admin"))).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private HrEmployee stubEmp(long id, long tenantId, Integer status) {
        HrEmployee e = new HrEmployee();
        e.setId(id);
        e.setTenantId(tenantId);
        e.setCode("E" + id);
        e.setName("Alice");
        e.setDeptId(10L);
        e.setPostId(20L);
        e.setStatus(status == null ? 0 : status);
        e.setHireDate(LocalDate.now());
        return e;
    }

    // ---------------------------------------------------------------
    // create
    // ---------------------------------------------------------------

    @Test
    void save_create_writesNewEmployeeWithTenant() {
        when(employeeMapper.insert(any(HrEmployee.class))).thenAnswer(inv -> {
            HrEmployee arg = inv.getArgument(0);
            arg.setId(501L);
            return 1;
        });

        SaveEmployeeRequest req = new SaveEmployeeRequest();
        req.setCode("E-NEW");
        req.setName("Bob");
        req.setDeptId(10L);
        req.setPostId(20L);
        req.setIdCardEnc("plaintext-id-card"); // service treats as opaque string
        req.setMobileEnc("plaintext-mobile");

        HrEmployee out = employeeService.save(req);

        assertEquals(501L, out.getId());
        assertEquals(TID, out.getTenantId());
        assertEquals("E-NEW", out.getCode());
        assertEquals("plaintext-id-card", out.getIdCardEnc(),
            "service must pass encrypted fields through verbatim; "
                + "encryption happens at the TypeHandler boundary");
    }

    // ---------------------------------------------------------------
    // update
    // ---------------------------------------------------------------

    @Test
    void save_update_appliesNonNullFields() {
        when(employeeMapper.selectById(50L)).thenReturn(stubEmp(50L, TID, 0));
        when(employeeMapper.updateById(any(HrEmployee.class))).thenAnswer(inv -> 1);

        SaveEmployeeRequest req = new SaveEmployeeRequest();
        req.setId(50L);
        req.setName("AliceNew");
        req.setDeptId(99L);

        HrEmployee out = employeeService.save(req);

        assertEquals("AliceNew", out.getName());
        assertEquals(99L, out.getDeptId());
        // Unchanged fields retain their prior values.
        assertEquals("E50", out.getCode());
    }

    @Test
    void save_update_crossTenant_throws404() {
        when(employeeMapper.selectById(50L)).thenReturn(stubEmp(50L, OTHER_TID, 0));

        SaveEmployeeRequest req = new SaveEmployeeRequest();
        req.setId(50L);
        req.setName("x");

        ServiceException ex = assertThrows(ServiceException.class,
            () -> employeeService.save(req));
        assertEquals(404, ex.getCode());
        verify(employeeMapper, never()).updateById(any());
    }

    // ---------------------------------------------------------------
    // getById
    // ---------------------------------------------------------------

    @Test
    void getById_sameTenant_returnsRecord() {
        when(employeeMapper.selectById(50L)).thenReturn(stubEmp(50L, TID, 0));
        HrEmployee out = employeeService.getById(50L);
        assertEquals(50L, out.getId());
    }

    @Test
    void getById_crossTenant_returns404NotForbidden() {
        when(employeeMapper.selectById(50L)).thenReturn(stubEmp(50L, OTHER_TID, 0));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> employeeService.getById(50L));
        assertEquals(404, ex.getCode(), "existence must not leak across tenants");
    }

    @Test
    void getById_superAdmin_bypassesTenantGuard() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("root")
            .roles(new HashSet<>(Set.of("super_admin"))).build());
        when(employeeMapper.selectById(50L)).thenReturn(stubEmp(50L, OTHER_TID, 0));

        HrEmployee out = employeeService.getById(50L);
        assertEquals(50L, out.getId());
    }

    @Test
    void getById_notFound_throws404() {
        when(employeeMapper.selectById(999L)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> employeeService.getById(999L));
        assertEquals(404, ex.getCode());
    }

    @Test
    void getById_missingTenantContext_throws401() {
        UserContextHolder.clear();
        ServiceException ex = assertThrows(ServiceException.class,
            () -> employeeService.getById(50L));
        assertEquals(401, ex.getCode());
    }

    // ---------------------------------------------------------------
    // profile
    // ---------------------------------------------------------------

    @Test
    void getProfile_usesCurrentUserIdAndTenant() {
        HrEmployee e = stubEmp(50L, TID, 0);
        e.setUserId(UID);
        when(employeeMapper.findByUserId(UID)).thenReturn(e);

        HrEmployee out = employeeService.getProfile();
        assertEquals(UID, out.getUserId());
        verify(employeeMapper).findByUserId(UID);
    }

    @Test
    void getProfile_noUserContext_throws401() {
        UserContextHolder.clear();
        ServiceException ex = assertThrows(ServiceException.class,
            () -> employeeService.getProfile());
        assertEquals(401, ex.getCode());
    }

    @Test
    void getProfile_noEmployeeRecord_throws404() {
        when(employeeMapper.findByUserId(UID)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> employeeService.getProfile());
        assertEquals(404, ex.getCode());
    }

    // ---------------------------------------------------------------
    // transfer
    // ---------------------------------------------------------------

    @Test
    void transfer_writesAuditRowAndUpdatesEmployeeAndPublishesEvent() {
        HrEmployee e = stubEmp(50L, TID, 0);
        when(employeeMapper.selectById(50L)).thenReturn(e);
        when(transferMapper.insert(any(HrTransfer.class))).thenAnswer(inv -> {
            HrTransfer t = inv.getArgument(0);
            t.setId(7001L);
            return 1;
        });
        when(employeeMapper.updateById(any(HrEmployee.class))).thenAnswer(inv -> 1);

        HrEmployee out = employeeService.transfer(50L, 99L, 88L, LocalDate.of(2026, 10, 1));

        // 1. Audit row written
        ArgumentCaptor<HrTransfer> trCaptor = ArgumentCaptor.forClass(HrTransfer.class);
        verify(transferMapper).insert(trCaptor.capture());
        HrTransfer tr = trCaptor.getValue();
        assertEquals(50L, tr.getEmployeeId());
        assertEquals(10L, tr.getFromDeptId());
        assertEquals(99L, tr.getToDeptId());
        assertEquals(20L, tr.getFromPostId());
        assertEquals(88L, tr.getToPostId());
        assertEquals(LocalDate.of(2026, 10, 1), tr.getEffectiveAt());

        // 2. Employee updated in-place
        ArgumentCaptor<HrEmployee> empCaptor = ArgumentCaptor.forClass(HrEmployee.class);
        verify(employeeMapper).updateById(empCaptor.capture());
        assertEquals(99L, empCaptor.getValue().getDeptId());
        assertEquals(88L, empCaptor.getValue().getPostId());

        // 3. Event published
        verify(eventPublisher).publishEvent(any(com.lumen.hr.event.EmployeeTransferredEvent.class));
        assertEquals(50L, out.getId());
    }

    @Test
    void transfer_missingEffectiveAt_throws400() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> employeeService.transfer(50L, 99L, 88L, null));
        assertEquals(400, ex.getCode());
        verify(transferMapper, never()).insert(any());
    }

    @Test
    void transfer_crossTenant_throws404() {
        when(employeeMapper.selectById(50L)).thenReturn(stubEmp(50L, OTHER_TID, 0));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> employeeService.transfer(50L, 99L, 88L, LocalDate.now()));
        assertEquals(404, ex.getCode());
        verify(transferMapper, never()).insert(any());
    }

    // ---------------------------------------------------------------
    // delete / soft delete
    // ---------------------------------------------------------------

    @Test
    void delete_softDeletesAndDoesNotTouchTransfer() {
        when(employeeMapper.selectById(50L)).thenReturn(stubEmp(50L, TID, 0));
        when(employeeMapper.deleteById(50L)).thenReturn(1);

        employeeService.delete(50L);

        // Soft-delete is the @TableLogic flag-flip — we only assert the call.
        verify(employeeMapper).deleteById(50L);
        verify(transferMapper, never()).insert(any());
    }

    @Test
    void delete_crossTenant_throws404() {
        when(employeeMapper.selectById(50L)).thenReturn(stubEmp(50L, OTHER_TID, 0));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> employeeService.delete(50L));
        assertEquals(404, ex.getCode());
        verify(employeeMapper, never()).deleteById(any());
    }

    // ---------------------------------------------------------------
    // encryption round-trip — service is opaque
    // ---------------------------------------------------------------

    @Test
    void encryption_isTransparentAtServiceLayer() {
        when(employeeMapper.insert(any(HrEmployee.class))).thenAnswer(inv -> {
            HrEmployee arg = inv.getArgument(0);
            arg.setId(8001L);
            return 1;
        });
        SaveEmployeeRequest req = new SaveEmployeeRequest();
        req.setCode("E-CRYPT");
        req.setName("Crypto");
        req.setDeptId(1L);
        req.setPostId(1L);
        req.setIdCardEnc("11010119900101001X");
        req.setMobileEnc("13800138000");

        HrEmployee out = employeeService.save(req);

        // Service must NOT decrypt/transform — encryption happens at the TypeHandler.
        // If a regression here encrypts at the service layer, this assertion would fail
        // because the handler-bound ciphertext is base64/IV-prefixed opaque bytes.
        assertEquals("11010119900101001X", out.getIdCardEnc());
        assertEquals("13800138000", out.getMobileEnc());
    }
}
