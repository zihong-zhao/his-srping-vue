# E1 + E2 + E3 + E6 最小闭环冒烟测试
# 前置：数据库已初始化，应用已在 http://localhost:8080 运行
# 用法：powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1

param([string]$BaseUrl = "http://localhost:8080")

$ErrorActionPreference = "Stop"

function Login($username, $password) {
    $body = @{ username = $username; password = $password } | ConvertTo-Json
    $resp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" `
        -ContentType "application/json; charset=utf-8" -Body $body
    return $resp.data.token
}

Write-Host "1) 登录 doctor1"
$doctorToken = Login "doctor1" "doctor123"
$doctorHeaders = @{ Authorization = "Bearer $doctorToken" }

Write-Host "2) 查询门诊病历模板"
$templates = Invoke-RestMethod -Uri "$BaseUrl/api/emr/templates?docType=OUTPATIENT_NOTE" -Headers $doctorHeaders
$template = $templates.data[0]
Write-Host "   模板: $($template.name) (id=$($template.id), v$($template.version))"

Write-Host "3) 创建病历草稿"
$createBody = @{
    patientId  = 10001
    visitId    = 20001
    docType    = "OUTPATIENT_NOTE"
    templateId = $template.id
    title      = "门诊病历-演示"
} | ConvertTo-Json
$doc = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/emr/documents" `
    -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" -Body $createBody
$docId = $doc.data.id
Write-Host "   病历 id=$docId, 状态=$($doc.data.status), 版本=$($doc.data.currentVersion)"

Write-Host "4) 书写并保存（生成版本 2）"
$content = @{
    sections = @(
        @{ code = "chief_complaint"; title = "主诉"; type = "text"; value = "发热三天" },
        @{ code = "present_illness"; title = "现病史"; type = "text"; value = "三天前受凉后发热，最高 38.9℃" },
        @{ code = "physical_exam"; title = "体格检查"; type = "text"; value = "T 38.5℃，咽部充血" },
        @{ code = "diagnosis"; title = "诊断"; type = "icd"; value = @(@{ code = "J18.9"; name = "肺炎" }) },
        @{ code = "treatment_plan"; title = "处理意见"; type = "text"; value = "抗感染治疗，三天后复诊" }
    )
}
$saveBody = @{ content = $content; changeSummary = "首次书写" } | ConvertTo-Json -Depth 10
$saved = Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/emr/documents/$docId" `
    -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" -Body $saveBody
Write-Host "   保存成功，当前版本=$($saved.data.currentVersion)"

Write-Host "5) 提交病历"
$submitted = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/emr/documents/$docId/submit" -Headers $doctorHeaders
Write-Host "   状态=$($submitted.data.status)"

Write-Host "6) 电子签名"
$signed = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/emr/documents/$docId/sign" -Headers $doctorHeaders
Write-Host "   状态=$($signed.data.status), 签名时间=$($signed.data.signedAt)"

Write-Host "7) 验签"
$verify = Invoke-RestMethod -Uri "$BaseUrl/api/emr/documents/$docId/verify" -Headers $doctorHeaders
Write-Host "   有效=$($verify.data.valid), 说明=$($verify.data.message), 证书=$($verify.data.certSn)"

Write-Host "8) 版本差异（v1 -> v2）"
$diff = Invoke-RestMethod -Uri "$BaseUrl/api/emr/documents/$docId/versions/diff?from=1&to=2" -Headers $doctorHeaders
Write-Host "   变更段落数=$($diff.data.changes.Count)"

Write-Host "9) 审计日志（admin）"
$adminToken = Login "admin" "admin123"
$adminHeaders = @{ Authorization = "Bearer $adminToken" }
$logs = Invoke-RestMethod -Uri "$BaseUrl/api/audit/logs?limit=10" -Headers $adminHeaders
Write-Host "   最近审计条数=$($logs.data.Count)"

Write-Host "10) 运行质控（E2）"
$qc = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/emr/documents/$docId/qc/run" -Headers $doctorHeaders
Write-Host "   阻断=$($qc.data.blocked), 缺陷数=$($qc.data.issues.Count)"

Write-Host "11) 质控员查看规则与缺陷"
$qcToken = Login "qc1" "qc123"
$qcHeaders = @{ Authorization = "Bearer $qcToken" }
$rules = Invoke-RestMethod -Uri "$BaseUrl/api/emr/qc/rules" -Headers $qcHeaders
$issues = Invoke-RestMethod -Uri "$BaseUrl/api/emr/qc/issues?docId=$docId" -Headers $qcHeaders
Write-Host "   规则数=$($rules.data.Count), 该病历缺陷数=$($issues.data.Count)"

Write-Host "12) 患者全流程时间轴（E3）"
$timeline = Invoke-RestMethod -Uri "$BaseUrl/api/cdr/patients/10001/timeline" -Headers $doctorHeaders
Write-Host "   病历数=$($timeline.data.totalDocuments), 已签名=$($timeline.data.signedDocuments), 事件数=$($timeline.data.events.Count)"

Write-Host "13) 归档（E7，病案质控）"
$archived = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/emr/documents/$docId/archive" -Headers $qcHeaders
Write-Host "   状态=$($archived.data.status), 归档哈希=$($archived.data.archiveHash)"

Write-Host "14) 归档后修改应被拒绝"
try {
    $tamperBody = @{ content = $content; changeSummary = "归档后修改" } | ConvertTo-Json -Depth 10
    Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/emr/documents/$docId" `
        -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" -Body $tamperBody | Out-Null
    Write-Host "   异常：归档病历居然可以修改"
    exit 1
} catch {
    Write-Host "   已拒绝：HTTP $([int]$_.Exception.Response.StatusCode)"
}

Write-Host "15) 封存与调阅控制（E6-07）"
$sealed = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/emr/documents/$docId/seal" `
    -Headers $adminHeaders -ContentType "application/json; charset=utf-8" `
    -Body (@{ reason = "司法封存演练" } | ConvertTo-Json)
Write-Host "   封存标记=$($sealed.data.sealed)"
try {
    Invoke-RestMethod -Uri "$BaseUrl/api/emr/documents/$docId" -Headers $doctorHeaders | Out-Null
    Write-Host "   异常：封存病历仍可被医生调阅"
    exit 1
} catch {
    Write-Host "   医生调阅已拒绝：HTTP $([int]$_.Exception.Response.StatusCode)"
}
$adminView = Invoke-RestMethod -Uri "$BaseUrl/api/emr/documents/$docId" -Headers $adminHeaders
Write-Host "   管理员调阅通过，状态=$($adminView.data.status)"

Write-Host "16) 解封"
$unsealed = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/emr/documents/$docId/unseal" `
    -Headers $adminHeaders -ContentType "application/json; charset=utf-8" `
    -Body (@{ reason = "封存到期" } | ConvertTo-Json)
Write-Host "   封存标记=$($unsealed.data.sealed)"
$doctorView = Invoke-RestMethod -Uri "$BaseUrl/api/emr/documents/$docId" -Headers $doctorHeaders
Write-Host "   医生恢复调阅，状态=$($doctorView.data.status)"

Write-Host "17) 借阅申请（E7-04，非作者医生）"
$doctor2Token = Login "doctor2" "doctor123"
$doctor2Headers = @{ Authorization = "Bearer $doctor2Token" }
try {
    Invoke-RestMethod -Uri "$BaseUrl/api/emr/documents/$docId" -Headers $doctor2Headers | Out-Null
    Write-Host "   异常：未借阅即可调阅"
    exit 1
} catch {
    Write-Host "   未借阅调阅已拒绝：HTTP $([int]$_.Exception.Response.StatusCode)"
}
$borrowBody = @{ docId = $docId; reason = "科研回顾"; days = 7 } | ConvertTo-Json
$borrow = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/emr/borrow-requests" `
    -Headers $doctor2Headers -ContentType "application/json; charset=utf-8" -Body $borrowBody
Write-Host "   借阅申请 id=$($borrow.data.id), 状态=$($borrow.data.status)"

Write-Host "18) 质控审批借阅"
$approvedBorrow = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/emr/borrow-requests/$($borrow.data.id)/approve" `
    -Headers $qcHeaders -ContentType "application/json; charset=utf-8" `
    -Body (@{ note = "同意借阅" } | ConvertTo-Json)
Write-Host "   状态=$($approvedBorrow.data.status), 到期=$($approvedBorrow.data.dueAt)"

Write-Host "19) 借阅期内调阅与列表"
$borrowedView = Invoke-RestMethod -Uri "$BaseUrl/api/emr/documents/$docId" -Headers $doctor2Headers
Write-Host "   借阅调阅通过，状态=$($borrowedView.data.status)"
$borrowList = Invoke-RestMethod -Uri "$BaseUrl/api/emr/borrow-requests?status=APPROVED" -Headers $doctor2Headers
Write-Host "   我的借阅记录数=$($borrowList.data.Count)"

Write-Host "20) 归还后调阅失效"
$returned = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/emr/borrow-requests/$($borrow.data.id)/return" -Headers $doctor2Headers
Write-Host "   状态=$($returned.data.status)"
try {
    Invoke-RestMethod -Uri "$BaseUrl/api/emr/documents/$docId" -Headers $doctor2Headers | Out-Null
    Write-Host "   异常：归还后仍可调阅"
    exit 1
} catch {
    Write-Host "   归还后调阅已拒绝：HTTP $([int]$_.Exception.Response.StatusCode)"
}

Write-Host "21) 复印申请 -> 审批 -> 完成"
$copy = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/emr/copy-requests" `
    -Headers $doctor2Headers -ContentType "application/json; charset=utf-8" `
    -Body (@{ docId = $docId; purpose = "医保报销"; copies = 2 } | ConvertTo-Json)
Write-Host "   复印申请 id=$($copy.data.id), 状态=$($copy.data.status)"
$approvedCopy = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/emr/copy-requests/$($copy.data.id)/approve" `
    -Headers $qcHeaders -ContentType "application/json; charset=utf-8" -Body "{}"
Write-Host "   审批后状态=$($approvedCopy.data.status)"
$doneCopy = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/emr/copy-requests/$($copy.data.id)/complete" -Headers $qcHeaders
Write-Host "   完成状态=$($doneCopy.data.status), 操作人=$($doneCopy.data.completedByName), 份数=$($doneCopy.data.copies)"

Write-Host "22) ICD 编码字典（E7-05）"
$icd = Invoke-RestMethod -Uri "$BaseUrl/api/emr/icd/codes?type=ICD10&keyword=%E8%82%BA" -Headers $qcHeaders
Write-Host "   匹配编码数=$($icd.data.Count)"

Write-Host "23) 保存病案首页（E7-01）"
$frontPageBody = @{
    patientId      = 10001
    visitId        = 20002
    inpatientNo    = "ZY20002"
    admissionDate  = "2026-11-01T09:00:00"
    dischargeDate  = "2026-11-06T10:00:00"
    admissionDept  = "呼吸内科"
    dischargeDept  = "呼吸内科"
    dischargeWay   = "医嘱离院"
    attendingDoctor = "张医生"
    diagnoses      = @(
        @{ code = "J18.9"; name = "肺炎"; main = $true },
        @{ code = "I10"; name = "原发性高血压"; main = $false }
    )
    surgeries      = @()
    cost           = @{ total = 12000; drug = 5000; material = 1000; bed = 2000; exam = 2000; treatment = 1500; nursing = 500; other = 0 }
} | ConvertTo-Json -Depth 10
$frontPage = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/emr/front-pages" `
    -Headers $qcHeaders -ContentType "application/json; charset=utf-8" -Body $frontPageBody
Write-Host "   病案首页 id=$($frontPage.data.id), 状态=$($frontPage.data.status), 诊断数=$($frontPage.data.diagnoses.Count)"

Write-Host "24) ICD 编码核对"
$fpVerify = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/emr/front-pages/$($frontPage.data.id)/verify" -Headers $qcHeaders
Write-Host "   核对通过=$($fpVerify.data.verified), 问题数=$($fpVerify.data.issues.Count)"
if (-not $fpVerify.data.verified) { Write-Host "   异常：合法首页核对未通过"; exit 1 }

Write-Host "25) 无效编码核对应被阻断"
$badPage = $frontPageBody | ConvertFrom-Json
$badPage.diagnoses[0].code = "BAD.9"
$badPage.cost.total = 13000
$badBody = $badPage | ConvertTo-Json -Depth 10
$badSaved = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/emr/front-pages" `
    -Headers $qcHeaders -ContentType "application/json; charset=utf-8" -Body $badBody
$badVerify = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/emr/front-pages/$($badSaved.data.id)/verify" -Headers $qcHeaders
Write-Host "   核对通过=$($badVerify.data.verified), 问题数=$($badVerify.data.issues.Count)"
if ($badVerify.data.verified) { Write-Host "   异常：无效编码未被阻断"; exit 1 }

Write-Host "26) 四条闭环开立（E5）"
$loopChains = [ordered]@{
    MED_ORDER    = @("REVIEWED", "EXECUTED", "CHARGED")
    PRESCRIPTION = @("REVIEWED", "CHARGED", "DISPENSED", "DISPENSED_OUT")
    LAB          = @("CHARGED", "SAMPLED", "RECEIVED", "TESTED", "VERIFIED", "REPORTED", "VIEWED")
    EXAM         = @("CHARGED", "SCHEDULED", "PERFORMED", "REPORTED", "VIEWED")
}
$loopIds = @{}
foreach ($type in $loopChains.Keys) {
    $loopBody = @{ loopType = $type; patientId = 10001; visitId = 20001; title = "$type 闭环演示" } | ConvertTo-Json
    $loop = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/e5/loops" `
        -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" -Body $loopBody
    $loopIds[$type] = $loop.data.id
    Write-Host "   $type 单据 id=$($loop.data.id), 当前节点=$($loop.data.currentNode)"
}

Write-Host "27) 乱序推进应被拒绝"
try {
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/e5/loops/$($loopIds['LAB'])/advance" `
        -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" `
        -Body (@{ nodeCode = "REPORTED" } | ConvertTo-Json) | Out-Null
    Write-Host "   异常：乱序推进未被拒绝"
    exit 1
} catch {
    Write-Host "   已拒绝：HTTP $([int]$_.Exception.Response.StatusCode)"
}

Write-Host "28) 按序推进四条闭环"
foreach ($type in $loopChains.Keys) {
    $adv = $null
    foreach ($node in $loopChains[$type]) {
        $adv = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/e5/loops/$($loopIds[$type])/advance" `
            -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" `
            -Body (@{ nodeCode = $node } | ConvertTo-Json)
    }
    Write-Host "   $type 最终状态=$($adv.data.status), 节点数=$($adv.data.nodes.Count)"
    if ($adv.data.status -ne "CLOSED") { Write-Host "   异常：$type 未闭环"; exit 1 }
}

Write-Host "29) 闭环率统计"
$stats = Invoke-RestMethod -Uri "$BaseUrl/api/e5/loops/stats" -Headers $qcHeaders
foreach ($s in $stats.data) {
    Write-Host "   $($s.loopType)：$($s.closed)/$($s.total)，闭环率 $($s.rate)%"
}
$labStat = $stats.data | Where-Object { $_.loopType -eq "LAB" }
if ([double]$labStat.rate -lt 100) { Write-Host "   异常：检验闭环率未达 100%"; exit 1 }
$closedLoops = Invoke-RestMethod -Uri "$BaseUrl/api/e5/loops?patientId=10001&status=CLOSED" -Headers $doctorHeaders
Write-Host "   已闭环单据数=$($closedLoops.data.Count)"

Write-Host "30) 药品知识库检索（E4）"
$drugs = Invoke-RestMethod -Uri "$BaseUrl/api/e4/drugs?keyword=华法林" -Headers $doctorHeaders
Write-Host "   匹配药品数=$($drugs.data.Count)"
if ($drugs.data.Count -lt 1) { Write-Host "   异常：未检索到药品"; exit 1 }

Write-Host "31) 合理用药审查-安全处方"
$safeBody = @{
    patientId      = 10001
    visitId        = 20001
    ageYears       = 35
    weightKg       = 65
    diagnosisCodes = @("J18.9")
    drugs          = @(
        @{ drugCode = "AMOXICILLIN"; dose = 500; unit = "mg"; frequency = "TID" },
        @{ drugCode = "PARACETAMOL"; dose = 500; unit = "mg"; frequency = "TID" }
    )
} | ConvertTo-Json -Depth 10
$safeReview = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/e4/prescriptions/review" `
    -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" -Body $safeBody
Write-Host "   决策=$($safeReview.data.decision), 阻断=$($safeReview.data.blocked), 提醒数=$($safeReview.data.alerts.Count)"
if ($safeReview.data.decision -ne "PASSED") { Write-Host "   异常：安全处方被拦截"; exit 1 }

Write-Host "32) 相互作用阻断（华法林 + 阿司匹林）"
$blockBody = @{
    patientId      = 10001
    visitId        = 20001
    ageYears       = 68
    diagnosisCodes = @("I63.9")
    drugs          = @(
        @{ drugCode = "WARFARIN"; dose = 3; unit = "mg"; frequency = "QD" },
        @{ drugCode = "ASPIRIN"; dose = 100; unit = "mg"; frequency = "QD" }
    )
} | ConvertTo-Json -Depth 10
$blockReview = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/e4/prescriptions/review" `
    -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" -Body $blockBody
Write-Host "   决策=$($blockReview.data.decision), 阻断=$($blockReview.data.blocked), 提醒数=$($blockReview.data.alerts.Count)"
if (-not $blockReview.data.blocked) { Write-Host "   异常：阻断级相互作用未生效"; exit 1 }

Write-Host "33) 强提醒填理由与忽略留痕（E4-07）"
$warnBody = @{
    patientId      = 10001
    visitId        = 20001
    ageYears       = 68
    diagnosisCodes = @("I10")
    drugs          = @(
        @{ drugCode = "ATORVASTATIN"; dose = 20; unit = "mg"; frequency = "QD" },
        @{ drugCode = "CLARITHROMYCIN"; dose = 500; unit = "mg"; frequency = "BID" }
    )
} | ConvertTo-Json -Depth 10
$warnReview = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/e4/prescriptions/review" `
    -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" -Body $warnBody
Write-Host "   未填理由：决策=$($warnReview.data.decision), 需要理由=$($warnReview.data.requiresOverride)"
if (-not $warnReview.data.requiresOverride) { Write-Host "   异常：强提醒未要求填写理由"; exit 1 }

$overrideBody = $warnBody | ConvertFrom-Json
$overrideBody | Add-Member -NotePropertyName overrideReason -NotePropertyValue "患者感染明确，短期联用并监测肌酸激酶" -Force
$overridden = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/e4/prescriptions/review" `
    -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" `
    -Body ($overrideBody | ConvertTo-Json -Depth 10)
Write-Host "   填写理由后决策=$($overridden.data.decision)"
if ($overridden.data.decision -ne "OVERRIDDEN") { Write-Host "   异常：填写理由后未放行"; exit 1 }

$warnAlert = $overridden.data.alerts | Where-Object { $_.severity -eq "WARN" } | Select-Object -First 1
$ignoreBody = @{ reason = "已与临床药师复核，确认短期使用" } | ConvertTo-Json
$ignored = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/e4/alerts/$($warnAlert.id)/ignore" `
    -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" -Body $ignoreBody
Write-Host "   忽略后决策=$($ignored.data.decision), 理由=$($ignored.data.overrideReason)"
if ($ignored.data.decision -ne "IGNORED") { Write-Host "   异常：忽略未留痕"; exit 1 }

Write-Host "34) 危急值上报 -> 确认 -> 关闭（E4-04）"
$cvBody = @{
    patientId      = 10001
    visitId        = 20001
    itemCode       = "K"
    itemName       = "血钾"
    resultValue    = "6.8"
    unit           = "mmol/L"
    referenceRange = "3.5-5.5"
    severity       = "HIGH"
} | ConvertTo-Json
$cv = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/e4/critical-values" `
    -Headers $qcHeaders -ContentType "application/json; charset=utf-8" -Body $cvBody
Write-Host "   上报 id=$($cv.data.id), 状态=$($cv.data.status), 上报人=$($cv.data.reportedByName)"
$cvConfirmed = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/e4/critical-values/$($cv.data.id)/confirm" -Headers $doctorHeaders
Write-Host "   确认后状态=$($cvConfirmed.data.status), 确认人=$($cvConfirmed.data.confirmedByName)"
$closeBody = @{ handlingNote = "立即静脉降钾并复查血钾，30 分钟后复测" } | ConvertTo-Json
$cvClosed = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/e4/critical-values/$($cv.data.id)/close" `
    -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" -Body $closeBody
Write-Host "   关闭后状态=$($cvClosed.data.status), 处理措施=$($cvClosed.data.handlingNote)"
if ($cvClosed.data.status -ne "CLOSED") { Write-Host "   异常：危急值未关闭"; exit 1 }
$cvPending = Invoke-RestMethod -Uri "$BaseUrl/api/e4/critical-values?patientId=10001&status=PENDING" -Headers $doctorHeaders
Write-Host "   该患者待确认危急值数=$($cvPending.data.Count)"

Write-Host "35) E4 审计留痕"
$reviewAudits = Invoke-RestMethod -Uri "$BaseUrl/api/audit/logs?action=CDSS_REVIEW&limit=20" -Headers $adminHeaders
$cvAudits = Invoke-RestMethod -Uri "$BaseUrl/api/audit/logs?action=CRITICAL_VALUE_CLOSE&limit=20" -Headers $adminHeaders
Write-Host "   合理用药审查审计=$($reviewAudits.data.Count), 危急值关闭审计=$($cvAudits.data.Count)"
if ($reviewAudits.data.Count -lt 1 -or $cvAudits.data.Count -lt 1) { Write-Host "   异常：E4 审计缺失"; exit 1 }

Write-Host "36) 临床路径模板列表（E4-05）"
$pathways = Invoke-RestMethod -Uri "$BaseUrl/api/e4/pathways" -Headers $doctorHeaders
$capPathway = $pathways.data | Where-Object { $_.pathwayCode -eq "CAP" } | Select-Object -First 1
Write-Host "   路径数=$($pathways.data.Count), CAP 节点数=$($capPathway.nodes.Count)"
if ($pathways.data.Count -lt 5 -or $capPathway.nodes.Count -lt 5) {
    Write-Host "   异常：路径模板或节点缺失"; exit 1
}
$capNodes = $capPathway.nodes | Sort-Object sequence

Write-Host "37) 患者入径与重复入径拦截"
$enrollBody = @{ patientId = 10001; visitId = 20001 } | ConvertTo-Json
$capInstance = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/e4/pathways/$($capPathway.id)/enroll" `
    -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" -Body $enrollBody
Write-Host "   实例 id=$($capInstance.data.id), 状态=$($capInstance.data.status), 当前节点=$($capInstance.data.currentNodeName)"
try {
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/e4/pathways/$($capPathway.id)/enroll" `
        -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" -Body $enrollBody | Out-Null
    Write-Host "   异常：重复入径未拦截"; exit 1
} catch {
    Write-Host "   重复入径已拒绝：HTTP $([int]$_.Exception.Response.StatusCode)"
}

Write-Host "38) 跳序完成应被拒绝，按序完成后自动闭环"
try {
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/e4/pathways/instances/$($capInstance.data.id)/nodes/$($capNodes[2].id)/complete" `
        -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" `
        -Body (@{ note = "尝试跳序" } | ConvertTo-Json) | Out-Null
    Write-Host "   异常：跳序完成未被拒绝"; exit 1
} catch {
    Write-Host "   跳序已拒绝：HTTP $([int]$_.Exception.Response.StatusCode)"
}
$completed = $null
foreach ($node in $capNodes) {
    $completed = Invoke-RestMethod -Method Post `
        -Uri "$BaseUrl/api/e4/pathways/instances/$($capInstance.data.id)/nodes/$($node.id)/complete" `
        -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" `
        -Body (@{ note = "完成：$($node.nodeName)" } | ConvertTo-Json)
}
Write-Host "   最终状态=$($completed.data.status), 完成节点=$($completed.data.completedNodes)/$($completed.data.totalNodes)"
if ($completed.data.status -ne "COMPLETED") { Write-Host "   异常：路径未闭环"; exit 1 }

Write-Host "39) 路径变异与退出"
$hfPathway = $pathways.data | Where-Object { $_.pathwayCode -eq "HEART_FAILURE" } | Select-Object -First 1
$hfInstance = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/e4/pathways/$($hfPathway.id)/enroll" `
    -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" `
    -Body (@{ patientId = 10002; visitId = 20002 } | ConvertTo-Json)
$varianceBody = @{
    nodeId       = $hfPathway.nodes[0].id
    varianceType = "DELAY"
    reason       = "患者合并感染，抗心衰治疗延后一天"
} | ConvertTo-Json
$variance = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/e4/pathways/instances/$($hfInstance.data.id)/variances" `
    -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" -Body $varianceBody
Write-Host "   变异记录数=$($variance.data.variances.Count), 类型=$($variance.data.variances[0].varianceType)"
$exited = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/e4/pathways/instances/$($hfInstance.data.id)/exit" `
    -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" `
    -Body (@{ reason = "患者转院治疗" } | ConvertTo-Json)
Write-Host "   退出后状态=$($exited.data.status), 原因=$($exited.data.exitReason)"
if ($exited.data.status -ne "EXITED") { Write-Host "   异常：退出未生效"; exit 1 }

Write-Host "40) 临床路径执行率统计"
$pathwayStats = Invoke-RestMethod -Uri "$BaseUrl/api/e4/pathways/stats" -Headers $qcHeaders
$capStat = $pathwayStats.data | Where-Object { $_.pathwayCode -eq "CAP" } | Select-Object -First 1
$hfStat = $pathwayStats.data | Where-Object { $_.pathwayCode -eq "HEART_FAILURE" } | Select-Object -First 1
Write-Host "   CAP 入径=$($capStat.total), 完成=$($capStat.completed), 执行率=$($capStat.rate)%"
Write-Host "   心衰 入径=$($hfStat.total), 完成=$($hfStat.completed), 退出=$($hfStat.exited), 执行率=$($hfStat.rate)%"
if ([decimal]$capStat.rate -ne 100 -or [decimal]$hfStat.rate -ne 0) {
    Write-Host "   异常：执行率计算不符"; exit 1
}

Write-Host "41) 检验检查知识库检索（E4-03）"
$labItems = Invoke-RestMethod -Uri "$BaseUrl/api/e4/exam-items?itemType=LAB&limit=200" -Headers $doctorHeaders
$examItems = Invoke-RestMethod -Uri "$BaseUrl/api/e4/exam-items?itemType=EXAM&limit=200" -Headers $doctorHeaders
Write-Host "   检验项目=$($labItems.data.Count), 检查项目=$($examItems.data.Count)"
if ($labItems.data.Count -lt 12 -or $examItems.data.Count -lt 8) {
    Write-Host "   异常：检验检查知识库种子缺失"; exit 1
}
$kItem = $labItems.data | Where-Object { $_.itemCode -eq "K" } | Select-Object -First 1

Write-Host "42) 血钾知识库详情：标本/参考范围/危急值阈值"
$x = Invoke-RestMethod -Uri "$BaseUrl/api/e4/exam-items/K" -Headers $doctorHeaders
Write-Host "   标本=$($x.data.specimen), 参考范围=$($x.data.referenceRange)$($x.data.unit), 危急值=$($x.data.criticalLow)-$($x.data.criticalHigh)"
if ($x.data.specimen -ne "血清" -or $x.data.referenceRange -ne "3.5-5.5" -or
    [decimal]$x.data.criticalLow -ne [decimal]3 -or [decimal]$x.data.criticalHigh -ne [decimal]6) {
    Write-Host "   异常：血钾知识库字段不符"; exit 1
}

Write-Host "43) 检查适应症匹配"
$kIndication = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/e4/exam-items/indication-check" `
    -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" `
    -Body (@{ itemCode = "K"; diagnosisCodes = @("I10", "R50.9") } | ConvertTo-Json)
Write-Host "   血钾 + I10 适应症=$($kIndication.data.indicated), 匹配=$($kIndication.data.matchedDiagnoses -join ',')"
if (-not $kIndication.data.indicated) { Write-Host "   异常：血钾适应症未匹配"; exit 1 }
$ctIndication = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/e4/exam-items/indication-check" `
    -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" `
    -Body (@{ itemCode = "CT_CHEST"; diagnosisCodes = @("I10") } | ConvertTo-Json)
Write-Host "   胸部 CT + I10 适应症=$($ctIndication.data.indicated), 提示=$($ctIndication.data.message)"
if ($ctIndication.data.indicated) { Write-Host "   异常：胸部 CT 不应匹配高血压适应症"; exit 1 }

Write-Host "44) 危急值阈值判定"
$kHigh = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/e4/exam-items/critical-check" `
    -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" `
    -Body (@{ itemCode = "K"; value = 6.8 } | ConvertTo-Json)
Write-Host "   血钾 6.8 -> $($kHigh.data.severity)，$($kHigh.data.message)"
$kLow = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/e4/exam-items/critical-check" `
    -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" `
    -Body (@{ itemCode = "K"; value = 2.6 } | ConvertTo-Json)
Write-Host "   血钾 2.6 -> $($kLow.data.severity)，$($kLow.data.message)"
$ctCrit = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/e4/exam-items/critical-check" `
    -Headers $doctorHeaders -ContentType "application/json; charset=utf-8" `
    -Body (@{ itemCode = "CT_CHEST"; value = 5 } | ConvertTo-Json)
Write-Host "   胸部 CT -> $($ctCrit.data.severity)，$($ctCrit.data.message)"
if ($kHigh.data.severity -ne "HIGH" -or $kLow.data.severity -ne "LOW" -or
    $ctCrit.data.severity -ne "NORMAL") {
    Write-Host "   异常：危急值判定不符"; exit 1
}

Write-Host ""
Write-Host "冒烟测试通过：docId=$docId"
