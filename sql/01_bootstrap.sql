-- 可选：用于作业提交时单独执行（需要超级用户权限）
-- 作用：创建数据库 bankdb、用户 bank_admin、schema bank_schema

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'bank_admin') THEN
        CREATE ROLE bank_admin LOGIN PASSWORD 'root_password';
    END IF;
END $$;

-- 下面语句需要在 postgres 库执行
-- CREATE DATABASE bankdb OWNER bank_admin;

-- 连接到 bankdb 后执行：
-- CREATE SCHEMA IF NOT EXISTS bank_schema AUTHORIZATION bank_admin;
-- ALTER ROLE bank_admin IN DATABASE bankdb SET search_path TO bank_schema, public;
-- GRANT ALL PRIVILEGES ON SCHEMA bank_schema TO bank_admin;
