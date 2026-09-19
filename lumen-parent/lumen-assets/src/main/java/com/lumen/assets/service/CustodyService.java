package com.lumen.assets.service;

import com.lumen.assets.dto.ApplyCustodyRequest;
import com.lumen.assets.entity.AstAsset;
import com.lumen.assets.entity.AstCustody;
import com.lumen.assets.mapper.AstAssetMapper;
import com.lumen.assets.mapper.AstCustodyMapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 资产领用/归还。同时只能一人持有（status='active' 唯一）：
 * 申请前用 {@link AstCustodyMapper#findActiveByAsset} 校验；
 * 若已有 active 记录 → 409。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustodyService {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_RETURNED = "returned";

    private final AstCustodyMapper custodyMapper;
    private final AstAssetMapper assetMapper;

    private UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    public AstCustody getById(Long id) {
        UserContext ctx = requireCtx();
        AstCustody c = custodyMapper.selectById(id);
        if (c == null) throw new ServiceException(404, "Custody not found: " + id);
        if (!c.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Custody not found: " + id);
        }
        return c;
    }

    /**
     * 申请领用。资产必须存在且未报废；同一资产同时只能有一条 active 记录。
     */
    @Transactional
    public AstCustody apply(ApplyCustodyRequest req) {
        UserContext ctx = requireCtx();
        AstAsset asset = assetMapper.selectById(req.getAssetId());
        if (asset == null || !asset.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Asset not found: " + req.getAssetId());
        }
        if (AssetService.STATUS_SCRAPPED.equals(asset.getStatus())) {
            throw new ServiceException(409, "Cannot apply scrapped asset");
        }
        AstCustody active = custodyMapper.findActiveByAsset(req.getAssetId());
        if (active != null) {
            throw new ServiceException(409,
                "Asset already in custody (custodyId=" + active.getId()
                    + ", custodian=" + active.getCustodianId() + ")");
        }
        AstCustody c = new AstCustody();
        c.setTenantId(ctx.getTenantId());
        c.setAssetId(req.getAssetId());
        c.setCustodianId(req.getCustodianId());
        c.setStartAt(req.getStartAt());
        c.setStatus(STATUS_ACTIVE);
        custodyMapper.insert(c);
        // 同步资产保管人
        asset.setCustodianId(req.getCustodianId());
        if (AssetService.STATUS_IN_STOCK.equals(asset.getStatus())) {
            asset.setStatus(AssetService.STATUS_IN_USE);
        }
        assetMapper.updateById(asset);
        log.info("Custody applied id={} asset={} custodian={}",
            c.getId(), req.getAssetId(), req.getCustodianId());
        return c;
    }

    @Transactional
    public AstCustody returnCustody(Long id, LocalDateTime returnAt) {
        AstCustody existing = getById(id);
        if (STATUS_RETURNED.equals(existing.getStatus())) {
            throw new ServiceException(409, "Custody already returned: " + id);
        }
        existing.setStatus(STATUS_RETURNED);
        existing.setReturnAt(returnAt);
        if (existing.getEndAt() == null) existing.setEndAt(returnAt);
        custodyMapper.updateById(existing);
        log.info("Custody returned id={} asset={} at={}",
            id, existing.getAssetId(), returnAt);
        return existing;
    }

    public List<AstCustody> findByAsset(Long assetId) {
        requireCtx();
        return custodyMapper.findByAsset(assetId);
    }

    public List<AstCustody> findActiveByCustodian(Long custodianId) {
        requireCtx();
        return custodyMapper.findActiveByCustodian(custodianId);
    }
}