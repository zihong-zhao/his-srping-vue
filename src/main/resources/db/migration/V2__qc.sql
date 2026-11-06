-- E2 病历质控：规则元数据 + 缺陷清单

create table emr_qc_rule (
    id          bigint primary key,
    rule_code   varchar(64)  not null,
    name        varchar(128) not null,
    category    varchar(32)  not null,
    severity    varchar(16)  not null,
    doc_type    varchar(64),
    params_json text,
    enabled     boolean not null default true,
    created_at  timestamp not null default now()
);
create unique index uk_emr_qc_rule_code on emr_qc_rule (rule_code);

create table emr_qc_issue (
    id            bigint primary key,
    doc_id        bigint not null,
    rule_code     varchar(64) not null,
    rule_name     varchar(128) not null,
    category      varchar(32) not null,
    severity      varchar(16) not null,
    defect_desc   varchar(500) not null,
    status        varchar(16) not null,
    assignee_id   bigint,
    assignee_name varchar(64),
    closed_by     bigint,
    closed_at     timestamp,
    closed_note   varchar(500),
    created_at    timestamp not null default now()
);
create index idx_emr_qc_issue_doc on emr_qc_issue (doc_id, status);
create index idx_emr_qc_issue_status on emr_qc_issue (status, created_at desc);
