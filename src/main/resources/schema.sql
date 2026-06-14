-- =============================================
-- 银行存取款系统 - 课程大作业数据库对象
-- 默认工作在 bank_schema
-- =============================================

CREATE SCHEMA IF NOT EXISTS bank_schema;
SET search_path TO bank_schema, public;

DROP VIEW IF EXISTS bank_schema.v_card_info_zh;
DROP VIEW IF EXISTS bank_schema.v_user_info_zh;
DROP FUNCTION IF EXISTS bank_schema.fn_print_statement(VARCHAR, TIMESTAMP WITH TIME ZONE, TIMESTAMP WITH TIME ZONE);
DROP FUNCTION IF EXISTS bank_schema.fn_transfer(VARCHAR, VARCHAR, NUMERIC, VARCHAR);
DROP FUNCTION IF EXISTS bank_schema.fn_deposit_or_withdraw(VARCHAR, VARCHAR, NUMERIC, VARCHAR);
DROP FUNCTION IF EXISTS bank_schema.fn_generate_card_no();
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'bank_schema'
          AND c.relname = 'trade_info'
    ) THEN
        DROP TRIGGER IF EXISTS trg_trade_apply ON bank_schema.trade_info;
    END IF;
END $$;
DROP FUNCTION IF EXISTS bank_schema.trg_apply_trade();
DROP TABLE IF EXISTS bank_schema.trade_info;
DROP TABLE IF EXISTS bank_schema.card_info;
DROP TABLE IF EXISTS bank_schema.deposit;
DROP TABLE IF EXISTS bank_schema.user_info;

CREATE TABLE bank_schema.user_info (
    user_id BIGSERIAL PRIMARY KEY,
    user_name VARCHAR(50) NOT NULL,
    id_card_enc VARCHAR(255) NOT NULL,
    id_card_mask VARCHAR(18) NOT NULL,
    phone VARCHAR(11) NOT NULL,
    address VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_phone UNIQUE (phone),
    CONSTRAINT chk_id_card_mask CHECK (id_card_mask ~ '^[0-9]{17}[0-9Xx]$'),
    CONSTRAINT chk_phone CHECK (phone ~ '^[0-9]{11}$')
);

CREATE TABLE bank_schema.deposit (
    deposit_id BIGSERIAL PRIMARY KEY,
    deposit_name VARCHAR(50) NOT NULL,
    deposit_desc VARCHAR(255),
    CONSTRAINT chk_deposit_name_not_blank CHECK (length(trim(deposit_name)) > 0)
);

CREATE TABLE bank_schema.card_info (
    card_no VARCHAR(16) PRIMARY KEY,
    currency VARCHAR(10) NOT NULL DEFAULT 'CNY',
    deposit_id BIGINT NOT NULL,
    open_time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    open_amount NUMERIC(15,2) NOT NULL,
    balance NUMERIC(15,2) NOT NULL,
    withdraw_pwd_enc VARCHAR(255) NOT NULL,
    is_lost BOOLEAN NOT NULL DEFAULT FALSE,
    user_id BIGINT NOT NULL,
    CONSTRAINT fk_card_user FOREIGN KEY (user_id) REFERENCES bank_schema.user_info(user_id),
    CONSTRAINT fk_card_deposit FOREIGN KEY (deposit_id) REFERENCES bank_schema.deposit(deposit_id),
    CONSTRAINT chk_card_no CHECK (card_no ~ '^10103576[0-9]{8}$'),
    CONSTRAINT chk_open_amount CHECK (open_amount >= 1.00),
    CONSTRAINT chk_balance CHECK (balance >= 1.00)
);

CREATE TABLE bank_schema.trade_info (
    trade_id BIGSERIAL PRIMARY KEY,
    trade_time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    trade_type VARCHAR(20) NOT NULL,
    card_no VARCHAR(16) NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    remark VARCHAR(255),
    CONSTRAINT fk_trade_card FOREIGN KEY (card_no) REFERENCES bank_schema.card_info(card_no),
    CONSTRAINT chk_trade_type CHECK (trade_type IN ('存入', '支取')),
    CONSTRAINT chk_trade_amount CHECK (amount > 0)
);

CREATE INDEX idx_card_user_id ON bank_schema.card_info(user_id);
CREATE INDEX idx_trade_card_no ON bank_schema.trade_info(card_no);
CREATE INDEX idx_trade_time ON bank_schema.trade_info(trade_time);

CREATE OR REPLACE VIEW bank_schema.v_user_info_zh AS
SELECT
    u.user_id AS "客户编号",
    u.user_name AS "客户姓名",
    u.id_card_mask AS "身份证号",
    u.phone AS "联系电话",
    u.address AS "联系地址",
    u.created_at AS "创建时间"
FROM bank_schema.user_info u;

CREATE OR REPLACE VIEW bank_schema.v_card_info_zh AS
SELECT
    c.card_no AS "卡号",
    u.user_name AS "客户姓名",
    c.currency AS "币种",
    d.deposit_name AS "存款类型",
    c.open_time AS "开卡时间",
    c.open_amount AS "开卡金额",
    c.balance AS "账户余额",
    CASE WHEN c.is_lost THEN '是' ELSE '否' END AS "是否挂失"
FROM bank_schema.card_info c
JOIN bank_schema.user_info u ON u.user_id = c.user_id
JOIN bank_schema.deposit d ON d.deposit_id = c.deposit_id;

CREATE OR REPLACE FUNCTION bank_schema.fn_generate_card_no()
RETURNS VARCHAR(16)
LANGUAGE plpgsql
AS $$
DECLARE
    v_no VARCHAR(16);
BEGIN
    LOOP
        v_no := '10103576' || lpad((floor(random() * 100000000))::TEXT, 8, '0');
        EXIT WHEN NOT EXISTS (SELECT 1 FROM bank_schema.card_info WHERE card_no = v_no);
    END LOOP;
    RETURN v_no;
END;
$$;

CREATE OR REPLACE FUNCTION bank_schema.trg_apply_trade()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_balance NUMERIC(15,2);
    v_lost BOOLEAN;
BEGIN
    SELECT balance, is_lost INTO v_balance, v_lost
    FROM bank_schema.card_info
    WHERE card_no = NEW.card_no
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION '卡号不存在: %', NEW.card_no;
    END IF;

    IF v_lost THEN
        RAISE EXCEPTION '卡号已挂失，无法交易: %', NEW.card_no;
    END IF;

    IF NEW.trade_type = '存入' THEN
        UPDATE bank_schema.card_info
        SET balance = balance + NEW.amount
        WHERE card_no = NEW.card_no;
    ELSIF NEW.trade_type = '支取' THEN
        IF v_balance < NEW.amount THEN
            RAISE EXCEPTION '余额不足，当前余额: %, 取款金额: %', v_balance, NEW.amount;
        END IF;
        UPDATE bank_schema.card_info
        SET balance = balance - NEW.amount
        WHERE card_no = NEW.card_no;
    ELSE
        RAISE EXCEPTION '不支持的交易类型: %', NEW.trade_type;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_trade_apply
BEFORE INSERT ON bank_schema.trade_info
FOR EACH ROW
EXECUTE FUNCTION bank_schema.trg_apply_trade();

CREATE OR REPLACE FUNCTION bank_schema.fn_deposit_or_withdraw(
    p_card_no VARCHAR,
    p_trade_type VARCHAR,
    p_amount NUMERIC,
    p_remark VARCHAR DEFAULT NULL
)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    v_trade_id BIGINT;
BEGIN
    IF p_amount IS NULL OR p_amount <= 0 THEN
        RAISE EXCEPTION '交易金额必须大于0';
    END IF;

    INSERT INTO bank_schema.trade_info(trade_type, card_no, amount, remark)
    VALUES (p_trade_type, p_card_no, p_amount, p_remark)
    RETURNING trade_id INTO v_trade_id;

    RETURN v_trade_id;
END;
$$;

CREATE OR REPLACE FUNCTION bank_schema.fn_transfer(
    p_from_card_no VARCHAR,
    p_to_card_no VARCHAR,
    p_amount NUMERIC,
    p_remark VARCHAR DEFAULT NULL
)
RETURNS VARCHAR
LANGUAGE plpgsql
AS $$
DECLARE
    v_from_balance NUMERIC(15,2);
    v_from_lost BOOLEAN;
    v_to_lost BOOLEAN;
BEGIN
    IF p_from_card_no = p_to_card_no THEN
        RAISE EXCEPTION '转出卡与转入卡不能相同';
    END IF;

    IF p_amount IS NULL OR p_amount <= 0 THEN
        RAISE EXCEPTION '转账金额必须大于0';
    END IF;

    SELECT balance, is_lost INTO v_from_balance, v_from_lost
    FROM bank_schema.card_info
    WHERE card_no = p_from_card_no
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION '转出卡号不存在: %', p_from_card_no;
    END IF;

    SELECT is_lost INTO v_to_lost
    FROM bank_schema.card_info
    WHERE card_no = p_to_card_no
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION '转入卡号不存在: %', p_to_card_no;
    END IF;

    IF v_from_lost OR v_to_lost THEN
        RAISE EXCEPTION '挂失账户不可转账';
    END IF;

    IF v_from_balance < p_amount THEN
        RAISE EXCEPTION '余额不足，当前余额: %, 转账金额: %', v_from_balance, p_amount;
    END IF;

    INSERT INTO bank_schema.trade_info(trade_type, card_no, amount, remark)
    VALUES ('支取', p_from_card_no, p_amount, COALESCE(p_remark, '转账转出'));

    INSERT INTO bank_schema.trade_info(trade_type, card_no, amount, remark)
    VALUES ('存入', p_to_card_no, p_amount, COALESCE(p_remark, '转账转入'));

    RETURN '转账成功';
END;
$$;

CREATE OR REPLACE FUNCTION bank_schema.fn_print_statement(
    p_card_no VARCHAR,
    p_start TIMESTAMP WITH TIME ZONE,
    p_end TIMESTAMP WITH TIME ZONE
)
RETURNS TABLE(
    "交易时间" TIMESTAMP WITH TIME ZONE,
    "交易类型" VARCHAR,
    "交易金额" NUMERIC,
    "备注" VARCHAR,
    "卡号" VARCHAR
)
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM bank_schema.card_info WHERE card_no = p_card_no) THEN
        RAISE EXCEPTION '卡号不存在: %', p_card_no;
    END IF;

    RETURN QUERY
    SELECT t.trade_time, t.trade_type, t.amount, t.remark, t.card_no
    FROM bank_schema.trade_info t
    WHERE t.card_no = p_card_no
      AND t.trade_time >= p_start
      AND t.trade_time <= p_end
    ORDER BY t.trade_time DESC;
END;
$$;
