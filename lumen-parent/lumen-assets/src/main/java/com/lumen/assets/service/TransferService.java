package com.lumen.assets.service;

import com.lumen.assets.dto.TransferRequest;
import com.lumen.assets.entity.AstAsset;
import com.lumen.assets.entity.AstTransfer;
import com.lumen.assets.mapper.AstAssetMapper;
import com.lumen.assets.mapper.AstTransferMapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 资产调拨。状态机：pending → approved → completed；pending → rejected 终态。
 * 仅 approved 才能 complete（updated asset dept/custodian）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_COMPLETED = "completed";

    private final AstTransferMapper transferMapper;
    private final AstAssetMapper assetMapper;

    private UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    public AstTransfer getById(Long id) {
        UserContext ctx = requireCtx();
        AstTransfer t = transferMapper.selectById(id);
        if (t == null) throw new ServiceException(404, "Transfer not found: " + id);
        if (!t.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Transfer not found: " + id);
        }
        return t;
    }

    public List<AstTransfer> findByAsset(Long assetId) {
        requireCtx();
        return transferMapper.findByAsset(assetId);
    }

    @Transactional
    public AstTransfer apply(TransferRequest req) {
        UserContext ctx = requireCtx();
        AstAsset asset = assetMapper.selectById(req.getAssetId());
        if (asset == null || !asset.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Asset not found: " + req.getAssetId());
        }
        if (AssetService.STATUS_SCRAPPED.equals(asset.getStatus())) {
            throw new ServiceException(409, "Cannot transfer scrapped asset");
        }
        AstTransfer t = new AstTransfer();
        t.setTenantId(ctx.getTenantId());
        t.setAssetId(req.getAssetId());
        t.setFromDeptId(asset.getDeptId());
        t.setFromCustodianId(asset.getCustodianId());
        t.setToDeptId(req.getToDeptId());
        t.setToCustodianId(req.getToCustodianId());
        t.setTransferDate(req.getTransferDate() != null ? req.getTransferDate() : LocalDate.now());
        t.setReason(req.getReason());
        t.setStatus(STATUS_PENDING);
        transferMapper.insert(t);
        log.info("Transfer applied id={} asset={} from={}->{}",
            t.getId(), req.getAssetId(), asset.getDeptId(), req.getToDeptId());
        return t;
    }

    @Transactional
    public AstTransfer approve(Long id) {
        AstTransfer existing = getById(id);
        if (!STATUS_PENDING.equals(existing.getStatus())) {
            throw new ServiceException(409, "Only pending transfer can be approved");
        }
        existing.setStatus(STATUS_APPROVED);
        transferMapper.updateById(existing);
        log.info("Transfer approved id={}", id);
        return existing;
    }

    @Transactional
    public AstTransfer reject(Long id, String reason) {
        AstTransfer existing = getById(id);
        if (!STATUS_PENDING.equals(existing.getStatus())) {
            throw new ServiceException(409, "Only pending transfer can be rejected");
        }
        existing.setStatus(STATUS_REJECTED);
        String rejectTag = "rejected: " + (reason == null ? "" : reason);
        existing.setReason(existing.getReason() == null
            ? rejectTag
            : existing.getReason() + " | " + rejectTag);
        transferMapper.updateById(existing);
        log.info("Transfer rejected id={} reason={}", id, reason);
        return existing;
    }

    /**
     * 完成调拨：仅 approved 才能 complete，并把 asset.dept/custodian 改成目标值。
     */
    @Transactional
    public AstTransfer complete(Long id) {
        AstTransfer existing = getById(id);
        if (!STATUS_APPROVED.equals(existing.getStatus())) {
            throw new ServiceException(409, "Transfer must be approved before complete");
        }
        AstAsset asset = assetMapper.selectById(existing.getAssetId());
        if (asset == null) {
            throw new ServiceException(404, "Asset not found: " + existing.getAssetId());
        }
        asset.setDeptId(existing.getToDeptId());
        asset.setCustodianId(existing.getToCustodianId());
        assetMapper.updateById(asset);
        existing.setStatus(STATUS_COMPLETED);
        transferMapper.updateById(existing);
        log.info("Transfer completed id={} asset={}", id, existing.getAssetId());
        return existing;
    }
}