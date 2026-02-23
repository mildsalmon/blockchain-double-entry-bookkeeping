'use client';

import { useState } from 'react';

interface WalletInputProps {
  onSubmit: (address: string, startBlock?: number) => Promise<void>;
}

const ETH_ADDRESS_REGEX = /^0x[a-fA-F0-9]{40}$/;

export function WalletInput({ onSubmit }: WalletInputProps) {
  const [address, setAddress] = useState('');
  const [startBlock, setStartBlock] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();

    if (!ETH_ADDRESS_REGEX.test(address)) {
      setError('Ethereum 주소 형식이 올바르지 않습니다.');
      return;
    }

    const parsedStartBlock =
      startBlock.trim() === '' ? undefined : Number.parseInt(startBlock.trim(), 10);
    if (parsedStartBlock !== undefined && (!Number.isFinite(parsedStartBlock) || parsedStartBlock < 0)) {
      setError('시작 블록은 0 이상의 정수여야 합니다.');
      return;
    }

    setError(null);
    setLoading(true);
    try {
      await onSubmit(address, parsedStartBlock);
      setAddress('');
      setStartBlock('');
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
      <label className="mb-2 mt-4 block text-sm font-medium text-slate-600" htmlFor="wallet-start-block">
        시작 블록 (선택)
      </label>
      <div className="flex flex-col gap-3 md:flex-row">
        <input
          id="wallet-address"
          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
          placeholder="0x..."
          value={address}
          onChange={(e) => setAddress(e.target.value)}
        />
        <input
          id="wallet-start-block"
          type="number"
          min={0}
          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm md:max-w-xs"
          placeholder="예: 19000000"
          value={startBlock}
          onChange={(e) => setStartBlock(e.target.value)}
        />
        <button
          type="submit"
          disabled={loading}
          className="rounded-lg bg-ink px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
        >
          {loading ? '등록 중...' : '등록'}
        </button>
      </div>
      {error && <p className="mt-2 text-sm text-ember">{error}</p>}
    </form>
  );
}
