-- E7-04 病案借阅与复印：申请 -> 审批 -> 借阅/复印 -> 留痕

create table emr_borrow_request (
    id             bigint primary key,
    doc_id         bigint not null,
    applicant_id   bigint not null,
    applicant_name varchar(64) not null,
    reason         varchar(500) not null,
    days           integer not null,
    status         varchar(16) not null,
    approver_id    bigint,
    approver_name  varchar(64),
    approve_note   varchar(500),
    approved_at    timestamp,
    start_at       timestamp,
    due_at         timestamp,
    returned_at    timestamp,
    created_at     timestamp not null default now()
);
create index idx_emr_borrow_doc on emr_borrow_request (doc_id, status);
create index idx_emr_borrow_applicant on emr_borrow_request (applicant_id, status);
create index idx_emr_borrow_status on emr_borrow_request (status, created_at desc);

create table emr_copy_request (
    id                bigint primary key,
    doc_id            bigint not null,
    applicant_id      bigint not null,
    applicant_name    varchar(64) not null,
    purpose           varchar(500) not null,
    copies            integer not null,
    status            varchar(16) not null,
    approver_id       bigint,
    approver_name     varchar(64),
    approve_note      varchar(500),
    approved_at       timestamp,
    completed_by      bigint,
    completed_by_name varchar(64),
    completed_at      timestamp,
    created_at        timestamp not null default now()
);
create index idx_emr_copy_doc on emr_copy_request (doc_id, status);
create index idx_emr_copy_status on emr_copy_request (status, created_at desc);
