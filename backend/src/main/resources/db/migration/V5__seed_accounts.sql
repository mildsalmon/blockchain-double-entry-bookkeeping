INSERT INTO accounts (code, name, category, is_system)
VALUES
    ('자산:암호화폐:ETH', 'ETH 보유 자산', 'ASSET', TRUE),
    ('자산:암호화폐:ERC20:*', 'ERC-20 토큰 자산(템플릿)', 'ASSET', TRUE),
    ('비용:가스비', '네트워크 가스비', 'EXPENSE', TRUE),
    ('비용:거래수수료', 'DEX 거래 수수료', 'EXPENSE', TRUE),
    ('수익:실현이익', '자산 처분 실현이익', 'REVENUE', TRUE),
    ('비용:실현손실', '자산 처분 실현손실', 'EXPENSE', TRUE),
    ('수익:에어드롭', '에어드롭 수익', 'REVENUE', TRUE)
ON CONFLICT (code) DO NOTHING;
