package com.lumen.payroll.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.payroll.dto.GenerateBankFileRequest;
import com.lumen.payroll.entity.PayBankFile;
import com.lumen.payroll.entity.PaySlip;
import com.lumen.payroll.mapper.PayBankFileMapper;
import com.lumen.payroll.mapper.PaySlipMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankFileServiceTest {

    @Mock private PayBankFileMapper bankFileMapper;
    @Mock private PaySlipMapper slipMapper;

    @InjectMocks private BankFileService service;

    private static final long UID = 100L;
    private static final long TID = 1L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TID).userName("alice").build());
    }

    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    private PaySlip confirmedSlip(long id, long empId, String net) {
        PaySlip s = new PaySlip();
        s.setId(id);
        s.setTenantId(TID);
        s.setEmployeeId(empId);
        s.setStatus("confirmed");
        s.setNetSalary(new BigDecimal(net));
        return s;
    }

    private GenerateBankFileRequest req(String period, String bank) {
        GenerateBankFileRequest r = new GenerateBankFileRequest();
        r.setPeriod(period);
        r.setBankCode(bank);
        return r;
    }

    // ---------- generate: 文本格式 ----------

    @Test
    void generate_createsTextFileWithHeaderAndRows() {
        when(slipMapper.findByPeriod("2026-09")).thenReturn(List.of(
            confirmedSlip(1L, 200L, "8000.00"),
            confirmedSlip(2L, 300L, "12000.00")
        ));
        when(bankFileMapper.findByPeriodAndBankCode("2026-09", "ccb")).thenReturn(null);
        when(bankFileMapper.insert(any(PayBankFile.class))).thenAnswer(inv -> {
            PayBankFile bf = inv.getArgument(0);
            bf.setId(50L);
            return 1;
        });

        PayBankFile bf = service.generate(req("2026-09", "ccb"));
        assertEquals(50L, bf.getId());
        assertEquals("generated", bf.getStatus());
        assertEquals(2, bf.getEmployeeCount());
        // 8000 + 12000 = 20000
        assertEquals(0, bf.getTotalAmount().compareTo(new BigDecimal("20000.00")));
        assertNotNull(bf.getFileMd5());
        assertTrue(bf.getFilePath().contains("/payroll/2026-09/ccb_"));
        assertNotNull(bf.getGeneratedAt());

        // 验证文本格式 (md5 + path 由 buildTextFile 内容决定, 此处不反推)
        List<PaySlip> slips = slipMapper.findByPeriod("2026-09");
        String content = service.buildTextFile("ccb", "2026-09", slips);
        assertTrue(content.startsWith("HEADER|ccb|2026-09|2|20000.00|"));
        assertTrue(content.contains("00001|200|8000.00"));
        assertTrue(content.contains("00002|300|12000.00"));
        assertTrue(content.startsWith("HEADER", 0));
        assertTrue(content.contains("FOOTER|END|"));
    }

    @Test
    void generate_duplicateFile_throws409() {
        when(slipMapper.findByPeriod("2026-09")).thenReturn(List.of(
            confirmedSlip(1L, 200L, "8000.00")
        ));
        PayBankFile existing = new PayBankFile();
        existing.setId(99L);
        existing.setStatus("generated");
        when(bankFileMapper.findByPeriodAndBankCode("2026-09", "ccb")).thenReturn(existing);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.generate(req("2026-09", "ccb")));
        assertEquals(409, ex.getCode());
        verify(bankFileMapper, never()).insert(any());
    }

    @Test
    void generate_noConfirmedSlips_throws400() {
        when(slipMapper.findByPeriod("2026-09")).thenReturn(List.of());
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.generate(req("2026-09", "ccb")));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("No confirmed"));
    }

    @Test
    void generate_skipsDraftAndCalculated() {
        PaySlip draft = new PaySlip();
        draft.setStatus("draft"); draft.setTenantId(TID); draft.setEmployeeId(200L);
        draft.setNetSalary(new BigDecimal("9999"));
        PaySlip confirmed = confirmedSlip(1L, 200L, "8000.00");
        when(slipMapper.findByPeriod("2026-09")).thenReturn(List.of(draft, confirmed));
        when(bankFileMapper.findByPeriodAndBankCode(any(), any())).thenReturn(null);
        when(bankFileMapper.insert(any(PayBankFile.class))).thenAnswer(inv -> {
            PayBankFile bf = inv.getArgument(0);
            bf.setId(60L);
            return 1;
        });
        PayBankFile bf = service.generate(req("2026-09", "icbc"));
        assertEquals(1, bf.getEmployeeCount());
        assertEquals(0, bf.getTotalAmount().compareTo(new BigDecimal("8000.00")));
    }

    // ---------- send / confirm: 状态机 ----------

    @Test
    void send_generatedFile_succeeds() {
        PayBankFile bf = new PayBankFile();
        bf.setId(50L);
        bf.setTenantId(TID);
        bf.setStatus("generated");
        when(bankFileMapper.selectById(50L)).thenReturn(bf);
        PayBankFile out = service.send(50L);
        assertEquals("sent", out.getStatus());
        assertNotNull(out.getSentAt());
    }

    @Test
    void send_alreadySent_throws409() {
        PayBankFile bf = new PayBankFile();
        bf.setId(50L);
        bf.setTenantId(TID);
        bf.setStatus("sent");
        when(bankFileMapper.selectById(50L)).thenReturn(bf);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.send(50L));
        assertEquals(409, ex.getCode());
    }

    @Test
    void confirm_sentFile_succeeds() {
        PayBankFile bf = new PayBankFile();
        bf.setId(50L);
        bf.setTenantId(TID);
        bf.setStatus("sent");
        when(bankFileMapper.selectById(50L)).thenReturn(bf);
        PayBankFile out = service.markConfirmed(50L);
        assertEquals("confirmed", out.getStatus());
    }

    @Test
    void confirm_generated_throws409() {
        PayBankFile bf = new PayBankFile();
        bf.setId(50L);
        bf.setTenantId(TID);
        bf.setStatus("generated");
        when(bankFileMapper.selectById(50L)).thenReturn(bf);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.markConfirmed(50L));
        assertEquals(409, ex.getCode());
    }

    // ---------- 跨租户 404 ----------

    @Test
    void get_crossTenant_returns404() {
        PayBankFile other = new PayBankFile();
        other.setId(11L);
        other.setTenantId(999L);
        when(bankFileMapper.selectById(11L)).thenReturn(other);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.get(11L));
        assertEquals(404, ex.getCode());
    }
}