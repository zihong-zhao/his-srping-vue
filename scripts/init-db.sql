-- 首次建库（需用超级用户执行）：psql -U postgres -f scripts/init-db.sql
-- 若用户或数据库已存在可跳过对应语句

CREATE USER his WITH PASSWORD 'his';
CREATE DATABASE his OWNER his ENCODING 'UTF8';
