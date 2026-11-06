package com.his.cdss.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.his.cdss.dto.ExamItemDtos;
import com.his.cdss.entity.CdssExamItem;
import com.his.cdss.mapper.CdssExamItemMapper;
import com.his.common.BizException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** E4-03 检验检查知识库：项目检索、适应症匹配与危急值阈值判定。 */
@Service
public class ExamItemService {

    public static final String SEVERITY_HIGH = "HIGH";
    public static final String SEVERITY_LOW = "LOW";
    public static final String SEVERITY_NORMAL = "NORMAL";

    private static final Set<String> ITEM_TYPES = Set.of("LAB", "EXAM");

    private final CdssExamItemMapper examItemMapper;
    private final ObjectMapper objectMapper;

    public ExamItemService(CdssExamItemMapper examItemMapper, ObjectMapper objectMapper) {
        this.examItemMapper = examItemMapper;
        this.objectMapper = objectMapper;
    }

    public List<ExamItemDtos.ExamItemView> list(String itemType, String keyword, int limit) {
        if (itemType != null && !itemType.isBlank() && !ITEM_TYPES.contains(itemType)) {
            throw new BizException("INVALID_ITEM_TYPE", "项目类型仅支持 LAB 或 EXAM");
        }
        int size = Math.max(1, Math.min(limit, 200));
        var query = Wrappers.<CdssExamItem>lambdaQuery()
                .eq(CdssExamItem::getEnabled, true)
                .eq(itemType != null && !itemType.isBlank(), CdssExamItem::getItemType, itemType)
                .orderByAsc(CdssExamItem::getItemType)
                .orderByAsc(CdssExamItem::getItemCode)
                .last("limit " + size);
        if (keyword != null && !keyword.isBlank()) {
            query.and(w -> w.like(CdssExamItem::getItemCode, keyword)
                    .or().like(CdssExamItem::getItemName, keyword));
        }
        return examItemMapper.selectList(query).stream().map(this::toView).toList();
    }

    public ExamItemDtos.ExamItemView get(String itemCode) {
        return toView(requireItem(itemCode));
    }

    /** 检查项目是否与患者诊断匹配；无适应症配置视为不受限。 */
    public ExamItemDtos.IndicationCheckResult checkIndication(ExamItemDtos.IndicationCheckRequest request) {
        CdssExamItem item = requireItem(request.itemCode());
        List<String> indications = readStringList(item.getIndicationsJson());
        if (indications.isEmpty()) {
            return new ExamItemDtos.IndicationCheckResult(
                    item.getItemCode(), item.getItemName(), item.getItemType(),
                    true, false, List.of(), item.getSpecimen(), item.getPrecaution(),
                    "该检查项目无适应症限制");
        }
        List<String> diagnosisCodes = request.diagnosisCodes() == null ? List.of() : request.diagnosisCodes();
        List<String> matched = new LinkedHashSet<>(indications.stream()
                .filter(diagnosisCodes::contains).toList()).stream().toList();
        boolean indicated = !matched.isEmpty();
        String message = indicated
                ? "诊断匹配适应症：" + String.join("、", matched)
                : "诊断不符合适应症，需人工确认必要性";
        return new ExamItemDtos.IndicationCheckResult(
                item.getItemCode(), item.getItemName(), item.getItemType(),
                indicated, true, matched, item.getSpecimen(), item.getPrecaution(), message);
    }

    /** 按知识库阈值判定结果是否为危急值。 */
    public ExamItemDtos.CriticalCheckResult checkCritical(ExamItemDtos.CriticalCheckRequest request) {
        CdssExamItem item = requireItem(request.itemCode());
        BigDecimal value = request.value();
        if (item.getCriticalLow() == null && item.getCriticalHigh() == null) {
            return new ExamItemDtos.CriticalCheckResult(
                    item.getItemCode(), item.getItemName(), SEVERITY_NORMAL, value, item.getUnit(),
                    item.getReferenceRange(), null, null, "该项目未配置危急值阈值");
        }
        String severity = SEVERITY_NORMAL;
        String message = "结果在参考范围内，未触发危急值";
        if (item.getCriticalHigh() != null && value.compareTo(item.getCriticalHigh()) > 0) {
            severity = SEVERITY_HIGH;
            message = item.getItemName() + " " + plain(value) + unitOf(item)
                    + " 高于危急值上限 " + plain(item.getCriticalHigh()) + unitOf(item) + "，请立即处理";
        } else if (item.getCriticalLow() != null && value.compareTo(item.getCriticalLow()) < 0) {
            severity = SEVERITY_LOW;
            message = item.getItemName() + " " + plain(value) + unitOf(item)
                    + " 低于危急值下限 " + plain(item.getCriticalLow()) + unitOf(item) + "，请立即处理";
        }
        return new ExamItemDtos.CriticalCheckResult(
                item.getItemCode(), item.getItemName(), severity, value, item.getUnit(),
                item.getReferenceRange(), item.getCriticalLow(), item.getCriticalHigh(), message);
    }

    private CdssExamItem requireItem(String itemCode) {
        CdssExamItem item = examItemMapper.selectOne(Wrappers.<CdssExamItem>lambdaQuery()
                .eq(CdssExamItem::getItemCode, itemCode)
                .eq(CdssExamItem::getEnabled, true)
                .last("limit 1"));
        if (item == null) {
            throw new BizException("EXAM_ITEM_NOT_FOUND", "检验检查项目不存在或已停用：" + itemCode,
                    HttpStatus.NOT_FOUND);
        }
        return item;
    }

    private String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String unitOf(CdssExamItem item) {
        return item.getUnit() == null || item.getUnit().isBlank() ? "" : " " + item.getUnit();
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new BizException("JSON_PARSE_FAILED", "检验检查知识库数据解析失败");
        }
    }

    private ExamItemDtos.ExamItemView toView(CdssExamItem item) {
        return new ExamItemDtos.ExamItemView(
                item.getId(),
                item.getItemCode(),
                item.getItemName(),
                item.getItemType(),
                item.getCategory(),
                item.getSpecimen(),
                item.getPrecaution(),
                readStringList(item.getIndicationsJson()),
                item.getReferenceRange(),
                item.getUnit(),
                item.getCriticalLow(),
                item.getCriticalHigh(),
                item.getTurnaroundMinutes());
    }
}
