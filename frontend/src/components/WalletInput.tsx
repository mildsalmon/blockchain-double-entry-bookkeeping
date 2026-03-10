'use client';

import { useMemo, useState } from 'react';
import { api } from '@/lib/api';
import type { WalletCreatePayload, WalletCutoffPreflight, WalletMode } from '@/types/wallet';

interface WalletInputProps {
  onSubmit: (payload: WalletCreatePayload) => Promise<void>;
}

const ETH_ADDRESS_REGEX = /^0x[a-fA-F0-9]{40}$/;
const TOKEN_ADDRESS_REGEX = /^0x[a-fA-F0-9]{40}$/;

export function WalletInput({ onSubmit }: WalletInputProps) {
  const [address, setAddress] = useState('');
  const [reviewedBy, setReviewedBy] = useState('');
  const [mode, setMode] = useState<WalletMode>('BALANCE_FLOW_CUTOFF');
  const [startBlock, setStartBlock] = useState('');
  const [cutoffBlock, setCutoffBlock] = useState('');
  const [trackedTokens, setTrackedTokens] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [preflight, setPreflight] = useState<WalletCutoffPreflight | null>(null);
  const [pendingPayload, setPendingPayload] = useState<WalletCreatePayload | null>(null);

  const parsedTokenLines = useMemo(() => {
    return trackedTokens
      .split(/[,\n ]+/)
      .map((token) => token.trim())
      .filter((token) => token.length > 0);
  }, [trackedTokens]);

  function clearPreflight() {
    setPreflight(null);
    setPendingPayload(null);
  }

  function resetForm() {
    setAddress('');
    setReviewedBy('');
    setMode('BALANCE_FLOW_CUTOFF');
    setStartBlock('');
    setCutoffBlock('');
    setTrackedTokens('');
    clearPreflight();
  }

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
      setError(null);
      setLoading(true);
      try {
        await onSubmit(payload);
        resetForm();
      } catch (e) {
        setError(e instanceof Error ? e.message : '지갑 등록 중 오류가 발생했습니다.');
      } finally {
        setLoading(false);
      }
      return;
    }

    if (reviewedBy.trim() === '') {
      setError('컷오프 등록에는 검토자 이름이 필요합니다.');
      return;
    }

    const parsedCutoffBlock =
      cutoffBlock.trim() === '' ? undefined : Number.parseInt(cutoffBlock.trim(), 10);
    if (parsedCutoffBlock === undefined || !Number.isFinite(parsedCutoffBlock) || parsedCutoffBlock < 0) {
      setError('컷오프 블록은 0 이상의 정수로 입력해야 합니다.');
      return;
    }

    const invalidToken = parsedTokenLines.find((token) => !TOKEN_ADDRESS_REGEX.test(token));
    if (invalidToken) {
      setError(`잘못된 토큰 주소 형식입니다: ${invalidToken}`);
      return;
    }

    payload.reviewedBy = reviewedBy.trim();
    payload.cutoffBlock = parsedCutoffBlock;
    payload.trackedTokens = parsedTokenLines;

    setError(null);
    setLoading(true);
    try {
      const result = await api.preflightCutoffWallet({
        address,
        cutoffBlock: parsedCutoffBlock,
        trackedTokens: parsedTokenLines
      });
      payload.preflightSummaryHash = result.summaryHash;
      setPendingPayload(payload);
      setPreflight(result);
    } catch (e) {
      setError(e instanceof Error ? e.message : '컷오프 사전 검증 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  }

  async function handleConfirmRegistration() {
    if (!pendingPayload) return;

    setError(null);
    setLoading(true);
    try {
      await onSubmit(pendingPayload);
      resetForm();
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
          onChange={(e) => {
            setAddress(e.target.value);
            clearPreflight();
          }}
        />
        <select
          id="wallet-mode"
          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm md:max-w-xs"
          value={mode}
          onChange={(e) => {
            setMode(e.target.value as WalletMode);
            clearPreflight();
          }}
        >
          <option value="BALANCE_FLOW_CUTOFF">컷오프 기반 온보딩 (기본)</option>
          <option value="FULL">전체 이력 동기화 (조사/감사용)</option>
        </select>
        <button
          type="submit"
          disabled={loading}
          className="rounded-lg bg-ink px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
        >
          {loading ? '처리 중...' : mode === 'BALANCE_FLOW_CUTOFF' ? '사전 검증' : '등록'}
        </button>
      </div>

      {mode === 'FULL' && (
        <>
          <p className="mt-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
            `FULL`은 조사, 감사, omitted token 원인 분석용 fallback 입니다. cutoff opening balance를 자동 보정하지는 않습니다.
          </p>
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
            onChange={(e) => {
              setStartBlock(e.target.value);
              clearPreflight();
            }}
          />
        </>
      )}

      {mode === 'BALANCE_FLOW_CUTOFF' && (
        <>
          <p className="mt-3 rounded-lg border border-sky-200 bg-sky-50 px-3 py-2 text-xs text-sky-800">
            cutoff 스냅샷은 ETH와 여기에 입력한 토큰만 opening balance로 반영합니다. cutoff 이후 새로 움직인 토큰만 자동 발견됩니다.
          </p>
          <label className="mb-2 mt-4 block text-sm font-medium text-slate-600" htmlFor="wallet-reviewed-by">
            검토자 이름
          </label>
          <input
            id="wallet-reviewed-by"
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm md:max-w-xs"
            placeholder="예: Kim Ops"
            value={reviewedBy}
            onChange={(e) => {
              setReviewedBy(e.target.value);
              clearPreflight();
            }}
          />
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
            onChange={(e) => {
              setCutoffBlock(e.target.value);
              clearPreflight();
            }}
          />
          <label className="mb-2 mt-4 block text-sm font-medium text-slate-600" htmlFor="tracked-tokens">
            컷오프 시점 보유 토큰 contract address (선택, 쉼표/공백/줄바꿈 구분)
          </label>
          <textarea
            id="tracked-tokens"
            className="min-h-24 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            placeholder="0x..., 0x..."
            value={trackedTokens}
            onChange={(e) => {
              setTrackedTokens(e.target.value);
              clearPreflight();
            }}
          />
          <p className="mt-2 text-xs text-slate-500">
            중복 주소는 자동으로 제거됩니다. cutoff 시점에 이미 들고 있었지만 여기서 빠진 토큰은 자동으로 복구되지 않습니다.
          </p>
        </>
      )}

      {preflight && (
        <div className="mt-4 rounded-xl border border-mint/30 bg-mint/5 p-4">
          <h3 className="text-sm font-semibold text-ink">스냅샷 확인</h3>
          <div className="mt-2 space-y-1 text-xs text-slate-700">
            <p>지갑 주소: <span className="font-mono">{preflight.address}</span></p>
            <p>컷오프 블록: {preflight.cutoffBlock.toLocaleString('ko-KR')}</p>
            <p>검토자: {reviewedBy.trim() || '-'}</p>
            <p>{preflight.warning}</p>
          </div>
          <ul className="mt-3 space-y-2 rounded-lg border border-slate-200 bg-white p-3 text-xs text-slate-700">
            {preflight.seededTokens.map((token) => (
              <li key={`${token.tokenAddress ?? 'eth'}-${token.displayLabel}`} className="flex flex-col gap-1">
                <span className="font-medium text-ink">{token.displayLabel}</span>
                <span className="font-mono text-[11px] text-slate-500">{token.tokenAddress ?? 'native ETH'}</span>
              </li>
            ))}
          </ul>
          <div className="mt-3 flex flex-wrap gap-2">
            <button
              type="button"
              disabled={loading}
              className="rounded-lg bg-ink px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
              onClick={() => {
                void handleConfirmRegistration();
              }}
            >
              {loading ? '등록 중...' : '확인 후 등록'}
            </button>
            <button
              type="button"
              className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700"
              onClick={clearPreflight}
            >
              다시 수정
            </button>
          </div>
        </div>
      )}

      {error && <p className="mt-3 text-sm text-ember">{error}</p>}
    </form>
  );
}
