package com.lumen.file.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.file.dto.CompleteChunkRequest;
import com.lumen.file.dto.InitChunkRequest;
import com.lumen.file.dto.InitChunkResponse;
import com.lumen.file.entity.FileChunk;
import com.lumen.file.entity.FileMetadata;
import com.lumen.file.mapper.FileChunkMapper;
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

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChunkServiceTest {

    @Mock private FileChunkMapper chunkMapper;
    @Mock private FileMetadataMapper metadataMapper;
    @Mock private StorageProvider storage;

    private ChunkService chunkService;
    private StorageProperties properties;

    private static final long UID = 100L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        properties = new StorageProperties();
        chunkService = new ChunkService(chunkMapper, metadataMapper, storage, properties);
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice").build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void init_returnsUploadId() {
        when(metadataMapper.selectByMd5AndTenant(anyString(), eq(TID))).thenReturn(null);
        when(storage.initMultipartUpload(anyString(), anyString(), anyString())).thenReturn("upload-abc");
        when(storage.presignedUrl(anyString(), anyString(), any(Duration.class)))
            .thenAnswer(inv -> "http://minio/" + inv.getArgument(0) + "/" + inv.getArgument(1));

        InitChunkRequest req = new InitChunkRequest();
        req.setFileMd5("abc123");
        req.setFileName("big.bin");
        req.setTotalChunks(3);
        req.setChunkSize(5L * 1024 * 1024);
        req.setTotalSize(15L * 1024 * 1024);

        InitChunkResponse resp = chunkService.init(req);

        assertNotNull(resp.getUploadId());
        assertEquals("upload-abc", resp.getUploadId());
        assertEquals(3, resp.getPresignedUrls().size());
        assertTrue(resp.getPresignedUrls().containsKey(1));
        assertTrue(resp.getPresignedUrls().containsKey(3));

        // 占位行：3 个 uploaded=0
        verify(chunkMapper, times(3)).insert(any(FileChunk.class));
    }

    @Test
    void init_duplicate_returnsNullUploadId() {
        FileMetadata existing = new FileMetadata();
        existing.setId(7L);
        existing.setMd5("abc");
        existing.setTenantId(TID);
        when(metadataMapper.selectByMd5AndTenant(eq("abc"), eq(TID))).thenReturn(existing);

        InitChunkRequest req = new InitChunkRequest();
        req.setFileMd5("abc");
        req.setFileName("x.bin");
        req.setTotalChunks(2);
        req.setChunkSize(1024L);
        req.setTotalSize(2048L);

        InitChunkResponse resp = chunkService.init(req);
        assertNull(resp.getUploadId());
        assertTrue(resp.getPresignedUrls().isEmpty());
        verify(storage, never()).initMultipartUpload(anyString(), anyString(), anyString());
        verify(chunkMapper, never()).insert(any(FileChunk.class));
    }

    @Test
    void init_exceedsMaxSize_throws413() {
        properties.setMaxFileSize(100L);
        InitChunkRequest req = new InitChunkRequest();
        req.setFileMd5("abc");
        req.setFileName("x.bin");
        req.setTotalChunks(1);
        req.setChunkSize(200L);
        req.setTotalSize(500L);

        ServiceException ex = assertThrows(ServiceException.class, () -> chunkService.init(req));
        assertEquals(413, ex.getCode());
    }

    @Test
    void uploadPart_writesRow() {
        FileChunk existing = new FileChunk();
        existing.setId(1L);
        existing.setUploadId("upload-abc");
        existing.setChunkNumber(1);
        existing.setUploader(UID);
        existing.setUploaded(0);
        when(chunkMapper.selectByUploadIdAndNumber("upload-abc", 1)).thenReturn(existing);
        when(chunkMapper.updateById(any(FileChunk.class))).thenReturn(1);

        MockMultipartFile f = new MockMultipartFile(
            "file", "part-1", "application/octet-stream", new byte[1024]);
        chunkService.uploadPart("upload-abc", 1, f);

        ArgumentCaptor<FileChunk> cap = ArgumentCaptor.forClass(FileChunk.class);
        verify(chunkMapper).updateById(cap.capture());
        FileChunk saved = cap.getValue();
        assertEquals(1, saved.getUploaded());
        assertEquals(1024, saved.getChunkSize());
        assertNotNull(saved.getStoragePath());
        verify(storage).put(eq(properties.getMinio().getBucket()), anyString(),
            any(), eq(1024L), anyString());
    }

    @Test
    void uploadPart_otherUser_throws403() {
        FileChunk existing = new FileChunk();
        existing.setId(1L);
        existing.setUploadId("upload-abc");
        existing.setChunkNumber(1);
        existing.setUploader(999L); // 不是当前用户
        when(chunkMapper.selectByUploadIdAndNumber("upload-abc", 1)).thenReturn(existing);

        MockMultipartFile f = new MockMultipartFile(
            "file", "part-1", "application/octet-stream", new byte[10]);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> chunkService.uploadPart("upload-abc", 1, f));
        assertEquals(403, ex.getCode());
        verify(chunkMapper, never()).updateById(any(FileChunk.class));
        verify(storage, never()).put(anyString(), anyString(), any(), anyLong(), anyString());
    }

    @Test
    void uploadPart_notFound_throws404() {
        when(chunkMapper.selectByUploadIdAndNumber("missing", 1)).thenReturn(null);
        MockMultipartFile f = new MockMultipartFile(
            "file", "part-1", "application/octet-stream", new byte[10]);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> chunkService.uploadPart("missing", 1, f));
        assertEquals(404, ex.getCode());
    }

    @Test
    void complete_mergesAndWritesMetadata() {
        List<FileChunk> rows = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            FileChunk r = new FileChunk();
            r.setId((long) i);
            r.setUploadId("upload-abc");
            r.setChunkNumber(i);
            r.setChunkSize(1024);
            r.setUploaded(1);
            r.setUploader(UID);
            r.setFileMd5("md5-abc");
            r.setStoragePath("upload-abc/.parts/" + i);
            r.setDeleted(0);
            r.setTotalChunks(3);
            rows.add(r);
        }
        when(chunkMapper.listByUploadId("upload-abc")).thenReturn(rows);
        when(metadataMapper.insert(any(FileMetadata.class))).thenAnswer(inv -> {
            FileMetadata m = inv.getArgument(0);
            m.setId(99L);
            return 1;
        });
        when(chunkMapper.updateById(any(FileChunk.class))).thenReturn(1);

        CompleteChunkRequest req = new CompleteChunkRequest();
        req.setUploadId("upload-abc");
        req.setTotalChunks(3);

        FileMetadata out = chunkService.complete(req);

        assertEquals(99L, out.getId());
        assertEquals(UID, out.getUploader());
        assertEquals(TID, out.getTenantId());
        assertEquals(3072L, out.getSizeBytes());
        assertEquals("md5-abc", out.getMd5());
        // chunk 行 soft-delete
        verify(chunkMapper, times(3)).updateById(any(FileChunk.class));
    }

    @Test
    void complete_chunkCountMismatch_throws400() {
        List<FileChunk> rows = List.of(makeChunk(1L, 1, UID));
        when(chunkMapper.listByUploadId("upload-abc")).thenReturn(rows);

        CompleteChunkRequest req = new CompleteChunkRequest();
        req.setUploadId("upload-abc");
        req.setTotalChunks(5); // mismatch

        ServiceException ex = assertThrows(ServiceException.class, () -> chunkService.complete(req));
        assertEquals(400, ex.getCode());
    }

    @Test
    void complete_incompleteChunk_throws409() {
        List<FileChunk> rows = new ArrayList<>();
        rows.add(makeChunk(1L, 1, UID));
        FileChunk incomplete = makeChunk(2L, 2, UID);
        incomplete.setUploaded(0);
        rows.add(incomplete);
        when(chunkMapper.listByUploadId("upload-abc")).thenReturn(rows);

        CompleteChunkRequest req = new CompleteChunkRequest();
        req.setUploadId("upload-abc");
        req.setTotalChunks(2);

        ServiceException ex = assertThrows(ServiceException.class, () -> chunkService.complete(req));
        assertEquals(409, ex.getCode());
    }

    @Test
    void complete_otherUser_throws403() {
        List<FileChunk> rows = List.of(makeChunk(1L, 1, 999L));
        when(chunkMapper.listByUploadId("upload-abc")).thenReturn(rows);

        CompleteChunkRequest req = new CompleteChunkRequest();
        req.setUploadId("upload-abc");
        req.setTotalChunks(1);

        ServiceException ex = assertThrows(ServiceException.class, () -> chunkService.complete(req));
        assertEquals(403, ex.getCode());
        verify(metadataMapper, never()).insert(any(FileMetadata.class));
    }

    @Test
    void abort_deletesAllParts() {
        List<FileChunk> rows = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            FileChunk r = makeChunk((long) i, i, UID);
            r.setStoragePath("upload-abc/.parts/" + i);
            rows.add(r);
        }
        when(chunkMapper.listByUploadId("upload-abc")).thenReturn(rows);
        when(chunkMapper.updateById(any(FileChunk.class))).thenReturn(1);

        chunkService.abort("upload-abc");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> cap = ArgumentCaptor.forClass(List.class);
        verify(storage).deleteAll(eq(properties.getMinio().getBucket()), cap.capture());
        assertEquals(3, cap.getValue().size());
        verify(chunkMapper, times(3)).updateById(any(FileChunk.class));
    }

    @Test
    void abort_otherUser_throws403() {
        List<FileChunk> rows = List.of(makeChunk(1L, 1, 999L));
        when(chunkMapper.listByUploadId("upload-abc")).thenReturn(rows);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> chunkService.abort("upload-abc"));
        assertEquals(403, ex.getCode());
        verify(storage, never()).deleteAll(anyString(), anyList());
    }

    @Test
    void abort_notFound_throws404() {
        when(chunkMapper.listByUploadId("missing")).thenReturn(Collections.emptyList());
        ServiceException ex = assertThrows(ServiceException.class,
            () -> chunkService.abort("missing"));
        assertEquals(404, ex.getCode());
    }

    private static FileChunk makeChunk(Long id, int n, long uploader) {
        FileChunk r = new FileChunk();
        r.setId(id);
        r.setUploadId("upload-abc");
        r.setChunkNumber(n);
        r.setChunkSize(1024);
        r.setUploaded(1);
        r.setUploader(uploader);
        r.setFileMd5("md5-abc");
        r.setStoragePath("upload-abc/.parts/" + n);
        r.setDeleted(0);
        r.setTotalChunks(3);
        return r;
    }
}
