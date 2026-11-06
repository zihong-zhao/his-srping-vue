-- E5 四条闭环：医嘱、处方、检验、检查
-- 统一建模：业务单号 + 节点事件，节点按序推进，末节点到达即闭环

create table loop_instance (
    id             bigint primary key,
    biz_no         varchar(64) not null,
    loop_type      varchar(32) not null,
    patient_id     bigint not null,
    visit_id       bigint,
    title          varchar(200) not null,
    status         varchar(16) not null,
    current_node   varchar(32) not null,
    started_by     bigint not null,
    started_by_name varchar(64) not null,
    started_at     timestamp not null default now(),
    closed_at      timestamp,
    created_at     timestamp not null default now(),
    updated_at     timestamp not null default now(),
    version        integer not null default 1
);
create unique index uk_loop_instance_biz on loop_instance (biz_no);
create index idx_loop_instance_patient on loop_instance (patient_id, started_at desc);
create index idx_loop_instance_stats on loop_instance (loop_type, started_at);

create table loop_node_event (
    id            bigint primary key,
    loop_id       bigint not null,
    node_code     varchar(32) not null,
    node_name     varchar(64) not null,
    sequence      integer not null,
    operator_id   bigint not null,
    operator_name varchar(64) not null,
    occurred_at   timestamp not null default now(),
    payload_json  text
);
create index idx_loop_node_event_loop on loop_node_event (loop_id, sequence);
