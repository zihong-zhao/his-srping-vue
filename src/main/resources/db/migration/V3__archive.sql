-- E7 归档与封存：归档哈希、封存标记、封存/解封留痕

alter table emr_document add column archived_by  bigint;
alter table emr_document add column archive_hash varchar(64);
alter table emr_document add column sealed       boolean not null default false;

create table emr_seal (
    id            bigint primary key,
    doc_id        bigint not null,
    action        varchar(16) not null,
    reason        varchar(500) not null,
    operator_id   bigint not null,
    operator_name varchar(64) not null,
    created_at    timestamp not null default now()
);
create index idx_emr_seal_doc on emr_seal (doc_id, created_at desc);
