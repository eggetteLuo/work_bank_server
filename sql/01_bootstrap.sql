-- 可选：用于作业提交时单独执行（需要 openGauss 管理员权限）
-- 作用：创建数据库 bank_system、用户 bank_admin、schema bank_schema

-- 下面语句建议先连接 openGauss 默认 postgres 数据库后执行。
-- 如果你直接使用 docker-compose.yml，默认管理员用户通常是 gaussdb。

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'bank_admin') THEN
        CREATE ROLE bank_admin LOGIN PASSWORD 'Gauss@123';
    END IF;
END $$;

-- CREATE DATABASE bank_system OWNER bank_admin;

-- 连接到 bank_system 后执行：
-- CREATE SCHEMA IF NOT EXISTS bank_schema AUTHORIZATION bank_admin;
-- ALTER ROLE bank_admin IN DATABASE bank_system SET search_path TO bank_schema, public;
-- GRANT ALL PRIVILEGES ON SCHEMA bank_schema TO bank_admin;
