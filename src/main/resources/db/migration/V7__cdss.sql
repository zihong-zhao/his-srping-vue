-- E4 临床决策支持（最小版）：药品知识库、相互作用、规则、提醒留痕、危急值闭环

create table cdss_rule (
    id          bigint primary key,
    rule_code   varchar(64)  not null,
    name        varchar(200) not null,
    category    varchar(32)  not null,
    severity    varchar(16)  not null,
    description varchar(500),
    enabled     boolean not null default true,
    created_at  timestamp not null default now()
);
create unique index uk_cdss_rule_code on cdss_rule (rule_code);

create table cdss_drug (
    id                    bigint primary key,
    drug_code             varchar(64)  not null,
    drug_name             varchar(200) not null,
    category              varchar(64)  not null,
    indications_json      text,
    contraindications_json text,
    adult_dose            varchar(200),
    child_dose_mg_per_kg  numeric(10, 2),
    max_daily_dose_mg     numeric(12, 2),
    renal_adjust_note     varchar(500),
    pregnancy_note        varchar(500),
    enabled               boolean not null default true,
    created_at            timestamp not null default now()
);
create unique index uk_cdss_drug_code on cdss_drug (drug_code);

create table cdss_drug_interaction (
    id           bigint primary key,
    drug_a_code  varchar(64) not null,
    drug_b_code  varchar(64) not null,
    severity     varchar(16) not null,
    mechanism    varchar(500),
    enabled      boolean not null default true,
    created_at   timestamp not null default now()
);
create unique index uk_cdss_interaction on cdss_drug_interaction (drug_a_code, drug_b_code);

create table cdss_alert_log (
    id               bigint primary key,
    patient_id       bigint not null,
    visit_id         bigint,
    biz_type         varchar(32) not null,
    biz_no           varchar(64),
    rule_code        varchar(64) not null,
    severity         varchar(16) not null,
    message          varchar(500) not null,
    decision         varchar(16) not null,
    override_reason  varchar(500),
    operator_id      bigint not null,
    operator_name    varchar(64) not null,
    created_at       timestamp not null default now()
);
create index idx_cdss_alert_patient on cdss_alert_log (patient_id, created_at desc);
create index idx_cdss_alert_biz on cdss_alert_log (biz_type, biz_no);

create table cdss_critical_value (
    id                bigint primary key,
    patient_id        bigint not null,
    visit_id          bigint,
    item_code         varchar(64) not null,
    item_name         varchar(200) not null,
    result_value      varchar(100) not null,
    unit              varchar(32),
    reference_range   varchar(100),
    severity          varchar(16) not null,
    status            varchar(16) not null,
    reported_by       bigint not null,
    reported_by_name  varchar(64) not null,
    reported_at       timestamp not null default now(),
    confirmed_by      bigint,
    confirmed_by_name varchar(64),
    confirmed_at      timestamp,
    handling_note     varchar(500),
    closed_at         timestamp,
    created_at        timestamp not null default now(),
    updated_at        timestamp not null default now(),
    version           integer not null default 1
);
create index idx_cdss_cv_patient on cdss_critical_value (patient_id, reported_at desc);
create index idx_cdss_cv_status on cdss_critical_value (status, reported_at);
