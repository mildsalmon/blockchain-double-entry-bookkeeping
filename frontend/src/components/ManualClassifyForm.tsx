'use client';

import { useState } from 'react';

interface ManualClassifyFormProps {
  eventId: number;
  initialTokenSymbol?: string | null;
  initialTokenAddress?: string | null;
  onSubmit: (eventId: number, payload: { eventType: string; tokenSymbol: string; amountDecimal: string; tokenAddress?: string | null }) => Promise<void>;
}

export function ManualClassifyForm({ eventId, initialTokenSymbol, initialTokenAddress, onSubmit }: ManualClassifyFormProps) {
  const [eventType, setEventType] = useState('MANUAL_CLASSIFIED');
  const [tokenSymbol, setTokenSymbol] = useState(initialTokenSymbol ?? 'ETH');
  const [tokenAddress, setTokenAddress] = useState(initialTokenAddress ?? '');
  const [amountDecimal, setAmountDecimal] = useState('0');
  const [error, setError] = useState<string | null>(null);

  return (
    <form
      className="grid gap-2 rounded-lg border border-slate-200 p-3"
      onSubmit={(e) => {
        e.preventDefault();
        const normalizedSymbol = tokenSymbol.trim().toUpperCase();
        const normalizedAddress = tokenAddress.trim();
        if (!normalizedAddress && normalizedSymbol !== 'ETH') {
          setError('ETH 외 토큰은 컨트랙트 주소가 필요합니다.');
          return;
        }
        if (normalizedAddress && !/^0x[a-fA-F0-9]{40}$/.test(normalizedAddress)) {
          setError('올바른 0x 컨트랙트 주소를 입력하세요.');
          return;
        }
        setError(null);
        void onSubmit(eventId, {
          eventType,
          tokenSymbol,
          amountDecimal,
          tokenAddress: normalizedAddress || null
        });
      }}
    >
      <select className="rounded border border-slate-300 px-2 py-1 text-sm" value={eventType} onChange={(e) => setEventType(e.target.value)}>
        <option value="MANUAL_CLASSIFIED">수동분류</option>
        <option value="INCOMING">입금</option>
        <option value="OUTGOING">출금</option>
        <option value="FEE">가스비</option>
      </select>
      <input className="rounded border border-slate-300 px-2 py-1 text-sm" value={tokenSymbol} onChange={(e) => setTokenSymbol(e.target.value)} placeholder="토큰 심볼" />
      <input
        className="rounded border border-slate-300 px-2 py-1 text-sm"
        value={tokenAddress}
        onChange={(e) => setTokenAddress(e.target.value)}
        placeholder="컨트랙트 주소 (ETH 외 필수)"
        spellCheck={false}
        autoCapitalize="off"
        autoCorrect="off"
      />
      <input className="rounded border border-slate-300 px-2 py-1 text-sm" value={amountDecimal} onChange={(e) => setAmountDecimal(e.target.value)} placeholder="수량" />
      <p className="text-xs text-slate-500">주소가 있으면 저장 심볼은 온체인 기준으로 정규화됩니다.</p>
      {error ? <p className="text-xs text-red-600">{error}</p> : null}
      <button className="rounded bg-ink px-3 py-1 text-xs font-semibold text-white" type="submit">
        분류 저장
      </button>
    </form>
  );
}
