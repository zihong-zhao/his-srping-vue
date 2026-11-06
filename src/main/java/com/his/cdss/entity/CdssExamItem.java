package com.his.cdss.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** E4-03 检验检查知识库：适应症、标本要求、参考范围、危急值阈值。 */
@Data
@TableName("cdss_exam_item")
public class CdssExamItem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String itemCode;

    private String itemName;

    /** LAB 检验 / EXAM 检查。 */
    private String itemType;

    private String category;

    /** 标本类型：全血、血清、尿液或检查部位。 */
    private String specimen;

    /** 标本采集/检查前要求，例如空腹、避免剧烈运动。 */
    private String precaution;

    /** 适应症诊断 ICD-10 编码数组；为空表示无适应症限制。 */
    private String indicationsJson;

    private String referenceRange;

    private String unit;

    /** 危急值下限，低于该值触发 LOW。 */
    private BigDecimal criticalLow;

    /** 危急值上限，高于该值触发 HIGH。 */
    private BigDecimal criticalHigh;

    private Integer turnaroundMinutes;

    private Boolean enabled;

    private LocalDateTime createdAt;
}
