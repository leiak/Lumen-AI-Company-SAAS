package com.lumen.bi.service;

import com.lumen.bi.entity.BiPermission;
import com.lumen.bi.mapper.BiPermissionMapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock private BiPermissionMapper permissionMapper;
    @InjectMocks private PermissionService service;

    private static final long UID = 100L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice")
            .roles(Set.of("user"))
            .build());
    }

    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    private BiPermission stub(long resourceId, String principalType, long principalId, String perm) {
        BiPermission p = new BiPermission();
        p.setId(1L);
        p.setTenantId(TID);
        p.setResourceType(PermissionService.RESOURCE_DASHBOARD);
        p.setResourceId(resourceId);
        p.setPrincipalType(principalType);
        p.setPrincipalId(principalId);
        p.setPermission(perm);
        return p;
    }

    // ---------- checkAccess: 校验链 ----------

    @Test
    void checkAccess_noPermissions_returnsFalse() {
        when(permissionMapper.findByResource("dashboard", 7L)).thenReturn(List.of());
        boolean ok = service.checkAccess("dashboard", 7L, "user", UID);
        assertFalse(ok);
    }

    @Test
    void checkAccess_matchingUserView_returnsTrue() {
        when(permissionMapper.findByResource("dashboard", 7L))
            .thenReturn(List.of(stub(7L, "user", UID, "view")));
        boolean ok = service.checkAccess("dashboard", 7L, "user", UID);
        assertTrue(ok);
    }

    @Test
    void checkAccess_matchingDifferentUser_returnsFalse() {
        when(permissionMapper.findByResource("dashboard", 7L))
            .thenReturn(List.of(stub(7L, "user", 999L, "view")));
        boolean ok = service.checkAccess("dashboard", 7L, "user", UID);
        assertFalse(ok);
    }

    @Test
    void checkAccess_matchingRole_returnsTrue() {
        when(permissionMapper.findByResource("dashboard", 7L))
            .thenReturn(List.of(stub(7L, "role", 5L, "edit")));
        boolean ok = service.checkAccess("dashboard", 7L, "role", 5L);
        assertTrue(ok);
    }

    @Test
    void checkAccess_adminRole_bypassesCheck() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("admin")
            .roles(Set.of("bi_admin")).build());
        // 即使无授权记录, admin 直接通过
        boolean ok = service.checkAccess("dashboard", 7L, "user", UID);
        assertTrue(ok);
        verify(permissionMapper, never()).findByResource(any(), any());
    }

    @Test
    void checkAccess_superAdminRole_bypassesCheck() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("admin")
            .roles(Set.of("super_admin")).build());
        boolean ok = service.checkAccess("dashboard", 7L, "user", UID);
        assertTrue(ok);
    }

    @Test
    void checkAccess_adminPrincipal_bypassesCheck() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("admin")
            .roles(Set.of("admin")).build());
        boolean ok = service.checkAccess("dashboard", 7L, "user", UID);
        assertTrue(ok);
    }

    @Test
    void checkAccess_nullPrincipalId_returnsFalse() {
        boolean ok = service.checkAccess("dashboard", 7L, "user", null);
        assertFalse(ok);
    }

    // ---------- grant / revoke ----------

    @Test
    void grant_newPermission_inserts() {
        when(permissionMapper.findExisting("dashboard", 7L, "user", UID)).thenReturn(null);
        when(permissionMapper.insert(any(BiPermission.class))).thenAnswer(inv -> {
            BiPermission p = inv.getArgument(0);
            p.setId(50L);
            return 1;
        });
        BiPermission out = service.grant("dashboard", 7L, "user", UID, "view");
        assertEquals(50L, out.getId());
        assertEquals(UID, out.getGrantedBy());
        assertNotNull(out.getGrantedAt());
    }

    @Test
    void grant_existingPermission_updates() {
        BiPermission existing = stub(7L, "user", UID, "view");
        when(permissionMapper.findExisting("dashboard", 7L, "user", UID)).thenReturn(existing);
        BiPermission out = service.grant("dashboard", 7L, "user", UID, "admin");
        assertEquals("admin", out.getPermission());
        verify(permissionMapper, never()).insert(any());
    }

    @Test
    void revoke_notFound_throws404() {
        when(permissionMapper.findExisting("dashboard", 7L, "user", UID)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.revoke("dashboard", 7L, "user", UID));
        assertEquals(404, ex.getCode());
    }

    @Test
    void revoke_existing_deletes() {
        BiPermission existing = stub(7L, "user", UID, "view");
        existing.setId(99L);
        when(permissionMapper.findExisting("dashboard", 7L, "user", UID)).thenReturn(existing);
        service.revoke("dashboard", 7L, "user", UID);
        verify(permissionMapper).deleteById(99L);
    }

    // ---------- currentPrincipal ----------

    @Test
    void currentPrincipal_returnsCtx() {
        PermissionService.Principal p = service.currentPrincipal();
        assertEquals(UID, p.userId());
        assertTrue(p.roles().contains("user"));
    }

    @Test
    void currentPrincipal_nullCtx_throws401() {
        UserContextHolder.clear();
        assertEquals(401, assertThrows(ServiceException.class,
            () -> service.currentPrincipal()).getCode());
    }
}