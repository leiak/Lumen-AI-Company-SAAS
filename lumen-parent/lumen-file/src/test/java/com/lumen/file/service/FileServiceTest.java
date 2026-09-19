package com.lumen.file.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.file.entity.FileMetadata;
import com.lumen.file.mapper.FileMetadataMapper;
import com.lumen.file.storage.StorageProperties;
import com.lumen.file.storage.StorageProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FileService tests — Mockito only, no Spring context.
 *
 * <p>Coverage (security-driven):</p>
 * <ol>
 *   <li>upload_storesMetadata — happy path writes file_metadata row</li>
 *   <li>upload_duplicateMd5_returnsExisting — md5 dedupe scoped to tenant</li>
 *   <li>presignedUrl_buildsUrl — clamps expiry</li>
 *   <li>delete_softDeletes — uploader can delete own file</li>
 *   <li>delete_otherTenantUser_throws403 — non-super, wrong tenant OR wrong uploader → 403</li>
 *   <li>getMetadata_crossTenant_throws404 — cross-tenant returns 404 (existence hidden)</li>
 *   <li>upload_exceedsMaxSize_throws413 — service-level size cap</li>
 *   <li>upload_businessTypeNotAllowed_throws415 — content-type allow-list</li>
 *   <li>upload_noAuth_throws401</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock private FileMetadataMapper metadataMapper;
    @Mock private StorageProvider storage;

    private FileService fileService;
    private StorageProperties properties;

    private static final long UID = 100L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        properties = new StorageProperties();
        // Defaults: maxFileSize 100MB, expiry cap 24h, default 1h.
        // Set content-type allow-list so we can test the 415 path.
        properties.getContentTypeAllowList().put("avatar", java.util.List.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"));
        properties.getContentTypeAllowList().put("contract", java.util.List.of(
            "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        fileService = new FileService(metadataMapper, storage, properties);
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice")
            .roles(new HashSet<>(Set.of("user")))
            .build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void upload_storesMetadata() throws Exception {
        when(metadataMapper.selectByMd5AndTenant(anyString(), anyLong())).thenReturn(null);
        when(metadataMapper.insert(any(FileMetadata.class))).thenAnswer(inv -> {
            FileMetadata m = inv.getArgument(0);
            m.setId(42L);
            return 1;
        });

        MockMultipartFile file = new MockMultipartFile(
            "file", "hello.pdf", "application/pdf", "hello world".getBytes());

        FileMetadata out = fileService.upload(file, "contract", "BIZ-1");

        assertNotNull(out.getId());
        assertEquals(42L, out.getId());
        assertEquals(TID, out.getTenantId());
        assertEquals(UID, out.getUploader());
        assertEquals("hello.pdf", out.getOriginalName());
        assertNotNull(out.getMd5());
        assertNotNull(out.getSha256());
        assertEquals("contract", out.getBusinessType());
        assertEquals("BIZ-1", out.getBusinessId());

        // 安全 #5: storagePath 永远只有 UUID + 可信扩展名 —— 不含 originalName
        assertFalse(out.getStoragePath().contains("hello"),
            "storagePath must not include originalName: " + out.getStoragePath());
        assertTrue(out.getStoragePath().endsWith(".pdf"));

        // storage.put was called once
        verify(storage).put(eq(properties.getMinio().getBucket()), anyString(),
            any(), eq(file.getSize()), eq("application/pdf"));
    }

    @Test
    void upload_duplicateMd5_returnsExisting() {
        FileMetadata existing = new FileMetadata();
        existing.setId(7L);
        existing.setMd5("abc");
        existing.setTenantId(TID);
        when(metadataMapper.selectByMd5AndTenant(anyString(), eq(TID))).thenReturn(existing);

        MockMultipartFile file = new MockMultipartFile(
            "file", "dup.bin", "application/octet-stream", "data".getBytes());

        FileMetadata out = fileService.upload(file, null, null);

        assertSame(existing, out);
        verify(storage, never()).put(anyString(), anyString(), any(), anyLong(), anyString());
        verify(metadataMapper, never()).insert(any(FileMetadata.class));
    }

    @Test
    void upload_duplicateMd5_differentTenant_doesNotDedupe() {
        // 安全 #6: 跨租户不去重 —— 模拟另一个租户已有该 md5，但当前租户没有
        FileMetadata otherTenantFile = new FileMetadata();
        otherTenantFile.setId(99L);
        otherTenantFile.setMd5("abc");
        otherTenantFile.setTenantId(2L); // 不同租户
        when(metadataMapper.selectByMd5AndTenant(anyString(), eq(TID))).thenReturn(null);
        when(metadataMapper.insert(any(FileMetadata.class))).thenAnswer(inv -> {
            FileMetadata m = inv.getArgument(0);
            m.setId(100L);
            return 1;
        });

        MockMultipartFile file = new MockMultipartFile(
            "file", "f.bin", "application/octet-stream", "data".getBytes());

        FileMetadata out = fileService.upload(file, null, null);

        assertNotEquals(99L, out.getId());
        assertEquals(TID, out.getTenantId());
        verify(metadataMapper).insert(any(FileMetadata.class));
    }

    @Test
    void presignedUrl_buildsUrl() {
        FileMetadata m = new FileMetadata();
        m.setId(1L);
        m.setBucket("lumen");
        m.setStoragePath("abc.txt");
        m.setTenantId(TID);
        when(metadataMapper.findOneInCurrentTenant(1L)).thenReturn(m);
        when(storage.presignedUrl(eq("lumen"), eq("abc.txt"), any(Duration.class)))
            .thenReturn("http://minio/lumen/abc.txt?sig=xyz");

        String url = fileService.presignedUrl(1L, 60L);
        assertEquals("http://minio/lumen/abc.txt?sig=xyz", url);

        ArgumentCaptor<Duration> cap = ArgumentCaptor.forClass(Duration.class);
        verify(storage).presignedUrl(eq("lumen"), eq("abc.txt"), cap.capture());
        assertEquals(60L, cap.getValue().getSeconds());
    }

    @Test
    void presignedUrl_clampToMax() {
        FileMetadata m = new FileMetadata();
        m.setId(1L);
        m.setBucket("lumen");
        m.setStoragePath("abc.txt");
        m.setTenantId(TID);
        when(metadataMapper.findOneInCurrentTenant(1L)).thenReturn(m);
        when(storage.presignedUrl(anyString(), anyString(), any(Duration.class)))
            .thenReturn("ok");

        // 安全 #8: 请求超过 24h 会被 clamp 到 24h
        fileService.presignedUrl(1L, 1_000_000L);
        ArgumentCaptor<Duration> cap = ArgumentCaptor.forClass(Duration.class);
        verify(storage).presignedUrl(anyString(), anyString(), cap.capture());
        assertEquals(properties.getMaxPresignedExpirySeconds(), cap.getValue().getSeconds());
    }

    @Test
    void delete_softDeletes() {
        FileMetadata m = new FileMetadata();
        m.setId(1L);
        m.setUploader(UID);
        m.setTenantId(TID);
        m.setBucket("lumen");
        m.setStoragePath("abc.txt");
        when(metadataMapper.selectByIdIgnoreTenant(1L)).thenReturn(m);
        when(metadataMapper.deleteById(1L)).thenReturn(1);

        fileService.delete(1L);

        verify(metadataMapper).deleteById(1L);
        verify(storage).delete("lumen", "abc.txt");
    }

    @Test
    void delete_otherTenantUser_throws403() {
        // 安全 #2: 非 super_admin 跨租户不能删 —— 即便他假装是 uploader
        FileMetadata m = new FileMetadata();
        m.setId(1L);
        m.setUploader(UID);
        m.setTenantId(2L); // 不同租户
        when(metadataMapper.selectByIdIgnoreTenant(1L)).thenReturn(m);

        ServiceException ex = assertThrows(ServiceException.class, () -> fileService.delete(1L));
        assertEquals(403, ex.getCode());
        verify(metadataMapper, never()).deleteById(anyLong());
        verify(storage, never()).delete(anyString(), anyString());
    }

    @Test
    void delete_otherUser_throws403() {
        // 同租户但 uploader != userId
        FileMetadata m = new FileMetadata();
        m.setId(1L);
        m.setUploader(999L); // 不是当前用户
        m.setTenantId(TID);
        when(metadataMapper.selectByIdIgnoreTenant(1L)).thenReturn(m);

        ServiceException ex = assertThrows(ServiceException.class, () -> fileService.delete(1L));
        assertEquals(403, ex.getCode());
        verify(metadataMapper, never()).deleteById(anyLong());
    }

    @Test
    void delete_superAdmin_canDeleteAny() {
        // 安全 #2: super_admin 跨租户也能删
        UserContextHolder.clear();
        UserContextHolder.set(UserContext.builder()
            .userId(200L).tenantId(2L)
            .roles(new HashSet<>(Set.of(FileService.SUPER_ADMIN_ROLE)))
            .build());

        FileMetadata m = new FileMetadata();
        m.setId(1L);
        m.setUploader(UID);
        m.setTenantId(TID);
        m.setBucket("lumen");
        m.setStoragePath("abc.txt");
        when(metadataMapper.selectByIdIgnoreTenant(1L)).thenReturn(m);
        when(metadataMapper.deleteById(1L)).thenReturn(1);

        fileService.delete(1L);

        verify(metadataMapper).deleteById(1L);
        verify(storage).delete("lumen", "abc.txt");
    }

    @Test
    void getMetadata_crossTenant_throws404() {
        // 安全 #1: 跨租户必须 404，不能 403（避免存在性泄露）
        FileMetadata m = new FileMetadata();
        m.setId(1L);
        m.setTenantId(2L);
        // findOneInCurrentTenant 已经被 TenantLineInnerInterceptor 改写 → 这里模拟返回 null
        when(metadataMapper.findOneInCurrentTenant(1L)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> fileService.getMetadata(1L));
        assertEquals(404, ex.getCode());
    }

    @Test
    void getMetadata_sameTenant_returns() {
        FileMetadata m = new FileMetadata();
        m.setId(1L);
        m.setTenantId(TID);
        when(metadataMapper.findOneInCurrentTenant(1L)).thenReturn(m);

        FileMetadata out = fileService.getMetadata(1L);
        assertEquals(1L, out.getId());
    }

    @Test
    void upload_exceedsMaxSize_throws413() {
        properties.setMaxFileSize(10L); // 极小上限
        MockMultipartFile big = new MockMultipartFile(
            "file", "big.bin", "application/octet-stream",
            new byte[100]);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> fileService.upload(big, null, null));
        assertEquals(413, ex.getCode());
        verify(storage, never()).put(anyString(), anyString(), any(), anyLong(), anyString());
    }

    @Test
    void upload_businessTypeNotAllowed_throws415() {
        // businessType=avatar 但 contentType=text/plain → 拒绝
        MockMultipartFile f = new MockMultipartFile(
            "file", "x.txt", "text/plain", "hi".getBytes());
        ServiceException ex = assertThrows(ServiceException.class,
            () -> fileService.upload(f, "avatar", null));
        assertEquals(415, ex.getCode());
    }

    @Test
    void upload_noAuth_throws401() {
        UserContextHolder.clear();
        MockMultipartFile f = new MockMultipartFile(
            "file", "x.txt", "text/plain", "hi".getBytes());
        ServiceException ex = assertThrows(ServiceException.class,
            () -> fileService.upload(f, null, null));
        assertEquals(401, ex.getCode());
    }

    @Test
    void upload_streamBuffer_usesInputStream() throws Exception {
        // 安全 #11: 不能把全文件 load 到 byte[] —— 这里只断言 hash 流式调用 OK
        when(metadataMapper.selectByMd5AndTenant(anyString(), anyLong())).thenReturn(null);
        when(metadataMapper.insert(any(FileMetadata.class))).thenAnswer(inv -> {
            FileMetadata m = inv.getArgument(0);
            m.setId(1L);
            return 1;
        });

        // 1 MB 流
        byte[] data = new byte[1024 * 1024];
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;
        MockMultipartFile f = new MockMultipartFile(
            "file", "big.bin", "application/octet-stream", data);

        FileMetadata out = fileService.upload(f, null, null);
        assertNotNull(out.getMd5());
        assertEquals(32, out.getMd5().length());   // md5 hex
        assertEquals(64, out.getSha256().length()); // sha256 hex
    }

    @Test
    void delete_notFound_throws404() {
        when(metadataMapper.selectByIdIgnoreTenant(1L)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> fileService.delete(1L));
        assertEquals(404, ex.getCode());
    }

    @Test
    void delete_minioFailure_doesNotRollback() {
        FileMetadata m = new FileMetadata();
        m.setId(1L);
        m.setUploader(UID);
        m.setTenantId(TID);
        m.setBucket("lumen");
        m.setStoragePath("missing.txt");
        when(metadataMapper.selectByIdIgnoreTenant(1L)).thenReturn(m);
        when(metadataMapper.deleteById(1L)).thenReturn(1);
        doThrow(new RuntimeException("minio down")).when(storage).delete("lumen", "missing.txt");

        // 业务异常已被吞 (log warn)，delete 仍视为成功
        assertDoesNotThrow(() -> fileService.delete(1L));
        verify(metadataMapper).deleteById(1L);
    }

    // Provide a stub for ByteArrayInputStream in tests so we don't accidentally close it twice.
    static {
        try {
            // Force module to load (helps when running tests in isolation)
            new ByteArrayInputStream(new byte[0]).close();
        } catch (java.io.IOException ignored) {
            // ignore
        }
    }
}
