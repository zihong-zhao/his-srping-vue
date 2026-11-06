-- E4-03 检验检查知识库：项目适应症、标本要求、参考范围、危急值阈值

create table cdss_exam_item (
    id                  bigint primary key,
    item_code           varchar(64)  not null,
    item_name           varchar(200) not null,
    item_type           varchar(16)  not null,
    category            varchar(64),
    specimen            varchar(64),
    precaution          varchar(500),
    indications_json    text,
    reference_range     varchar(100),
    unit                varchar(32),
    critical_low        numeric(12, 2),
    critical_high       numeric(12, 2),
    turnaround_minutes  integer,
    enabled             boolean not null default true,
    created_at          timestamp not null default now()
);
create unique index uk_cdss_exam_item_code on cdss_exam_item (item_code);
create index idx_cdss_exam_item_type on cdss_exam_item (item_type, item_code);
