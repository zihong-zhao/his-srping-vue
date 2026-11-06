-- E7-01 病案首页 + E7-05 ICD 编码字典

create table emr_icd_code (
    id         bigint primary key,
    code       varchar(32)  not null,
    name       varchar(200) not null,
    type       varchar(16)  not null,
    enabled    boolean not null default true,
    created_at timestamp not null default now()
);
create unique index uk_emr_icd_code on emr_icd_code (type, code);
create index idx_emr_icd_name on emr_icd_code (name);

create table emr_front_page (
    id                 bigint primary key,
    patient_id         bigint not null,
    visit_id           bigint not null,
    doc_id             bigint,
    inpatient_no       varchar(64),
    admission_date     timestamp,
    discharge_date     timestamp,
    admission_dept     varchar(100),
    discharge_dept     varchar(100),
    discharge_way      varchar(32) not null,
    anesthesia_type    varchar(64),
    attending_doctor   varchar(64),
    diagnoses_json     text not null,
    surgeries_json     text,
    cost_json          text,
    status             varchar(16) not null,
    verified_by        bigint,
    verified_by_name   varchar(64),
    verified_at        timestamp,
    verify_issues_json text,
    created_by         bigint not null,
    created_by_name    varchar(64) not null,
    created_at         timestamp not null default now(),
    updated_at         timestamp not null default now(),
    version            integer not null default 1
);
create unique index uk_emr_front_page_visit on emr_front_page (visit_id);
create index idx_emr_front_page_patient on emr_front_page (patient_id, created_at desc);
