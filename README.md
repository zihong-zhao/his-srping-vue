# HIS 医院信息系统（电子病历 4 级最小实现）

面向三级综合医院的 HIS 核心系统，按《电子病历应用水平分级评价标准》**4 级**（全院信息共享 + 中级医疗决策支持）设计。本仓库当前交付的是 **E1–E7 电子病历评价能力的最小可用实现 + 配套 Web 前端**，覆盖结构化病历、质控、CDR、临床决策支持、闭环、签名审计、归档病案管理全链路。

- 架构形态：B/S 前后端分离 + **模块化单体**（不拆微服务，理由见 [docs/01-架构方案.md](docs/01-架构方案.md) 第 3 节）
- 技术决策：Spring Boot 3 + Java 21（[docs/03-技术栈对比.md](docs/03-技术栈对比.md)）、PostgreSQL 16（[docs/04-数据库选型.md](docs/04-数据库选型.md)）

---

## 1. 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 21 · Spring Boot 3.3 · Spring Security（JWT）· MyBatis-Plus 3.5 · Flyway · Lombok · SpringDoc OpenAPI |
| 数据库 | PostgreSQL 16（`JSONB` 存结构化病历/报文，按时间分区，RLS 兜底数据权限） |
| 前端 | Vue 3 · Vite 6 · Element Plus · Vue Router 4 · Axios |
| 设计约束 | 统一响应体 `ApiResponse<T>`、写操作 `@Audited` 全留痕、按领域分包、模块独立 Flyway 脚本 |

## 2. 已实现的评价能力（E1–E7）

| 能力域 | 实现内容 |
| --- | --- |
| E1/E6 结构化病历与签名审计 | 病历创建/编辑（草稿）、提交、电子签名、版本留痕与差异对比、完整性校验，操作审计切面 |
| E2 病历质控 | 规则引擎 + 缺陷清单，运行质控、缺陷整改关闭 |
| E3 CDR 患者全流程视图 | 按时间轴聚合患者的病历、质控、闭环、危急值等事件 |
| E4 临床决策支持 | 药品知识库与合理用药审查（提醒可忽略留痕）、危急值上报→确认→处理闭环、临床路径（入径/节点执行/变异/退出/执行率）、检验检查知识库（适应症校验、危急值阈值判定） |
| E5 闭环管理 | 医嘱、处方、检验、检查四条闭环，节点推进与闭环率统计 |
| E7 归档与病案管理 | 病历归档、封存/解封、借阅与复印申请审批、病案首页（ICD-10/ICD-9 检索）、核对确认 |

迁移脚本 `V1–V9`，启动时自动建表并注入演示种子数据（药品、路径、检验检查项目、模板等）。

## 3. 项目结构

```
├── docs/                # 架构、功能需求、技术选型、数据库选型、评价模块设计
├── src/main/java/com/his/
│   ├── auth/            # 登录与 JWT
│   ├── security/        # 鉴权与当前用户
│   ├── audit/           # @Audited 操作审计
│   ├── emr/             # E1/E2/E6：病历、质控、版本、签名
│   ├── cdr/             # E3：患者时间轴
│   ├── cdss/            # E4：合理用药、危急值、临床路径、检验检查知识库
│   ├── loop/            # E5：四条闭环
│   └── bootstrap/       # 种子数据
├── src/main/resources/db/migration/   # Flyway V1–V9
├── frontend/            # Vue 3 工作站（登录、工作台、病历/质控/CDR/病案/闭环/CDSS/审计）
├── scripts/
│   ├── init-db.sql      # 建库建用户
│   └── smoke-test.ps1   # 44 步端到端冒烟
└── pom.xml
```

前端页面与后端接口一一对应：病历列表/编辑器、质控台、CDR 时间轴、病案借阅复印与首页、闭环台账、合理用药审查、危急值闭环、检验检查知识库、临床路径、审计日志（仅 ADMIN）。

## 4. 快速开始

### 4.1 环境要求

- JDK 21、Maven 3.8+、Node.js 18+、PostgreSQL 16

### 4.2 初始化数据库

```powershell
psql -U postgres -f scripts/init-db.sql   # 创建 his 用户（密码 his）与 his 库
```

可用环境变量覆盖数据源：`HIS_DB_URL`、`HIS_DB_USER`、`HIS_DB_PASSWORD`。

### 4.3 启动后端（8080）

```powershell
mvn -DskipTests package
java -jar target/his-emr-0.1.0.jar
```

Flyway 自动迁移并注入种子数据。接口文档：<http://localhost:8080/swagger-ui.html>

### 4.4 启动前端（5173）

```powershell
cd frontend
npm install
npm run dev    # /api 已代理到 8080
```

访问 <http://localhost:5173>，演示账号：

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| admin | admin123 | 管理员（审计、统计、全部写操作） |
| doctor1 | doctor123 | 医生（病历、闭环、CDSS 写操作） |
| qc1 | qc123 | 质控（质控规则、统计接口） |

### 4.5 端到端冒烟

```powershell
scripts/smoke-test.ps1   # 44 步，覆盖 E1–E7 关键链路
```

## 5. 文档索引

| 文档 | 内容 |
| --- | --- |
| [docs/01-架构方案.md](docs/01-架构方案.md) | 建设目标、模块化单体取舍、总体/部署架构、非功能指标、等保三级 |
| [docs/02-功能需求文档.md](docs/02-功能需求文档.md) | 角色权限、核心流程、16 个模块功能清单、4 级能力映射 |
| [docs/03-技术栈对比.md](docs/03-技术栈对比.md) | Spring Boot vs FastAPI vs Django 选型决策 |
| [docs/04-数据库选型.md](docs/04-数据库选型.md) | PostgreSQL vs MySQL vs 信创方案 |
| [docs/05-电子病历评价模块设计.md](docs/05-电子病历评价模块设计.md) | E1–E7 详细设计、评级自评清单、最小可用集 |

## 6. 后续路线（对应 docs 里程碑）

当前为 M3–M4 的评级能力底座。按 [docs/01-架构方案.md](docs/01-架构方案.md) 第 9 节的规划，后续依次补齐：M0 主数据/患者/挂号、M1 门诊闭环、M2 住院闭环、M5 评级准备（真实使用数据与制度文档）。
