package com.his.loop.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.his.common.BizException;
import com.his.loop.dto.LoopDtos;
import com.his.loop.entity.LoopInstance;
import com.his.loop.entity.LoopNodeEvent;
import com.his.loop.mapper.LoopInstanceMapper;
import com.his.loop.mapper.LoopNodeEventMapper;
import com.his.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * E5 闭环管理：医嘱、处方、检验、检查四条闭环。
 * 节点严格按序推进，末节点完成即视为闭环，闭环率按期统计。
 */
@Service
public class LoopService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CLOSED = "CLOSED";

    private record NodeDef(String code, String name) {
    }

    private static final Map<String, List<NodeDef>> CHAINS = new LinkedHashMap<>();

    static {
        CHAINS.put("MED_ORDER", List.of(
                new NodeDef("ORDERED", "开立"),
                new NodeDef("REVIEWED", "审核"),
                new NodeDef("EXECUTED", "执行"),
                new NodeDef("CHARGED", "计费")));
        CHAINS.put("PRESCRIPTION", List.of(
                new NodeDef("ORDERED", "开立"),
                new NodeDef("REVIEWED", "审方"),
                new NodeDef("CHARGED", "收费"),
                new NodeDef("DISPENSED", "调配"),
                new NodeDef("DISPENSED_OUT", "发药")));
        CHAINS.put("LAB", List.of(
                new NodeDef("ORDERED", "申请"),
                new NodeDef("CHARGED", "收费"),
                new NodeDef("SAMPLED", "采样"),
                new NodeDef("RECEIVED", "接收"),
                new NodeDef("TESTED", "上机"),
                new NodeDef("VERIFIED", "审核"),
                new NodeDef("REPORTED", "报告"),
                new NodeDef("VIEWED", "医生调阅")));
        CHAINS.put("EXAM", List.of(
                new NodeDef("ORDERED", "申请"),
                new NodeDef("CHARGED", "收费"),
                new NodeDef("SCHEDULED", "预约"),
                new NodeDef("PERFORMED", "检查"),
                new NodeDef("REPORTED", "报告"),
                new NodeDef("VIEWED", "医生调阅")));
    }

    private final LoopInstanceMapper instanceMapper;
    private final LoopNodeEventMapper eventMapper;
    private final ObjectMapper objectMapper;

    public LoopService(LoopInstanceMapper instanceMapper,
                       LoopNodeEventMapper eventMapper,
                       ObjectMapper objectMapper) {
        this.instanceMapper = instanceMapper;
        this.eventMapper = eventMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public LoopDtos.LoopView create(LoopDtos.CreateLoopRequest request, UserPrincipal user) {
        List<NodeDef> chain = CHAINS.get(request.loopType());
        if (chain == null) {
            throw new BizException("UNSUPPORTED_LOOP_TYPE", "不支持的闭环类型：" + request.loopType());
        }
        LocalDateTime now = LocalDateTime.now();
        NodeDef first = chain.get(0);

        LoopInstance instance = new LoopInstance();
        instance.setBizNo("LP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        instance.setLoopType(request.loopType());
        instance.setPatientId(request.patientId());
        instance.setVisitId(request.visitId());
        instance.setTitle(request.title());
        instance.setStatus(STATUS_ACTIVE);
        instance.setCurrentNode(first.code());
        instance.setStartedBy(user.id());
        instance.setStartedByName(user.displayName());
        instance.setStartedAt(now);
        instance.setCreatedAt(now);
        instance.setUpdatedAt(now);
        instance.setVersion(1);
        instanceMapper.insert(instance);

        insertEvent(instance, first, 1, request.detail(), user, now);
        return toView(instance);
    }

    @Transactional
    public LoopDtos.LoopView advance(Long id, LoopDtos.AdvanceRequest request, UserPrincipal user) {
        LoopInstance instance = requireInstance(id);
        if (STATUS_CLOSED.equals(instance.getStatus())) {
            throw new BizException("LOOP_CLOSED", "该单据已完成闭环", HttpStatus.CONFLICT);
        }
        List<NodeDef> chain = CHAINS.get(instance.getLoopType());
        int currentIndex = indexOf(chain, instance.getCurrentNode());
        if (currentIndex < 0 || currentIndex + 1 >= chain.size()) {
            throw new BizException("LOOP_BROKEN", "闭环节点定义异常", HttpStatus.CONFLICT);
        }
        NodeDef next = chain.get(currentIndex + 1);
        if (!next.code().equals(request.nodeCode())) {
            throw new BizException("INVALID_NODE",
                    "下一节点应为：" + next.name() + "(" + next.code() + ")，当前节点："
                            + chain.get(currentIndex).name(), HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        insertEvent(instance, next, currentIndex + 2, request.payload(), user, now);
        instance.setCurrentNode(next.code());
        instance.setUpdatedAt(now);
        if (currentIndex + 1 == chain.size() - 1) {
            instance.setStatus(STATUS_CLOSED);
            instance.setClosedAt(now);
        }
        if (instanceMapper.updateById(instance) == 0) {
            throw new BizException("CONFLICT", "单据已被他人推进，请刷新后重试", HttpStatus.CONFLICT);
        }
        return toView(instance);
    }

    public LoopDtos.LoopView get(Long id) {
        return toView(requireInstance(id));
    }

    public List<LoopDtos.LoopSummary> list(Long patientId, Long visitId,
                                           String loopType, String status, int limit) {
        if (patientId == null && visitId == null) {
            throw new BizException("QUERY_REQUIRED", "请至少按患者或就诊 ID 查询");
        }
        if (loopType != null && !loopType.isBlank() && !CHAINS.containsKey(loopType)) {
            throw new BizException("UNSUPPORTED_LOOP_TYPE", "不支持的闭环类型：" + loopType);
        }
        int size = Math.max(1, Math.min(limit, 200));
        return instanceMapper.selectList(Wrappers.<LoopInstance>lambdaQuery()
                        .eq(patientId != null, LoopInstance::getPatientId, patientId)
                        .eq(visitId != null, LoopInstance::getVisitId, visitId)
                        .eq(loopType != null && !loopType.isBlank(), LoopInstance::getLoopType, loopType)
                        .eq(status != null && !status.isBlank(), LoopInstance::getStatus, status)
                        .orderByDesc(LoopInstance::getStartedAt)
                        .last("limit " + size))
                .stream().map(this::toSummary).toList();
    }

    /** 闭环率 = 节点链完整且按序完成的单据数 / 同期应闭环单据总数 × 100%。 */
    public List<LoopDtos.LoopStats> stats(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        List<LoopInstance> instances = instanceMapper.selectList(Wrappers.<LoopInstance>lambdaQuery()
                .ge(LoopInstance::getStartedAt, start.atStartOfDay())
                .lt(LoopInstance::getStartedAt, end.plusDays(1).atStartOfDay()));

        Map<String, long[]> aggregate = new LinkedHashMap<>();
        CHAINS.keySet().forEach(type -> aggregate.put(type, new long[]{0, 0}));
        for (LoopInstance instance : instances) {
            long[] counts = aggregate.computeIfAbsent(instance.getLoopType(), key -> new long[]{0, 0});
            counts[0]++;
            if (STATUS_CLOSED.equals(instance.getStatus())) {
                counts[1]++;
            }
        }
        List<LoopDtos.LoopStats> result = new ArrayList<>();
        aggregate.forEach((type, counts) -> result.add(new LoopDtos.LoopStats(
                type, counts[0], counts[1], rate(counts[1], counts[0]))));
        return result;
    }

    private BigDecimal rate(long closed, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(closed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private void insertEvent(LoopInstance instance, NodeDef node, int sequence,
                             JsonNode payload, UserPrincipal user, LocalDateTime occurredAt) {
        LoopNodeEvent event = new LoopNodeEvent();
        event.setLoopId(instance.getId());
        event.setNodeCode(node.code());
        event.setNodeName(node.name());
        event.setSequence(sequence);
        event.setOperatorId(user.id());
        event.setOperatorName(user.displayName());
        event.setOccurredAt(occurredAt);
        event.setPayloadJson(payload == null ? null : payload.toString());
        eventMapper.insert(event);
    }

    private int indexOf(List<NodeDef> chain, String code) {
        for (int i = 0; i < chain.size(); i++) {
            if (chain.get(i).code().equals(code)) {
                return i;
            }
        }
        return -1;
    }

    private LoopInstance requireInstance(Long id) {
        LoopInstance instance = instanceMapper.selectById(id);
        if (instance == null) {
            throw new BizException("LOOP_NOT_FOUND", "闭环单据不存在", HttpStatus.NOT_FOUND);
        }
        return instance;
    }

    private List<LoopNodeEvent> events(Long loopId) {
        return eventMapper.selectList(Wrappers.<LoopNodeEvent>lambdaQuery()
                .eq(LoopNodeEvent::getLoopId, loopId)
                .orderByAsc(LoopNodeEvent::getSequence));
    }

    private LoopDtos.LoopView toView(LoopInstance instance) {
        List<NodeDef> chain = CHAINS.getOrDefault(instance.getLoopType(), List.of());
        String currentNodeName = chain.stream()
                .filter(node -> node.code().equals(instance.getCurrentNode()))
                .map(NodeDef::name)
                .findFirst()
                .orElse(instance.getCurrentNode());
        List<LoopDtos.LoopNodeView> nodes = events(instance.getId()).stream()
                .map(event -> new LoopDtos.LoopNodeView(
                        event.getNodeCode(),
                        event.getNodeName(),
                        event.getSequence(),
                        event.getOperatorName(),
                        event.getOccurredAt(),
                        readPayload(event.getPayloadJson())))
                .toList();
        return new LoopDtos.LoopView(
                instance.getId(),
                instance.getBizNo(),
                instance.getLoopType(),
                instance.getTitle(),
                instance.getPatientId(),
                instance.getVisitId(),
                instance.getStatus(),
                instance.getCurrentNode(),
                currentNodeName,
                chain.stream().map(NodeDef::code).toList(),
                instance.getStartedAt(),
                instance.getClosedAt(),
                nodes);
    }

    private LoopDtos.LoopSummary toSummary(LoopInstance instance) {
        return new LoopDtos.LoopSummary(
                instance.getId(),
                instance.getBizNo(),
                instance.getLoopType(),
                instance.getTitle(),
                instance.getPatientId(),
                instance.getVisitId(),
                instance.getStatus(),
                instance.getCurrentNode(),
                instance.getStartedAt(),
                instance.getClosedAt());
    }

    private JsonNode readPayload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new BizException("JSON_PARSE_FAILED", "闭环载荷解析失败");
        }
    }
}
