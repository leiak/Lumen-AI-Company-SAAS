package com.lumen.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.inventory.dto.SaveItemRequest;
import com.lumen.inventory.entity.InvItem;
import com.lumen.inventory.mapper.InvItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SKU 主数据 CRUD。
 *
 * <p>安全要点:</p>
 * <ul>
 *   <li>code + barcode 在 (tenant_id, deleted) 内 UNIQUE。</li>
 *   <li>barcode 为 null 时不参与唯一校验 (UK 部分列)。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemService {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";

    private final InvItemMapper itemMapper;

    public UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    public IPage<InvItem> page(int pageNum, int pageSize, String keyword, String category, String status) {
        requireCtx();
        var w = new LambdaQueryWrapper<InvItem>().orderByDesc(InvItem::getId);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(InvItem::getCode, keyword)
                .or().like(InvItem::getName, keyword)
                .or().like(InvItem::getBarcode, keyword));
        }
        if (category != null && !category.isBlank()) w.eq(InvItem::getCategory, category);
        if (status != null && !status.isBlank()) w.eq(InvItem::getStatus, status);
        return itemMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public InvItem getById(Long id) {
        UserContext ctx = requireCtx();
        InvItem it = itemMapper.selectById(id);
        if (it == null) throw new ServiceException(404, "Item not found: " + id);
        if (!it.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Item not found: " + id);
        }
        return it;
    }

    public InvItem findByCode(Long tenantId, String code) {
        return itemMapper.findByCode(tenantId, code);
    }

    public InvItem findByBarcode(Long tenantId, String barcode) {
        return itemMapper.findByBarcode(tenantId, barcode);
    }

    @Transactional
    public InvItem create(SaveItemRequest req) {
        UserContext ctx = requireCtx();
        validate(req);
        if (itemMapper.findByCode(ctx.getTenantId(), req.getCode()) != null) {
            throw new ServiceException(409, "Item code already exists: " + req.getCode());
        }
        if (req.getBarcode() != null && !req.getBarcode().isBlank()
            && itemMapper.findByBarcode(ctx.getTenantId(), req.getBarcode()) != null) {
            throw new ServiceException(409, "Item barcode already exists: " + req.getBarcode());
        }
        InvItem it = new InvItem();
        it.setTenantId(ctx.getTenantId());
        it.setCode(req.getCode());
        it.setName(req.getName());
        it.setSku(req.getSku());
        it.setCategory(req.getCategory());
        it.setUnit(req.getUnit());
        it.setSpec(req.getSpec());
        it.setBarcode(req.getBarcode());
        it.setStatus(req.getStatus() == null ? STATUS_ACTIVE : req.getStatus());
        try {
            itemMapper.insert(it);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Item code/barcode conflict", ex);
        }
        log.info("Item created id={} code={} barcode={}", it.getId(), it.getCode(), it.getBarcode());
        return it;
    }

    @Transactional
    public InvItem update(Long id, SaveItemRequest req) {
        InvItem existing = getById(id);
        if (req.getName() != null && !req.getName().isBlank()) existing.setName(req.getName());
        if (req.getSku() != null) existing.setSku(req.getSku());
        if (req.getCategory() != null) existing.setCategory(req.getCategory());
        if (req.getUnit() != null && !req.getUnit().isBlank()) existing.setUnit(req.getUnit());
        if (req.getSpec() != null) existing.setSpec(req.getSpec());
        if (req.getBarcode() != null) existing.setBarcode(req.getBarcode());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        try {
            itemMapper.updateById(existing);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Barcode conflict", ex);
        }
        return existing;
    }

    private void validate(SaveItemRequest req) {
        if (req.getCode() == null || req.getCode().isBlank()) throw new ServiceException(400, "code is required");
        if (req.getName() == null || req.getName().isBlank()) throw new ServiceException(400, "name is required");
        if (req.getUnit() == null || req.getUnit().isBlank()) throw new ServiceException(400, "unit is required");
    }
}