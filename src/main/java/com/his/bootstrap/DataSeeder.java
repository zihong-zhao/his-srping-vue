package com.his.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.his.cdss.entity.ClinicalPathway;
import com.his.cdss.entity.CdssDrug;
import com.his.cdss.entity.CdssDrugInteraction;
import com.his.cdss.entity.CdssExamItem;
import com.his.cdss.entity.CdssRule;
import com.his.cdss.entity.PathwayNode;
import com.his.cdss.mapper.ClinicalPathwayMapper;
import com.his.cdss.mapper.CdssDrugInteractionMapper;
import com.his.cdss.mapper.CdssDrugMapper;
import com.his.cdss.mapper.CdssExamItemMapper;
import com.his.cdss.mapper.CdssRuleMapper;
import com.his.cdss.mapper.PathwayNodeMapper;
import com.his.emr.entity.EmrIcdCode;
import com.his.emr.entity.EmrQcRule;
import com.his.emr.entity.EmrTemplate;
import com.his.emr.mapper.EmrIcdCodeMapper;
import com.his.emr.mapper.EmrQcRuleMapper;
import com.his.emr.mapper.EmrTemplateMapper;
import com.his.system.SysUser;
import com.his.system.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final SysUserMapper userMapper;
    private final EmrTemplateMapper templateMapper;
    private final EmrQcRuleMapper qcRuleMapper;
    private final EmrIcdCodeMapper icdCodeMapper;
    private final CdssRuleMapper cdssRuleMapper;
    private final CdssDrugMapper cdssDrugMapper;
    private final CdssDrugInteractionMapper cdssInteractionMapper;
    private final CdssExamItemMapper cdssExamItemMapper;
    private final ClinicalPathwayMapper clinicalPathwayMapper;
    private final PathwayNodeMapper pathwayNodeMapper;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(SysUserMapper userMapper,
                      EmrTemplateMapper templateMapper,
                      EmrQcRuleMapper qcRuleMapper,
                      EmrIcdCodeMapper icdCodeMapper,
                      CdssRuleMapper cdssRuleMapper,
                      CdssDrugMapper cdssDrugMapper,
                      CdssDrugInteractionMapper cdssInteractionMapper,
                      CdssExamItemMapper cdssExamItemMapper,
                      ClinicalPathwayMapper clinicalPathwayMapper,
                      PathwayNodeMapper pathwayNodeMapper,
                      PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.templateMapper = templateMapper;
        this.qcRuleMapper = qcRuleMapper;
        this.icdCodeMapper = icdCodeMapper;
        this.cdssRuleMapper = cdssRuleMapper;
        this.cdssDrugMapper = cdssDrugMapper;
        this.cdssInteractionMapper = cdssInteractionMapper;
        this.cdssExamItemMapper = cdssExamItemMapper;
        this.clinicalPathwayMapper = clinicalPathwayMapper;
        this.pathwayNodeMapper = pathwayNodeMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedUsers();
        seedTemplates();
        seedQcRules();
        seedIcdCodes();
        seedCdssRules();
        seedDrugs();
        seedDrugInteractions();
        seedExamItems();
        seedPathways();
    }

    private void seedUsers() {
        if (userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()) > 0) {
            return;
        }
        insertUser("admin", "admin123", "系统管理员", "ADMIN", 1L);
        insertUser("doctor1", "doctor123", "张医生", "DOCTOR", 1001L);
        insertUser("doctor2", "doctor123", "王医生", "DOCTOR", 1003L);
        insertUser("qc1", "qc123", "李质控", "QC", 1002L);
        log.info("seeded demo users: admin/admin123, doctor1/doctor123, doctor2/doctor123, qc1/qc123");
    }

    private void insertUser(String username, String rawPassword, String displayName,
                            String role, Long deptId) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setDisplayName(displayName);
        user.setDeptId(deptId);
        user.setRole(role);
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        userMapper.insert(user);
    }

    private void seedTemplates() {
        if (templateMapper.selectCount(Wrappers.<EmrTemplate>lambdaQuery()) > 0) {
            return;
        }
        insertTemplate("OUTPATIENT_NOTE", "门诊病历", """
                {"sections":[
                  {"code":"chief_complaint","title":"主诉","type":"text","required":true},
                  {"code":"present_illness","title":"现病史","type":"text","required":true},
                  {"code":"physical_exam","title":"体格检查","type":"text","required":true},
                  {"code":"diagnosis","title":"诊断","type":"icd","required":true},
                  {"code":"treatment_plan","title":"处理意见","type":"text","required":true}
                ]}
                """);
        insertTemplate("ADMISSION_RECORD", "入院记录", """
                {"sections":[
                  {"code":"chief_complaint","title":"主诉","type":"text","required":true},
                  {"code":"present_illness","title":"现病史","type":"text","required":true},
                  {"code":"past_history","title":"既往史","type":"text","required":true},
                  {"code":"physical_exam","title":"体格检查","type":"text","required":true},
                  {"code":"diagnosis","title":"初步诊断","type":"icd","required":true},
                  {"code":"treatment_plan","title":"诊疗计划","type":"text","required":true}
                ]}
                """);
        insertTemplate("FIRST_COURSE", "首次病程记录", """
                {"sections":[
                  {"code":"case_summary","title":"病例特点","type":"text","required":true},
                  {"code":"diagnosis_analysis","title":"拟诊讨论","type":"text","required":true},
                  {"code":"diagnostic_plan","title":"诊疗计划","type":"text","required":true}
                ]}
                """);
        insertTemplate("DISCHARGE_RECORD", "出院记录", """
                {"sections":[
                  {"code":"admission_info","title":"入院情况","type":"text","required":true},
                  {"code":"hospital_course","title":"住院经过","type":"text","required":true},
                  {"code":"discharge_diagnosis","title":"出院诊断","type":"icd","required":true},
                  {"code":"discharge_advice","title":"出院医嘱","type":"text","required":true}
                ]}
                """);
        log.info("seeded 4 EMR templates");
    }

    private void insertTemplate(String docType, String name, String schemaJson) {
        EmrTemplate template = new EmrTemplate();
        template.setDocType(docType);
        template.setName(name);
        template.setScope("HOSPITAL");
        template.setDeptId(0L);
        template.setVersion(1);
        template.setSchemaJson(schemaJson.strip());
        template.setEnabled(true);
        template.setCreatedAt(LocalDateTime.now());
        templateMapper.insert(template);
    }

    private void seedQcRules() {
        if (qcRuleMapper.selectCount(Wrappers.<EmrQcRule>lambdaQuery()) > 0) {
            return;
        }
        insertRule("REQUIRED_SECTION", "必填段落完整性", "FORM", "BLOCK", null, null);
        insertRule("TIME_LIMIT_ADMISSION_24H", "入院记录 24 小时时限", "FORM", "WARN",
                "ADMISSION_RECORD", "{\"hours\":24}");
        insertRule("TIME_LIMIT_FIRST_COURSE_8H", "首次病程 8 小时时限", "FORM", "WARN",
                "FIRST_COURSE", "{\"hours\":8}");
        insertRule("TIME_LIMIT_DISCHARGE_24H", "出院记录 24 小时时限", "FORM", "WARN",
                "DISCHARGE_RECORD", "{\"hours\":24}");
        log.info("seeded 4 QC rules");
    }

    private void insertRule(String code, String name, String category, String severity,
                            String docType, String paramsJson) {
        EmrQcRule rule = new EmrQcRule();
        rule.setRuleCode(code);
        rule.setName(name);
        rule.setCategory(category);
        rule.setSeverity(severity);
        rule.setDocType(docType);
        rule.setParamsJson(paramsJson);
        rule.setEnabled(true);
        rule.setCreatedAt(LocalDateTime.now());
        qcRuleMapper.insert(rule);
    }

    private void seedIcdCodes() {
        if (icdCodeMapper.selectCount(Wrappers.<EmrIcdCode>lambdaQuery()) > 0) {
            return;
        }
        insertIcd("J18.9", "肺炎", "ICD10");
        insertIcd("J18.0", "支气管肺炎", "ICD10");
        insertIcd("J45.9", "支气管哮喘", "ICD10");
        insertIcd("J44.9", "慢性阻塞性肺疾病", "ICD10");
        insertIcd("I10", "原发性高血压", "ICD10");
        insertIcd("I63.9", "脑梗死", "ICD10");
        insertIcd("E11.9", "2 型糖尿病", "ICD10");
        insertIcd("K35.9", "急性阑尾炎", "ICD10");
        insertIcd("K29.7", "胃炎", "ICD10");
        insertIcd("N18.9", "慢性肾脏病", "ICD10");
        insertIcd("N39.0", "泌尿道感染", "ICD10");
        insertIcd("S72.0", "股骨颈骨折", "ICD10");
        insertIcd("A16.2", "肺结核", "ICD10");
        insertIcd("R50.9", "发热", "ICD10");
        insertIcd("O80", "顺产", "ICD10");
        insertIcd("47.0", "阑尾切除术", "ICD9CM3");
        insertIcd("45.13", "结肠镜检查", "ICD9CM3");
        insertIcd("51.23", "腹腔镜胆囊切除术", "ICD9CM3");
        insertIcd("39.95", "血液透析", "ICD9CM3");
        insertIcd("88.56", "冠状动脉造影", "ICD9CM3");
        insertIcd("93.90", "无创通气", "ICD9CM3");
        insertIcd("96.71", "有创机械通气", "ICD9CM3");
        insertIcd("03.31", "腰椎穿刺", "ICD9CM3");
        insertIcd("74.1", "剖宫产", "ICD9CM3");
        insertIcd("81.51", "全髋关节置换", "ICD9CM3");
        log.info("seeded 25 ICD codes");
    }

    private void insertIcd(String code, String name, String type) {
        EmrIcdCode icd = new EmrIcdCode();
        icd.setCode(code);
        icd.setName(name);
        icd.setType(type);
        icd.setEnabled(true);
        icd.setCreatedAt(LocalDateTime.now());
        icdCodeMapper.insert(icd);
    }

    private void seedCdssRules() {
        if (cdssRuleMapper.selectCount(Wrappers.<CdssRule>lambdaQuery()) > 0) {
            return;
        }
        insertCdssRule("DRUG_UNKNOWN", "药品知识库未收录", "form", "INFO",
                "药品编码不在知识库中时提示人工核对");
        insertCdssRule("DRUG_CONTRAINDICATION", "诊断禁忌", "content", "BLOCK",
                "诊断命中药品禁忌时禁止开立");
        insertCdssRule("DRUG_INTERACTION", "药物相互作用", "content", "WARN",
                "命中相互作用表时提醒，表内 BLOCK 级直接阻断");
        insertCdssRule("DUPLICATE_DRUG", "重复用药", "content", "WARN",
                "同一处方内重复出现同一药品");
        insertCdssRule("CHILD_DOSE", "儿童剂量", "content", "WARN",
                "儿童患者按体重校验单次剂量");
        insertCdssRule("MAX_DAILY_DOSE", "日剂量上限", "content", "WARN",
                "日剂量超过药品上限");
        insertCdssRule("RENAL_ADJUST", "肾功能剂量调整", "content", "WARN",
                "eGFR 低于 60 时提示剂量调整");
        insertCdssRule("PREGNANCY", "孕产妇用药", "content", "WARN",
                "孕产妇使用有风险药品时提醒");
        log.info("seeded 8 CDSS rules");
    }

    private void insertCdssRule(String code, String name, String category,
                                String severity, String description) {
        CdssRule rule = new CdssRule();
        rule.setRuleCode(code);
        rule.setName(name);
        rule.setCategory(category);
        rule.setSeverity(severity);
        rule.setDescription(description);
        rule.setEnabled(true);
        rule.setCreatedAt(LocalDateTime.now());
        cdssRuleMapper.insert(rule);
    }

    private void seedDrugs() {
        if (cdssDrugMapper.selectCount(Wrappers.<CdssDrug>lambdaQuery()) > 0) {
            return;
        }
        insertDrug("AMOXICILLIN", "阿莫西林", "青霉素类",
                "[\"J18.9\",\"K35.9\"]", "[]",
                "0.5g 口服 q8h", new BigDecimal("0.05"), new BigDecimal("3000"),
                "eGFR<30 时减量并延长给药间隔", "妊娠期可用，哺乳期慎用");
        insertDrug("CEFTRIAXONE", "头孢曲松", "头孢菌素类",
                "[\"J18.9\",\"N39.0\"]", "[]",
                "1g 静滴 qd", new BigDecimal("0.05"), new BigDecimal("4000"),
                "一般无需调整", "妊娠期可用");
        insertDrug("AZITHROMYCIN", "阿奇霉素", "大环内酯类",
                "[\"J18.9\",\"J45.9\"]", "[]",
                "0.5g 口服 qd", new BigDecimal("0.01"), new BigDecimal("500"),
                "轻中度肾功能不全无需调整", "妊娠期慎用");
        insertDrug("LEVOFLOXACIN", "左氧氟沙星", "喹诺酮类",
                "[\"J18.9\",\"N39.0\"]", "[]",
                "0.5g 口服 qd", null, new BigDecimal("750"),
                "eGFR<50 时减量", "妊娠及哺乳期禁用，18 岁以下禁用");
        insertDrug("IBUPROFEN", "布洛芬", "NSAID",
                "[\"R50.9\"]", "[\"K29.7\"]",
                "0.2g 口服 q8h", new BigDecimal("0.01"), new BigDecimal("1200"),
                "eGFR<30 避免使用", "妊娠晚期禁用");
        insertDrug("PARACETAMOL", "对乙酰氨基酚", "解热镇痛",
                "[\"R50.9\"]", "[]",
                "0.5g 口服 q8h", new BigDecimal("0.015"), new BigDecimal("2000"),
                "eGFR<30 延长给药间隔", "妊娠期可用");
        insertDrug("WARFARIN", "华法林", "抗凝药",
                "[\"I63.9\"]", "[]",
                "3mg 口服 qd，按 INR 调整", null, null,
                "无需调整，监测 INR", "妊娠期禁用");
        insertDrug("ASPIRIN", "阿司匹林", "抗血小板",
                "[\"I63.9\",\"I10\"]", "[\"K29.7\"]",
                "100mg 口服 qd", null, new BigDecimal("100"),
                "无需调整", "妊娠晚期禁用");
        insertDrug("ATORVASTATIN", "阿托伐他汀", "调脂药",
                "[\"I10\",\"E11.9\"]", "[]",
                "20mg 口服 qd", null, new BigDecimal("40"),
                "无需调整", "妊娠期禁用");
        insertDrug("CLARITHROMYCIN", "克拉霉素", "大环内酯类",
                "[\"J18.9\"]", "[]",
                "0.5g 口服 q12h", new BigDecimal("0.0075"), new BigDecimal("1000"),
                "eGFR<30 时减量", "妊娠期慎用");
        insertDrug("DIGOXIN", "地高辛", "强心苷",
                "[\"I10\"]", "[]",
                "0.125mg 口服 qd", null, new BigDecimal("0.25"),
                "eGFR<50 时减量", "妊娠期可用");
        insertDrug("FUROSEMIDE", "呋塞米", "利尿剂",
                "[\"I10\"]", "[]",
                "20mg 口服 qd", new BigDecimal("0.01"), new BigDecimal("80"),
                "大剂量慎用", "妊娠期慎用");
        insertDrug("OMEPRAZOLE", "奥美拉唑", "质子泵抑制剂",
                "[\"K29.7\"]", "[]",
                "20mg 口服 qd", new BigDecimal("0.01"), new BigDecimal("40"),
                "无需调整", "妊娠期可用");
        insertDrug("METFORMIN", "二甲双胍", "双胍类",
                "[\"E11.9\"]", "[\"N18.9\"]",
                "0.5g 口服 bid", new BigDecimal("0.01"), new BigDecimal("2550"),
                "eGFR<45 减量，<30 禁用", "妊娠期慎用");
        log.info("seeded 14 CDSS drugs");
    }

    private void insertDrug(String code, String name, String category,
                            String indicationsJson, String contraindicationsJson,
                            String adultDose, BigDecimal childDoseMgPerKg,
                            BigDecimal maxDailyDoseMg, String renalAdjustNote,
                            String pregnancyNote) {
        CdssDrug drug = new CdssDrug();
        drug.setDrugCode(code);
        drug.setDrugName(name);
        drug.setCategory(category);
        drug.setIndicationsJson(indicationsJson);
        drug.setContraindicationsJson(contraindicationsJson);
        drug.setAdultDose(adultDose);
        drug.setChildDoseMgPerKg(childDoseMgPerKg);
        drug.setMaxDailyDoseMg(maxDailyDoseMg);
        drug.setRenalAdjustNote(renalAdjustNote);
        drug.setPregnancyNote(pregnancyNote);
        drug.setEnabled(true);
        drug.setCreatedAt(LocalDateTime.now());
        cdssDrugMapper.insert(drug);
    }

    private void seedDrugInteractions() {
        if (cdssInteractionMapper.selectCount(Wrappers.<CdssDrugInteraction>lambdaQuery()) > 0) {
            return;
        }
        insertInteraction("ASPIRIN", "WARFARIN", "BLOCK",
                "抗血小板与抗凝联用显著增加出血风险");
        insertInteraction("IBUPROFEN", "WARFARIN", "BLOCK",
                "NSAID 损伤胃黏膜并抑制血小板，显著增加华法林出血风险");
        insertInteraction("ATORVASTATIN", "CLARITHROMYCIN", "WARN",
                "克拉霉素抑制 CYP3A4，升高他汀血药浓度，增加肌病风险");
        insertInteraction("DIGOXIN", "FUROSEMIDE", "WARN",
                "呋塞米致低钾，增加洋地黄中毒风险");
        insertInteraction("IBUPROFEN", "LEVOFLOXACIN", "WARN",
                "喹诺酮与 NSAID 联用增加中枢兴奋与抽搐风险");
        insertInteraction("ASPIRIN", "IBUPROFEN", "WARN",
                "布洛芬可减弱阿司匹林的抗血小板作用");
        log.info("seeded 6 drug interactions");
    }

    private void insertInteraction(String drugACode, String drugBCode,
                                   String severity, String mechanism) {
        CdssDrugInteraction interaction = new CdssDrugInteraction();
        interaction.setDrugACode(drugACode);
        interaction.setDrugBCode(drugBCode);
        interaction.setSeverity(severity);
        interaction.setMechanism(mechanism);
        interaction.setEnabled(true);
        interaction.setCreatedAt(LocalDateTime.now());
        cdssInteractionMapper.insert(interaction);
    }

    private void seedExamItems() {
        if (cdssExamItemMapper.selectCount(Wrappers.<CdssExamItem>lambdaQuery()) > 0) {
            return;
        }
        insertExamItem("CBC", "血常规", "LAB", "血液", "EDTA 抗凝全血", "采血后 2 小时内送检",
                "[\"J18.9\",\"R50.9\",\"K35.9\"]", "WBC 4.0-10.0×10^9/L", "×10^9/L",
                null, null, 60);
        insertExamItem("K", "血钾", "LAB", "生化", "血清", "避免溶血，采血后尽快分离血清",
                "[\"I10\",\"N18.9\",\"E11.9\"]", "3.5-5.5", "mmol/L",
                new BigDecimal("3.0"), new BigDecimal("6.0"), 60);
        insertExamItem("NA", "血钠", "LAB", "生化", "血清", "避免溶血，采血后尽快分离血清",
                "[\"I10\",\"N18.9\"]", "135-145", "mmol/L",
                new BigDecimal("120"), new BigDecimal("160"), 60);
        insertExamItem("CR", "血肌酐", "LAB", "生化", "血清", "避免剧烈运动后采血",
                "[\"N18.9\",\"E11.9\",\"I10\"]", "44-133", "μmol/L",
                null, new BigDecimal("707"), 60);
        insertExamItem("GLU", "空腹血糖", "LAB", "生化", "血清", "空腹 8 小时以上",
                "[\"E11.9\",\"R50.9\"]", "3.9-6.1", "mmol/L",
                new BigDecimal("2.8"), new BigDecimal("22.0"), 60);
        insertExamItem("ALT", "丙氨酸氨基转移酶", "LAB", "生化", "血清", "空腹 8 小时以上",
                "[\"K29.7\",\"N18.9\"]", "0-40", "U/L",
                null, null, 120);
        insertExamItem("TBIL", "总胆红素", "LAB", "生化", "血清", "避光，空腹 8 小时以上",
                "[\"K29.7\"]", "0-21", "μmol/L",
                null, new BigDecimal("340"), 120);
        insertExamItem("HGB", "血红蛋白", "LAB", "血液", "EDTA 抗凝全血", "采血后 2 小时内送检",
                "[\"N18.9\",\"K35.9\"]", "130-175", "g/L",
                new BigDecimal("50"), null, 60);
        insertExamItem("PLT", "血小板计数", "LAB", "血液", "EDTA 抗凝全血", "避免凝血，采血后尽快送检",
                "[\"J18.9\",\"K35.9\"]", "125-350", "×10^9/L",
                new BigDecimal("20"), null, 60);
        insertExamItem("PT", "凝血酶原时间", "LAB", "凝血", "枸橼酸钠抗凝血浆", "按 9:1 抗凝比例采血",
                "[\"I63.9\",\"I10\"]", "11-14", "s",
                null, new BigDecimal("30"), 120);
        insertExamItem("CRP", "C 反应蛋白", "LAB", "免疫", "血清", "避免溶血",
                "[\"J18.9\",\"R50.9\"]", "0-10", "mg/L",
                null, new BigDecimal("100"), 120);
        insertExamItem("PCT", "降钙素原", "LAB", "免疫", "血清", "避免溶血，采血后尽快送检",
                "[\"J18.9\",\"N39.0\"]", "0-0.05", "ng/mL",
                null, new BigDecimal("10"), 180);

        insertExamItem("CT_HEAD", "头颅 CT", "EXAM", "影像", "头部", "去除金属物品；碘对比剂需评估肾功能",
                "[\"I63.9\"]", null, null, null, null, 120);
        insertExamItem("CT_CHEST", "胸部 CT", "EXAM", "影像", "胸部", "去除金属物品；碘对比剂需评估肾功能",
                "[\"J18.9\",\"J44.9\",\"A16.2\"]", null, null, null, null, 120);
        insertExamItem("MRI_HEAD", "头颅 MRI", "EXAM", "影像", "头部", "体内金属植入物及心脏起搏器禁用",
                "[\"I63.9\"]", null, null, null, null, 240);
        insertExamItem("US_ABDOMEN", "腹部超声", "EXAM", "超声", "腹部", "空腹 8 小时以上，检查前排空膀胱",
                "[\"K35.9\",\"K29.7\"]", null, null, null, null, 30);
        insertExamItem("ECG", "十二导联心电图", "EXAM", "电生理", "心脏", "静息状态，避免剧烈运动后立即检查",
                "[\"I10\",\"I63.9\"]", null, null, null, null, 15);
        insertExamItem("XRAY_CHEST", "胸部 X 线", "EXAM", "影像", "胸部", "去除胸部金属物品",
                "[\"J18.9\",\"J44.9\"]", null, null, null, null, 30);
        insertExamItem("GASTROSCOPY", "胃镜检查", "EXAM", "内镜", "胃及十二指肠", "空腹 8 小时以上，检查前取下义齿",
                "[\"K29.7\"]", null, null, null, null, 60);
        insertExamItem("COLONOSCOPY", "结肠镜检查", "EXAM", "内镜", "结肠", "检查前完成肠道准备，禁食 6 小时以上",
                "[\"K29.7\",\"K35.9\"]", null, null, null, null, 90);
        log.info("seeded 20 exam items (12 lab + 8 exam)");
    }

    private void insertExamItem(String code, String name, String type, String category,
                                String specimen, String precaution, String indicationsJson,
                                String referenceRange, String unit, BigDecimal criticalLow,
                                BigDecimal criticalHigh, int turnaroundMinutes) {
        CdssExamItem item = new CdssExamItem();
        item.setItemCode(code);
        item.setItemName(name);
        item.setItemType(type);
        item.setCategory(category);
        item.setSpecimen(specimen);
        item.setPrecaution(precaution);
        item.setIndicationsJson(indicationsJson);
        item.setReferenceRange(referenceRange);
        item.setUnit(unit);
        item.setCriticalLow(criticalLow);
        item.setCriticalHigh(criticalHigh);
        item.setTurnaroundMinutes(turnaroundMinutes);
        item.setEnabled(true);
        item.setCreatedAt(LocalDateTime.now());
        cdssExamItemMapper.insert(item);
    }

    private void seedPathways() {
        if (clinicalPathwayMapper.selectCount(Wrappers.<ClinicalPathway>lambdaQuery()) > 0) {
            return;
        }
        insertPathway("CAP", "社区获得性肺炎", "J18.9", "呼吸内科", 7,
                "成人社区获得性肺炎标准化诊疗路径", new String[][]{
                        {"ASSESS", "入院评估", "0"},
                        {"ANTIBIOTIC", "抗感染治疗", "1"},
                        {"RECHECK", "复查血常规与胸片", "3"},
                        {"EFFICACY", "疗效评估", "5"},
                        {"DISCHARGE", "出院评估", "6"}});
        insertPathway("ACI", "急性脑梗死", "I63.9", "神经内科", 10,
                "急性缺血性脑卒中标准化诊疗路径", new String[][]{
                        {"ASSESS", "入院评估与 NIHSS 评分", "0"},
                        {"ANTIPLATELET", "抗血小板/溶栓治疗", "1"},
                        {"REHAB", "康复评估", "3"},
                        {"IMAGING", "复查影像", "7"},
                        {"DISCHARGE", "出院评估", "9"}});
        insertPathway("APPENDECTOMY", "急性阑尾炎", "K35.9", "普外科", 5,
                "腹腔镜阑尾切除术临床路径", new String[][]{
                        {"ASSESS", "入院评估", "0"},
                        {"PREOP", "术前准备", "0"},
                        {"OPERATION", "手术", "0"},
                        {"POSTOP", "术后观察", "1"},
                        {"DISCHARGE", "出院评估", "3"}});
        insertPathway("CESAREAN", "剖宫产", "O80", "产科", 5,
                "剖宫产临床路径", new String[][]{
                        {"ASSESS", "入院评估", "0"},
                        {"PREOP", "术前准备", "0"},
                        {"OPERATION", "手术", "0"},
                        {"POSTOP", "术后观察", "1"},
                        {"DISCHARGE", "出院评估", "3"}});
        insertPathway("HEART_FAILURE", "心力衰竭", "I50.9", "心内科", 7,
                "慢性心力衰竭急性加重诊疗路径", new String[][]{
                        {"ASSESS", "入院评估", "0"},
                        {"DIURETIC", "利尿与扩血管治疗", "1"},
                        {"ELECTROLYTE", "复查电解质与肾功能", "3"},
                        {"CARDIAC_FUNCTION", "心功能评估", "5"},
                        {"DISCHARGE", "出院评估", "6"}});
        log.info("seeded 5 clinical pathways");
    }

    private void insertPathway(String code, String name, String diagnosisCode,
                               String department, int standardDays, String description,
                               String[][] nodes) {
        ClinicalPathway pathway = new ClinicalPathway();
        pathway.setPathwayCode(code);
        pathway.setName(name);
        pathway.setDiagnosisCode(diagnosisCode);
        pathway.setDepartment(department);
        pathway.setVersion(1);
        pathway.setStandardDays(standardDays);
        pathway.setDescription(description);
        pathway.setEnabled(true);
        pathway.setCreatedAt(LocalDateTime.now());
        clinicalPathwayMapper.insert(pathway);

        int sequence = 1;
        for (String[] node : nodes) {
            PathwayNode entity = new PathwayNode();
            entity.setPathwayId(pathway.getId());
            entity.setSequence(sequence++);
            entity.setNodeCode(node[0]);
            entity.setNodeName(node[1]);
            entity.setDayOffset(Integer.parseInt(node[2]));
            entity.setRequired(true);
            pathwayNodeMapper.insert(entity);
        }
    }
}
