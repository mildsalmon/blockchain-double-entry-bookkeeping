'use client';

import { useState } from 'react';

interface JournalFiltersProps {
  onApply: (filters: { fromDate?: string; toDate?: string; status?: string }) => void;
}

export function JournalFilters({ onApply }: JournalFiltersProps) {
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [status, setStatus] = useState('');

  return (
    <div className="rounded-xl border border-slate-200 bg-white/90 p-4">
      <div className="grid gap-3 md:grid-cols-4">
        <input type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)} className="rounded-lg border border-slate-300 px-3 py-2 text-sm" />
        <input type="date" value={toDate} onChange={(e) => setToDate(e.target.value)} className="rounded-lg border border-slate-300 px-3 py-2 text-sm" />
        <select value={status} onChange={(e) => setStatus(e.target.value)} className="rounded-lg border border-slate-300 px-3 py-2 text-sm">
          <option value="">전체 상태</option>
          <option value="AUTO_CLASSIFIED">자동분류</option>
          <option value="MANUAL_CLASSIFIED">수동분류</option>
          <option value="REVIEW_REQUIRED">검토필요</option>
          <option value="APPROVED">승인됨</option>
        </select>
        <button
          className="rounded-lg bg-ink px-4 py-2 text-sm font-semibold text-white"
          onClick={() => onApply({ fromDate, toDate, status })}
        >
          필터 적용
        </button>
      </div>
    </div>
  );
}
