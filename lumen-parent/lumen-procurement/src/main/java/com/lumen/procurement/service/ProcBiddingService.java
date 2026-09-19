package com.lumen.procurement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.procurement.dto.BiddingEvaluationDto;
import com.lumen.procurement.entity.ProcBidding;
import com.lumen.procurement.entity.ProcBiddingParticipant;
import com.lumen.procurement.entity.ProcSupplier;
import com.lumen.procurement.mapper.ProcBiddingMapper;
import com.lumen.procurement.mapper.ProcBiddingParticipantMapper;
import com.lumen.procurement.mapper.ProcSupplierMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 招投标流程。
 *
 * <p>状态机: draft → published → evaluating → awarded → closed。</p>
 * <p>安全要点:</p>
 * <ul>
 *   <li>publish: draft → published, 需校验 window 合法。</li>
 *   <li>inviteSuppliers: 仅 draft / published 可邀请, 黑名单不可参与。</li>
 *   <li>evaluate: 仅 published 状态可评估; status=joined 参与评分。</li>
 *   <li>award: 仅 published/evaluating 状态可定标; 只能选 status=joined 的参与方。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcBiddingService {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_EVALUATING = "evaluating";
    public static final String STATUS_AWARDED = "awarded";
    public static final String STATUS_CLOSED = "closed";

    public static final String PARTICIPANT_INVITED = "invited";
    public static final String PARTICIPANT_JOINED = "joined";
    public static final String PARTICIPANT_WITHDREW = "withdrew";
    public static final String PARTICIPANT_REJECTED = "rejected";

    public static final String TYPE_PUBLIC = "public";
    public static final String TYPE_INVITED = "invited";

    private final ProcBiddingMapper biddingMapper;
    private final ProcBiddingParticipantMapper participantMapper;
    private final ProcSupplierMapper supplierMapper;

    public UserContext requireCtx() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getTenantId() == null) {
            throw new ServiceException(401, "Missing user/tenant context");
        }
        return ctx;
    }

    public IPage<ProcBidding> page(int pageNum, int pageSize, String status) {
        requireCtx();
        var w = new LambdaQueryWrapper<ProcBidding>().orderByDesc(ProcBidding::getId);
        if (status != null && !status.isBlank()) w.eq(ProcBidding::getStatus, status);
        return biddingMapper.selectPage(Page.of(pageNum, pageSize), w);
    }

    public ProcBidding getById(Long id) {
        UserContext ctx = requireCtx();
        ProcBidding b = biddingMapper.selectById(id);
        if (b == null) throw new ServiceException(404, "Bidding not found: " + id);
        if (!b.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Bidding not found: " + id);
        }
        return b;
    }

    @Transactional
    public ProcBidding create(ProcBidding req) {
        UserContext ctx = requireCtx();
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new ServiceException(400, "code is required");
        }
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new ServiceException(400, "title is required");
        }
        if (req.getStartAt() == null || req.getEndAt() == null) {
            throw new ServiceException(400, "startAt/endAt required");
        }
        if (!req.getEndAt().isAfter(req.getStartAt())) {
            throw new ServiceException(400, "endAt must be after startAt");
        }
        if (biddingMapper.findByCode(ctx.getTenantId(), req.getCode()) != null) {
            throw new ServiceException(409, "Bidding code already exists: " + req.getCode());
        }
        ProcBidding b = new ProcBidding();
        b.setTenantId(ctx.getTenantId());
        b.setCode(req.getCode());
        b.setTitle(req.getTitle());
        b.setType(req.getType() == null ? TYPE_PUBLIC : req.getType());
        b.setStartAt(req.getStartAt());
        b.setEndAt(req.getEndAt());
        b.setStatus(STATUS_DRAFT);
        try {
            biddingMapper.insert(b);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "Bidding code conflict", ex);
        }
        log.info("Bidding created id={} code={}", b.getId(), b.getCode());
        return b;
    }

    /**
     * 发布: draft → published。
     */
    @Transactional
    public ProcBidding publish(Long id) {
        ProcBidding b = getById(id);
        if (!STATUS_DRAFT.equals(b.getStatus())) {
            throw new ServiceException(409, "Only draft bidding can be published (current=" + b.getStatus() + ")");
        }
        b.setStatus(STATUS_PUBLISHED);
        biddingMapper.updateById(b);
        log.info("Bidding published id={}", id);
        return b;
    }

    /**
     * 邀请供应商: 为每个 supplierId 创建 invited 参与方。
     * 仅 draft / published 可邀请。黑名单不可参与。
     */
    @Transactional
    public List<ProcBiddingParticipant> inviteSuppliers(Long biddingId, List<Long> supplierIds) {
        ProcBidding b = getById(biddingId);
        if (!STATUS_DRAFT.equals(b.getStatus()) && !STATUS_PUBLISHED.equals(b.getStatus())) {
            throw new ServiceException(409, "Cannot invite suppliers in status=" + b.getStatus());
        }
        if (supplierIds == null || supplierIds.isEmpty()) {
            throw new ServiceException(400, "supplierIds must not be empty");
        }
        UserContext ctx = requireCtx();
        List<ProcBiddingParticipant> out = new ArrayList<>();
        for (Long sid : supplierIds) {
            ProcSupplier s = supplierMapper.selectById(sid);
            if (s == null || !s.getTenantId().equals(ctx.getTenantId())) {
                throw new ServiceException(400, "Invalid supplierId: " + sid);
            }
            if (ProcSupplierService.STATUS_BLACKLIST.equals(s.getStatus())) {
                throw new ServiceException(409, "Supplier is blacklisted: " + sid);
            }
            // 同一 bidding + supplier 不能重复邀请
            List<ProcBiddingParticipant> existing = participantMapper.findByBidding(ctx.getTenantId(), biddingId);
            for (ProcBiddingParticipant p : existing) {
                if (p.getSupplierId().equals(sid)) {
                    throw new ServiceException(409, "Supplier already invited: " + sid);
                }
            }
            ProcBiddingParticipant p = new ProcBiddingParticipant();
            p.setTenantId(ctx.getTenantId());
            p.setBiddingId(biddingId);
            p.setSupplierId(sid);
            p.setStatus(PARTICIPANT_INVITED);
            try {
                participantMapper.insert(p);
            } catch (DuplicateKeyException ex) {
                throw new ServiceException(409, "Duplicate participant", ex);
            }
            out.add(p);
        }
        log.info("Bidding {} invited {} suppliers", biddingId, supplierIds.size());
        return out;
    }

    /**
     * 供应商参与投标 (提交 bidAmount) — invited → joined。
     * P4 阶段简化: 任何 invited 参与方都可以通过此方法投标。
     */
    @Transactional
    public ProcBiddingParticipant joinBidding(Long biddingId, Long supplierId, BigDecimal bidAmount) {
        UserContext ctx = requireCtx();
        ProcBidding b = getById(biddingId);
        if (!STATUS_PUBLISHED.equals(b.getStatus()) && !STATUS_EVALUATING.equals(b.getStatus())) {
            throw new ServiceException(409, "Bidding not accepting bids (current=" + b.getStatus() + ")");
        }
        if (bidAmount == null || bidAmount.signum() <= 0) {
            throw new ServiceException(400, "bidAmount must be positive");
        }
        List<ProcBiddingParticipant> parts = participantMapper.findByBidding(ctx.getTenantId(), biddingId);
        for (ProcBiddingParticipant p : parts) {
            if (p.getSupplierId().equals(supplierId)) {
                if (!PARTICIPANT_INVITED.equals(p.getStatus())) {
                    throw new ServiceException(409, "Participant not in invited state (current=" + p.getStatus() + ")");
                }
                p.setBidAmount(bidAmount);
                p.setStatus(PARTICIPANT_JOINED);
                participantMapper.updateById(p);
                log.info("Bidding {} supplier {} joined with bid={}", biddingId, supplierId, bidAmount);
                return p;
            }
        }
        throw new ServiceException(404, "Supplier not invited to bidding");
    }

    /**
     * 综合评估: 价格 (60%) + 评级 (40%)。
     * 公式:
     *   priceScore = (maxAmount - amount) / (maxAmount - minAmount)
     *   ratingScore = rating / 5
     *   compositeScore = 0.6 * priceScore + 0.4 * ratingScore
     * 仅 status=joined 参与评分。
     */
    public List<BiddingEvaluationDto> evaluate(Long biddingId) {
        UserContext ctx = requireCtx();
        ProcBidding b = getById(biddingId);
        if (!STATUS_PUBLISHED.equals(b.getStatus())) {
            throw new ServiceException(409, "Only published bidding can be evaluated (current=" + b.getStatus() + ")");
        }
        List<ProcBiddingParticipant> parts = participantMapper.findByBidding(ctx.getTenantId(), biddingId);
        List<ProcBiddingParticipant> joined = new ArrayList<>();
        for (ProcBiddingParticipant p : parts) {
            if (PARTICIPANT_JOINED.equals(p.getStatus())) joined.add(p);
        }
        if (joined.isEmpty()) return List.of();

        BigDecimal minAmt = joined.stream().map(ProcBiddingParticipant::getBidAmount)
            .filter(java.util.Objects::nonNull)
            .min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal maxAmt = joined.stream().map(ProcBiddingParticipant::getBidAmount)
            .filter(java.util.Objects::nonNull)
            .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal range = maxAmt.subtract(minAmt);

        List<BiddingEvaluationDto> out = new ArrayList<>();
        for (ProcBiddingParticipant p : joined) {
            ProcSupplier s = supplierMapper.selectById(p.getSupplierId());
            BigDecimal rating = s == null || s.getRating() == null ? BigDecimal.ZERO : s.getRating();
            double priceScore;
            if (range.signum() == 0 || p.getBidAmount() == null) {
                priceScore = p.getBidAmount() == null ? 0.0 : 1.0;
            } else {
                priceScore = maxAmt.subtract(p.getBidAmount())
                    .divide(range, 8, RoundingMode.HALF_UP).doubleValue();
            }
            double ratingScore = rating.divide(new BigDecimal("5"), 8, RoundingMode.HALF_UP).doubleValue();
            double composite = 0.6 * priceScore + 0.4 * ratingScore;
            BigDecimal overRate = minAmt.signum() == 0 || p.getBidAmount() == null
                ? BigDecimal.ZERO
                : p.getBidAmount().subtract(minAmt).divide(minAmt, 4, RoundingMode.HALF_UP);
            out.add(new BiddingEvaluationDto(
                p.getId(), p.getSupplierId(),
                s == null ? "?" : s.getName(),
                p.getBidAmount(), rating, composite, overRate, p.getStatus()));
        }
        out.sort((x, y) -> Double.compare(y.getCompositeScore(), x.getCompositeScore()));
        // 标记 evaluating
        if (!STATUS_EVALUATING.equals(b.getStatus())) {
            b.setStatus(STATUS_EVALUATING);
            biddingMapper.updateById(b);
        }
        return out;
    }

    /**
     * 定标: 仅 status=joined 的参与方可选中; bidding → awarded。
     */
    @Transactional
    public ProcBidding award(Long biddingId, Long selectedParticipantId) {
        UserContext ctx = requireCtx();
        ProcBidding b = getById(biddingId);
        if (!STATUS_PUBLISHED.equals(b.getStatus()) && !STATUS_EVALUATING.equals(b.getStatus())) {
            throw new ServiceException(409, "Bidding not in awardable state (current=" + b.getStatus() + ")");
        }
        if (selectedParticipantId == null) {
            throw new ServiceException(400, "selectedParticipantId is required");
        }
        ProcBiddingParticipant selected = participantMapper.selectById(selectedParticipantId);
        if (selected == null || !selected.getTenantId().equals(ctx.getTenantId())) {
            throw new ServiceException(404, "Participant not found");
        }
        if (!selected.getBiddingId().equals(biddingId)) {
            throw new ServiceException(409, "Participant does not belong to this bidding");
        }
        if (!PARTICIPANT_JOINED.equals(selected.getStatus())) {
            throw new ServiceException(409, "Only joined participant can be awarded (current=" + selected.getStatus() + ")");
        }
        // 其余 joined → rejected
        List<ProcBiddingParticipant> others = participantMapper.findByBidding(ctx.getTenantId(), biddingId);
        for (ProcBiddingParticipant o : others) {
            if (!o.getId().equals(selected.getId()) && PARTICIPANT_JOINED.equals(o.getStatus())) {
                o.setStatus(PARTICIPANT_REJECTED);
                participantMapper.updateById(o);
            }
        }
        b.setStatus(STATUS_AWARDED);
        biddingMapper.updateById(b);
        log.info("Bidding awarded id={} participant={}", biddingId, selected.getId());
        return b;
    }
}