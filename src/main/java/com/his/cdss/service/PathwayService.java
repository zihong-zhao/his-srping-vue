package com.his.cdss.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.his.cdss.dto.PathwayDtos;
import com.his.cdss.entity.ClinicalPathway;
import com.his.cdss.entity.PathwayInstance;
import com.his.cdss.entity.PathwayNode;
import com.his.cdss.entity.PathwayNodeRecord;
import com.his.cdss.entity.PathwayVariance;
import com.his.cdss.mapper.ClinicalPathwayMapper;
import com.his.cdss.mapper.PathwayInstanceMapper;
import com.his.cdss.mapper.PathwayNodeMapper;
import com.his.cdss.mapper.PathwayNodeRecordMapper;
import com.his.cdss.mapper.PathwayVarianceMapper;
import com.his.common.BizException;
import com.his.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** E4-05 临床路径：入径、按序执行节点、变异、退出与执行率统计。 */
@Service
public class PathwayService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_EXITED = "EXITED";

    private static final Set<String> VARIANCE_TYPES = Set.of("DELAY", "OMIT", "REPLACE", "OTHER");
    private static final Set<String> STATUSES = Set.of(STATUS_ACTIVE, STATUS_COMPLETED, STATUS_EXITED);

    private final ClinicalPathwayMapper pathwayMapper;
    private final PathwayNodeMapper nodeMapper;
    private final PathwayInstanceMapper instanceMapper;
    private final PathwayNodeRecordMapper recordMapper;
    private final PathwayVarianceMapper varianceMapper;

    public PathwayService(ClinicalPathwayMapper pathwayMapper,
                          PathwayNodeMapper nodeMapper,
                          PathwayInstanceMapper instanceMapper,
                          PathwayNodeRecordMapper recordMapper,
                          PathwayVarianceMapper varianceMapper) {
        this.pathwayMapper = pathwayMapper;
        this.nodeMapper = nodeMapper;
        this.instanceMapper = instanceMapper;
        this.recordMapper = recordMapper;
        this.varianceMapper = varianceMapper;
    }

    public List<PathwayDtos.PathwayView> listPathways() {
        return pathwayMapper.selectList(Wrappers.<ClinicalPathway>lambdaQuery()
                        .eq(ClinicalPathway::getEnabled, true)
                        .orderByAsc(ClinicalPathway::getPathwayCode))
                .stream().map(this::toPathwayView).toList();
    }

    public PathwayDtos.PathwayView getPathway(Long id) {
        return toPathwayView(requirePathway(id));
    }

    /** 患者入径；同一患者同一路径同时只允许一个进行中实例。 */
    @Transactional
    public PathwayDtos.InstanceView enroll(Long pathwayId, PathwayDtos.EnrollRequest request,
                                           UserPrincipal user) {
        ClinicalPathway pathway = requirePathway(pathwayId);
        if (!Boolean.TRUE.equals(pathway.getEnabled())) {
            throw new BizException("PATHWAY_DISABLED", "该路径已停用", HttpStatus.CONFLICT);
        }
        Long active = instanceMapper.selectCount(Wrappers.<PathwayInstance>lambdaQuery()
                .eq(PathwayInstance::getPatientId, request.patientId())
                .eq(PathwayInstance::getPathwayId, pathwayId)
                .eq(PathwayInstance::getStatus, STATUS_ACTIVE)
                .eq(request.visitId() != null, PathwayInstance::getVisitId, request.visitId())
                .isNull(request.visitId() == null, PathwayInstance::getVisitId));
        if (active != null && active > 0) {
            throw new BizException("PATHWAY_ALREADY_ACTIVE", "该患者已在此路径中，请勿重复入径",
                    HttpStatus.CONFLICT);
        }
        List<PathwayNode> nodes = loadNodes(pathwayId);
        if (nodes.isEmpty()) {
            throw new BizException("PATHWAY_EMPTY", "路径未配置节点，无法入径");
        }
        LocalDateTime now = LocalDateTime.now();
        PathwayInstance instance = new PathwayInstance();
        instance.setPathwayId(pathwayId);
        instance.setPatientId(request.patientId());
        instance.setVisitId(request.visitId());
        instance.setStatus(STATUS_ACTIVE);
        instance.setCurrentSequence(nodes.get(0).getSequence());
        instance.setStartedBy(user.id());
        instance.setStartedByName(user.displayName());
        instance.setStartedAt(now);
        instance.setCreatedAt(now);
        instance.setUpdatedAt(now);
        instance.setVersion(1);
        instanceMapper.insert(instance);
        return toInstanceView(instance, pathway, nodes);
    }

    public PathwayDtos.InstanceView getInstance(Long id) {
        PathwayInstance instance = requireInstance(id);
        ClinicalPathway pathway = requirePathway(instance.getPathwayId());
        return toInstanceView(instance, pathway, loadNodes(instance.getPathwayId()));
    }

    public List<PathwayDtos.InstanceView> listInstances(Long patientId, Long pathwayId,
                                                        String status, int limit) {
        if (patientId == null && pathwayId == null && (status == null || status.isBlank())) {
            throw new BizException("QUERY_REQUIRED", "请至少按患者、路径或状态查询入径实例");
        }
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
            throw new BizException("INVALID_STATUS", "不支持的入径状态：" + status);
        }
        int size = Math.max(1, Math.min(limit, 200));
        List<PathwayInstance> instances = instanceMapper.selectList(
                Wrappers.<PathwayInstance>lambdaQuery()
                        .eq(patientId != null, PathwayInstance::getPatientId, patientId)
                        .eq(pathwayId != null, PathwayInstance::getPathwayId, pathwayId)
                        .eq(status != null && !status.isBlank(), PathwayInstance::getStatus, status)
                        .orderByDesc(PathwayInstance::getStartedAt)
                        .last("limit " + size));
        Map<Long, ClinicalPathway> pathways = loadPathwayMap();
        Map<Long, List<PathwayNode>> nodeCache = new HashMap<>();
        List<PathwayDtos.InstanceView> views = new ArrayList<>();
        for (PathwayInstance instance : instances) {
            ClinicalPathway pathway = pathways.get(instance.getPathwayId());
            if (pathway == null) {
                continue;
            }
            List<PathwayNode> nodes = nodeCache.computeIfAbsent(instance.getPathwayId(), this::loadNodes);
            views.add(toInstanceView(instance, pathway, nodes));
        }
        return views;
    }

    /** 严格按节点顺序执行，跳过节点返回 409。 */
    @Transactional
    public PathwayDtos.InstanceView completeNode(Long instanceId, Long nodeId, String note,
                                                 UserPrincipal user) {
        PathwayInstance instance = requireInstance(instanceId);
        requireActive(instance);
        ClinicalPathway pathway = requirePathway(instance.getPathwayId());
        List<PathwayNode> nodes = loadNodes(instance.getPathwayId());
        PathwayNode target = nodes.stream()
                .filter(node -> node.getId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new BizException("NODE_NOT_FOUND", "节点不属于该路径", HttpStatus.NOT_FOUND));
        PathwayNode current = nodes.stream()
                .filter(node -> node.getSequence().equals(instance.getCurrentSequence()))
                .findFirst()
                .orElseThrow(() -> new BizException("PATHWAY_STATE_INVALID", "当前节点状态异常"));
        if (!target.getSequence().equals(current.getSequence())) {
            throw new BizException("INVALID_NODE",
                    "请先完成当前节点：" + current.getNodeName() + "（第 " + current.getSequence() + " 步）",
                    HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        PathwayNodeRecord record = new PathwayNodeRecord();
        record.setInstanceId(instance.getId());
        record.setNodeId(target.getId());
        record.setNodeCode(target.getNodeCode());
        record.setNodeName(target.getNodeName());
        record.setSequence(target.getSequence());
        record.setCompletedBy(user.id());
        record.setCompletedByName(user.displayName());
        record.setCompletedAt(now);
        record.setNote(note);
        recordMapper.insert(record);

        int index = nodes.indexOf(target);
        if (index == nodes.size() - 1) {
            instance.setStatus(STATUS_COMPLETED);
            instance.setEndedAt(now);
        } else {
            instance.setCurrentSequence(nodes.get(index + 1).getSequence());
        }
        instance.setUpdatedAt(now);
        if (instanceMapper.updateById(instance) == 0) {
            throw new BizException("CONFLICT", "路径实例已被他人更新，请刷新后重试", HttpStatus.CONFLICT);
        }
        return toInstanceView(instance, pathway, nodes);
    }

    @Transactional
    public PathwayDtos.InstanceView addVariance(Long instanceId, PathwayDtos.VarianceRequest request,
                                                UserPrincipal user) {
        PathwayInstance instance = requireInstance(instanceId);
        requireActive(instance);
        if (!VARIANCE_TYPES.contains(request.varianceType())) {
            throw new BizException("INVALID_VARIANCE_TYPE", "变异类型仅支持 DELAY/OMIT/REPLACE/OTHER");
        }
        Long nodeId = request.nodeId();
        String nodeCode = null;
        if (nodeId != null) {
            PathwayNode node = loadNodes(instance.getPathwayId()).stream()
                    .filter(item -> item.getId().equals(nodeId))
                    .findFirst()
                    .orElseThrow(() -> new BizException("NODE_NOT_FOUND", "节点不属于该路径",
                            HttpStatus.NOT_FOUND));
            nodeCode = node.getNodeCode();
        }
        PathwayVariance variance = new PathwayVariance();
        variance.setInstanceId(instanceId);
        variance.setNodeId(nodeId);
        variance.setNodeCode(nodeCode);
        variance.setVarianceType(request.varianceType());
        variance.setReason(request.reason());
        variance.setRecordedBy(user.id());
        variance.setRecordedByName(user.displayName());
        variance.setRecordedAt(LocalDateTime.now());
        varianceMapper.insert(variance);

        ClinicalPathway pathway = requirePathway(instance.getPathwayId());
        return toInstanceView(instance, pathway, loadNodes(instance.getPathwayId()));
    }

    @Transactional
    public PathwayDtos.InstanceView exit(Long instanceId, String reason, UserPrincipal user) {
        PathwayInstance instance = requireInstance(instanceId);
        requireActive(instance);
        LocalDateTime now = LocalDateTime.now();
        instance.setStatus(STATUS_EXITED);
        instance.setEndedAt(now);
        instance.setExitReason(reason);
        instance.setUpdatedAt(now);
        if (instanceMapper.updateById(instance) == 0) {
            throw new BizException("CONFLICT", "路径实例已被他人更新，请刷新后重试", HttpStatus.CONFLICT);
        }
        ClinicalPathway pathway = requirePathway(instance.getPathwayId());
        return toInstanceView(instance, pathway, loadNodes(instance.getPathwayId()));
    }

    /** 路径执行率 = 完成数 / 同期入径总数 × 100%。 */
    public List<PathwayDtos.PathwayStats> stats(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().minusDays(29) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        if (start.isAfter(end)) {
            throw new BizException("INVALID_RANGE", "开始日期不能晚于结束日期");
        }
        List<PathwayInstance> instances = instanceMapper.selectList(
                Wrappers.<PathwayInstance>lambdaQuery()
                        .ge(PathwayInstance::getStartedAt, start.atStartOfDay())
                        .lt(PathwayInstance::getStartedAt, end.plusDays(1).atStartOfDay()));

        Map<Long, long[]> counts = new HashMap<>();
        for (PathwayInstance instance : instances) {
            long[] row = counts.computeIfAbsent(instance.getPathwayId(), key -> new long[3]);
            row[0]++;
            if (STATUS_COMPLETED.equals(instance.getStatus())) {
                row[1]++;
            }
            if (STATUS_EXITED.equals(instance.getStatus())) {
                row[2]++;
            }
        }

        List<PathwayDtos.PathwayStats> result = new ArrayList<>();
        for (ClinicalPathway pathway : pathwayMapper.selectList(
                Wrappers.<ClinicalPathway>lambdaQuery()
                        .eq(ClinicalPathway::getEnabled, true)
                        .orderByAsc(ClinicalPathway::getPathwayCode))) {
            long[] row = counts.getOrDefault(pathway.getId(), new long[3]);
            long total = row[0];
            long completed = row[1];
            result.add(new PathwayDtos.PathwayStats(
                    pathway.getId(),
                    pathway.getPathwayCode(),
                    pathway.getName(),
                    total,
                    completed,
                    row[2],
                    rate(completed, total)));
        }
        return result;
    }

    private BigDecimal rate(long completed, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(completed).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private void requireActive(PathwayInstance instance) {
        if (!STATUS_ACTIVE.equals(instance.getStatus())) {
            throw new BizException("PATHWAY_NOT_ACTIVE", "路径实例当前状态为 "
                    + instance.getStatus() + "，不能继续执行", HttpStatus.CONFLICT);
        }
    }

    private ClinicalPathway requirePathway(Long id) {
        ClinicalPathway pathway = pathwayMapper.selectById(id);
        if (pathway == null) {
            throw new BizException("PATHWAY_NOT_FOUND", "临床路径不存在", HttpStatus.NOT_FOUND);
        }
        return pathway;
    }

    private PathwayInstance requireInstance(Long id) {
        PathwayInstance instance = instanceMapper.selectById(id);
        if (instance == null) {
            throw new BizException("PATHWAY_INSTANCE_NOT_FOUND", "入径实例不存在", HttpStatus.NOT_FOUND);
        }
        return instance;
    }

    private List<PathwayNode> loadNodes(Long pathwayId) {
        return nodeMapper.selectList(Wrappers.<PathwayNode>lambdaQuery()
                .eq(PathwayNode::getPathwayId, pathwayId)
                .orderByAsc(PathwayNode::getSequence));
    }

    private Map<Long, ClinicalPathway> loadPathwayMap() {
        Map<Long, ClinicalPathway> map = new HashMap<>();
        for (ClinicalPathway pathway : pathwayMapper.selectList(Wrappers.<ClinicalPathway>lambdaQuery())) {
            map.put(pathway.getId(), pathway);
        }
        return map;
    }

    private PathwayDtos.PathwayView toPathwayView(ClinicalPathway pathway) {
        return new PathwayDtos.PathwayView(
                pathway.getId(),
                pathway.getPathwayCode(),
                pathway.getName(),
                pathway.getDiagnosisCode(),
                pathway.getDepartment(),
                pathway.getVersion(),
                pathway.getStandardDays(),
                pathway.getDescription(),
                loadNodes(pathway.getId()).stream().map(this::toNodeView).toList());
    }

    private PathwayDtos.PathwayNodeView toNodeView(PathwayNode node) {
        return new PathwayDtos.PathwayNodeView(
                node.getId(),
                node.getSequence(),
                node.getNodeCode(),
                node.getNodeName(),
                node.getDayOffset(),
                node.getRequired(),
                node.getDescription());
    }

    private PathwayDtos.InstanceView toInstanceView(PathwayInstance instance,
                                                    ClinicalPathway pathway,
                                                    List<PathwayNode> nodes) {
        List<PathwayNodeRecord> records = recordMapper.selectList(
                Wrappers.<PathwayNodeRecord>lambdaQuery()
                        .eq(PathwayNodeRecord::getInstanceId, instance.getId())
                        .orderByAsc(PathwayNodeRecord::getSequence));
        List<PathwayVariance> variances = varianceMapper.selectList(
                Wrappers.<PathwayVariance>lambdaQuery()
                        .eq(PathwayVariance::getInstanceId, instance.getId())
                        .orderByDesc(PathwayVariance::getRecordedAt));

        PathwayNode currentNode = STATUS_ACTIVE.equals(instance.getStatus())
                ? nodes.stream()
                        .filter(node -> node.getSequence().equals(instance.getCurrentSequence()))
                        .findFirst().orElse(null)
                : null;

        return new PathwayDtos.InstanceView(
                instance.getId(),
                pathway.getId(),
                pathway.getPathwayCode(),
                pathway.getName(),
                instance.getPatientId(),
                instance.getVisitId(),
                instance.getStatus(),
                instance.getCurrentSequence(),
                currentNode == null ? null : currentNode.getNodeCode(),
                currentNode == null ? null : currentNode.getNodeName(),
                nodes.size(),
                records.size(),
                instance.getStartedByName(),
                instance.getStartedAt(),
                instance.getEndedAt(),
                instance.getExitReason(),
                records.stream().map(this::toNodeRecordView).toList(),
                variances.stream().map(this::toVarianceView).toList());
    }

    private PathwayDtos.NodeRecordView toNodeRecordView(PathwayNodeRecord record) {
        return new PathwayDtos.NodeRecordView(
                record.getNodeCode(),
                record.getNodeName(),
                record.getSequence(),
                record.getCompletedByName(),
                record.getCompletedAt(),
                record.getNote());
    }

    private PathwayDtos.VarianceView toVarianceView(PathwayVariance variance) {
        return new PathwayDtos.VarianceView(
                variance.getId(),
                variance.getNodeCode(),
                variance.getVarianceType(),
                variance.getReason(),
                variance.getRecordedByName(),
                variance.getRecordedAt());
    }
}
