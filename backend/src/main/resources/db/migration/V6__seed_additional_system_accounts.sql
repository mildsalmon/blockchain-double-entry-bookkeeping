INSERT INTO accounts (code, name, category, is_system)
VALUES
    ('수익:미지정수입', '출처 미지정 수입', 'REVENUE', TRUE),
    ('자산:외부', '외부 상대 계정', 'ASSET', TRUE)
ON CONFLICT (code) DO NOTHING;

