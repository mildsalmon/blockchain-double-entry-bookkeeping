'use client';

import { useState } from 'react';
import type { WalletCreatePayload, WalletMode } from '@/types/wallet';

interface WalletInputProps {
  onSubmit: (payload: WalletCreatePayload) => Promise<void>;
}

const ETH_ADDRESS_REGEX = /^0x[a-fA-F0-9]{40}$/;
const TOKEN_ADDRESS_REGEX = /^0x[a-fA-F0-9]{40}$/;

export function WalletInput({ onSubmit }: WalletInputProps) {
  const [address, setAddress] = useState('');
  const [mode, setMode] = useState<WalletMode>('FULL');
  const [startBlock, setStartBlock] = useState('');
  const [cutoffBlock, setCutoffBlock] = useState('');
  const [trackedTokens, setTrackedTokens] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();

    if (!ETH_ADDRESS_REGEX.test(address)) {
      setError('Ethereum 주소 형식이 올바르지 않습니다.');
      return;
    }

    const payload: WalletCreatePayload = {
      address,
      mode
    };

    if (mode === 'FULL') {
      const parsedStartBlock =
        startBlock.trim() === '' ? undefined : Number.parseInt(startBlock.trim(), 10);
      if (parsedStartBlock !== undefined && (!Number.isFinite(parsedStartBlock) || parsedStartBlock < 0)) {
        setError('시작 블록은 0 이상의 정수여야 합니다.');
        return;
      }
      payload.startBlock = parsedStartBlock;
    } else {
      const parsedCutoffBlock =
        cutoffBlock.trim() === '' ? undefined : Number.parseInt(cutoffBlock.trim(), 10);
      if (parsedCutoffBlock === undefined || !Number.isFinite(parsedCutoffBlock) || parsedCutoffBlock < 0) {
        setError('컷오프 블록은 0 이상의 정수로 입력해야 합니다.');
        return;
      }

      const parsedTokens = trackedTokens
        .split(/[,\n ]+/)
        .map((token) => token.trim())
        .filter((token) => token.length > 0);
      const invalidToken = parsedTokens.find((token) => !TOKEN_ADDRESS_REGEX.test(token));
      if (invalidToken) {
        setError(`잘못된 토큰 주소 형식입니다: ${invalidToken}`);
        return;
      }

      payload.cutoffBlock = parsedCutoffBlock;
      payload.trackedTokens = parsedTokens;
    }

    setError(null);
    setLoading(true);
    try {
      await onSubmit(payload);
      setAddress('');
      setMode('FULL');
      setStartBlock('');
      setCutoffBlock('');
      setTrackedTokens('');
    } catch (e) {
      setError(e instanceof Error ? e.message : '지갑 등록 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <form className="rounded-xl border border-slate-200 bg-white/90 p-5" onSubmit={handleSubmit}>
      <label className="mb-2 block text-sm font-medium text-slate-600" htmlFor="wallet-address">
        Ethereum 지갑 주소
      </label>
      <label className="mb-2 mt-4 block text-sm font-medium text-slate-600" htmlFor="wallet-mode">
        동기화 모드
      </label>
      <div className="flex flex-col gap-3 md:flex-row md:items-start">
        <input
          id="wallet-address"
          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
          placeholder="0x..."
          value={address}
          onChange={(e) => setAddress(e.target.value)}
        />
        <select
          id="wallet-mode"
          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm md:max-w-xs"
          value={mode}
          onChange={(e) => setMode(e.target.value as WalletMode)}
        >
          <option value="FULL">기본 전체 동기화</option>
          <option value="BALANCE_FLOW_CUTOFF">컷오프 잔고 + 이후 흐름</option>
        </select>
        <button
          type="submit"
          disabled={loading}
          className="rounded-lg bg-ink px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
        >
          {loading ? '등록 중...' : '등록'}
        </button>
      </div>

      {mode === 'FULL' && (
        <>
          <label className="mb-2 mt-4 block text-sm font-medium text-slate-600" htmlFor="wallet-start-block">
            시작 블록 (선택)
          </label>
          <input
            id="wallet-start-block"
            type="number"
            min={0}
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm md:max-w-xs"
            placeholder="예: 19000000"
            value={startBlock}
            onChange={(e) => setStartBlock(e.target.value)}
          />
        </>
      )}

      {mode === 'BALANCE_FLOW_CUTOFF' && (
        <>
          <label className="mb-2 mt-4 block text-sm font-medium text-slate-600" htmlFor="wallet-cutoff-block">
            컷오프 블록 (필수)
          </label>
          <input
            id="wallet-cutoff-block"
            type="number"
            min={0}
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm md:max-w-xs"
            placeholder="예: 20000000"
            value={cutoffBlock}
            onChange={(e) => setCutoffBlock(e.target.value)}
          />
          <label className="mb-2 mt-4 block text-sm font-medium text-slate-600" htmlFor="tracked-tokens">
            추적 토큰 주소 (선택, 쉼표/공백/줄바꿈 구분)
          </label>
          <textarea
            id="tracked-tokens"
            className="min-h-20 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            placeholder="0x..., 0x..."
            value={trackedTokens}
            onChange={(e) => setTrackedTokens(e.target.value)}
          />
        </>
      )}
      {error && <p className="mt-2 text-sm text-ember">{error}</p>}
    </form>
  );
}
