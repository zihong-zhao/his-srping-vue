-- E4-05 临床路径：模板、节点、入径实例、节点执行与变异记录

create table clinical_pathway (
    id             bigint primary key,
    pathway_code   varchar(64)  not null,
    name           varchar(200) not null,
    diagnosis_code varchar(32)  not null,
    department     varchar(64),
    version        integer not null default 1,
    standard_days  integer not null,
    description    varchar(500),
    enabled        boolean not null default true,
    created_at     timestamp not null default now()
);
create unique index uk_clinical_pathway_code on clinical_pathway (pathway_code);

create table pathway_node (
    id          bigint primary key,
    pathway_id  bigint not null,
    sequence    integer not null,
    node_code   varchar(64)  not null,
    node_name   varchar(200) not null,
    day_offset  integer not null default 0,
    required    boolean not null default true,
    description varchar(500)
);
create unique index uk_pathway_node_seq on pathway_node (pathway_id, sequence);

create table pathway_instance (
    id              bigint primary key,
    pathway_id      bigint not null,
    patient_id      bigint not null,
    visit_id        bigint,
    status          varchar(16) not null,
    current_sequence integer not null,
    started_by      bigint not null,
    started_by_name varchar(64) not null,
    started_at      timestamp not null default now(),
    ended_at        timestamp,
    exit_reason     varchar(500),
    created_at      timestamp not null default now(),
    updated_at      timestamp not null default now(),
    version         integer not null default 1
);
create index idx_pathway_instance_patient on pathway_instance (patient_id, started_at desc);
create index idx_pathway_instance_pathway on pathway_instance (pathway_id, status);

create table pathway_node_record (
    id                bigint primary key,
    instance_id       bigint not null,
    node_id           bigint not null,
    node_code         varchar(64) not null,
    node_name         varchar(200) not null,
    sequence          integer not null,
    completed_by      bigint not null,
    completed_by_name varchar(64) not null,
    completed_at      timestamp not null default now(),
    note              varchar(500)
);
create unique index uk_pathway_node_record on pathway_node_record (instance_id, node_id);

create table pathway_variance (
    id                bigint primary key,
    instance_id       bigint not null,
    node_id           bigint,
    node_code         varchar(64),
    variance_type     varchar(16) not null,
    reason            varchar(500) not null,
    recorded_by       bigint not null,
    recorded_by_name  varchar(64) not null,
    recorded_at       timestamp not null default now()
);
create index idx_pathway_variance_instance on pathway_variance (instance_id, recorded_at desc);
