-- HIS E1 + E6 最小闭环基线
-- 约定：主键 bigint（雪花 ID 由应用生成），金额/状态等业务约束由应用层保证

create table sys_user (
    id            bigint primary key,
    username      varchar(64)  not null,
    password_hash varchar(128) not null,
    display_name  varchar(64)  not null,
    dept_id       bigint,
    role          varchar(32)  not null,
    enabled       boolean      not null default true,
    created_at    timestamp    not null default now()
);
create unique index uk_sys_user_username on sys_user (username);

create table emr_template (
    id          bigint primary key,
    doc_type    varchar(64) not null,
    name        varchar(128) not null,
    scope       varchar(32) not null,
    dept_id     bigint,
    version     integer not null,
    schema_json text not null,
    enabled     boolean not null default true,
    created_at  timestamp not null default now()
);
create index idx_emr_template_doc_type on emr_template (doc_type, enabled);

create table emr_document (
    id               bigint primary key,
    patient_id       bigint not null,
    visit_id         bigint,
    doc_type         varchar(64) not null,
    template_id      bigint not null,
    template_version integer not null,
    title            varchar(200) not null,
    status           varchar(32) not null,
    author_id        bigint not null,
    author_name      varchar(64) not null,
    content_json     text not null,
    current_version  integer not null,
    signed_at        timestamp,
    archived_at      timestamp,
    created_at       timestamp not null default now(),
    updated_at       timestamp not null default now(),
    updated_by       bigint,
    version          integer not null default 1
);
create index idx_emr_document_patient on emr_document (patient_id, created_at desc);
create index idx_emr_document_visit on emr_document (visit_id);
create index idx_emr_document_author on emr_document (author_id, status);

create table emr_version (
    id              bigint primary key,
    doc_id          bigint not null,
    version_no      integer not null,
    content_json    text not null,
    change_summary  varchar(500),
    created_by      bigint not null,
    created_by_name varchar(64) not null,
    created_at      timestamp not null default now()
);
create unique index uk_emr_version_doc_no on emr_version (doc_id, version_no);

create table emr_signature (
    id           bigint primary key,
    doc_id       bigint not null,
    version_no   integer not null,
    signer_id    bigint not null,
    signer_name  varchar(64) not null,
    sign_type    varchar(32) not null,
    cert_sn      varchar(64) not null,
    content_hash varchar(128) not null,
    signature    varchar(256) not null,
    signed_at    timestamp not null
);
create index idx_emr_signature_doc on emr_signature (doc_id, version_no);

create table emr_access_log (
    id            bigint primary key,
    doc_id        bigint not null,
    operator_id   bigint not null,
    operator_name varchar(64) not null,
    action        varchar(32) not null,
    reason        varchar(200),
    ip            varchar(64),
    created_at    timestamp not null default now()
);
create index idx_emr_access_log_doc on emr_access_log (doc_id, created_at desc);

create table audit_log (
    id            bigint primary key,
    operator_id   bigint,
    operator_name varchar(64),
    action        varchar(64) not null,
    resource_type varchar(64) not null,
    request_uri   varchar(300),
    http_method   varchar(16),
    ip            varchar(64),
    success       boolean not null,
    error_msg     varchar(500),
    duration_ms   bigint,
    created_at    timestamp not null default now()
);
create index idx_audit_log_created on audit_log (created_at desc);
create index idx_audit_log_operator on audit_log (operator_id, created_at desc);
