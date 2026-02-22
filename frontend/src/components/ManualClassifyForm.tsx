'use client';

import { useState } from 'react';

interface ManualClassifyFormProps {
  eventId: number;
  onSubmit: (eventId: number, payload: { eventType: string; tokenSymbol: string; amountDecimal: string }) => Promise<void>;
}

export function ManualClassifyForm({ eventId, onSubmit }: ManualClassifyFormProps) {
  const [eventType, setEventType] = useState('MANUAL_CLASSIFIED');
  const [tokenSymbol, setTokenSymbol] = useState('ETH');
  const [amountDecimal, setAmountDecimal] = useState('0');

  return (
    <form
      className="grid gap-2 rounded-lg border border-slate-200 p-3"
      onSubmit={(e) => {
        e.preventDefault();
        void onSubmit(eventId, { eventType, tokenSymbol, amountDecimal });
      }}
    >
      <select className="rounded border border-slate-300 px-2 py-1 text-sm" value={eventType} onChange={(e) => setEventType(e.target.value)}>
        <option value="MANUAL_CLASSIFIED">수동분류</option>
        <option value="INCOMING">입금</option>
        <option value="OUTGOING">출금</option>
        <option value="SWAP">스왑</option>
        <option value="FEE">가스비</option>
      </select>
      <input className="rounded border border-slate-300 px-2 py-1 text-sm" value={tokenSymbol} onChange={(e) => setTokenSymbol(e.target.value)} placeholder="토큰 심볼" />
      <input className="rounded border border-slate-300 px-2 py-1 text-sm" value={amountDecimal} onChange={(e) => setAmountDecimal(e.target.value)} placeholder="수량" />
      <button className="rounded bg-ink px-3 py-1 text-xs font-semibold text-white" type="submit">
        분류 저장
      </button>
    </form>
  );
}
