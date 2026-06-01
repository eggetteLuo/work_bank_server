SET search_path TO bank_schema, public;

INSERT INTO bank_schema.deposit(deposit_name, deposit_desc)
VALUES ('活期储蓄', '可随时存取')
ON CONFLICT DO NOTHING;

INSERT INTO bank_schema.user_info(user_name, id_card_enc, id_card_mask, phone, address)
VALUES
('张三', 'MTIzNDU2Nzg5MDEyMzQ1Njc4', '123456789012345678', '13800138000', '上海市浦东新区'),
('李四', 'MzIxMDk4NzY1NDMyMTA5ODc2', '321098765432109876', '13900139000', '杭州市西湖区')
ON CONFLICT (phone) DO NOTHING;

DO $$
DECLARE
    v_card1 VARCHAR(16);
    v_card2 VARCHAR(16);
BEGIN
    SELECT card_no INTO v_card1 FROM bank_schema.card_info WHERE user_id = 1 LIMIT 1;
    IF v_card1 IS NULL THEN
        INSERT INTO bank_schema.card_info(card_no, currency, deposit_id, open_amount, balance, withdraw_pwd_enc, is_lost, user_id)
        VALUES (bank_schema.fn_generate_card_no(), 'CNY', 1, 1000.00, 1000.00, 'MTIzNDU2', FALSE, 1);
    END IF;

    SELECT card_no INTO v_card2 FROM bank_schema.card_info WHERE user_id = 2 LIMIT 1;
    IF v_card2 IS NULL THEN
        INSERT INTO bank_schema.card_info(card_no, currency, deposit_id, open_amount, balance, withdraw_pwd_enc, is_lost, user_id)
        VALUES (bank_schema.fn_generate_card_no(), 'CNY', 1, 1500.00, 1500.00, 'NjU0MzIx', FALSE, 2);
    END IF;
END $$;
