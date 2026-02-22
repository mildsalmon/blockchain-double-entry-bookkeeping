'use client';

import { useState } from 'react';
import { api } from '@/lib/api';

export function ExportDialog() {
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [format, setFormat] = useState<'CSV' | 'XLSX'>('CSV');
  const [loading, setLoading] = useState(false);

  async function handleExport() {
    if (!fromDate || !toDate) return;
    setLoading(true);
    try {
      const blob = await api.exportJournals({ fromDate, toDate, format });
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = `journals_${fromDate}_${toDate}.${format === 'CSV' ? 'csv' : 'xlsx'}`;
      link.click();
      URL.revokeObjectURL(link.href);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="rounded-xl border border-slate-200 bg-white/90 p-4">
      <h3 className="text-base font-semibold text-ink">내보내기</h3>
      <div className="mt-3 grid gap-2 md:grid-cols-4">
        <input type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)} className="rounded border border-slate-300 px-2 py-1 text-sm" />
        <input type="date" value={toDate} onChange={(e) => setToDate(e.target.value)} className="rounded border border-slate-300 px-2 py-1 text-sm" />
        <select value={format} onChange={(e) => setFormat(e.target.value as 'CSV' | 'XLSX')} className="rounded border border-slate-300 px-2 py-1 text-sm">
          <option value="CSV">CSV</option>
          <option value="XLSX">Excel</option>
        </select>
        <button className="rounded bg-ink px-3 py-1 text-sm font-semibold text-white disabled:opacity-60" disabled={loading} onClick={() => void handleExport()}>
          {loading ? '내보내는 중...' : '다운로드'}
        </button>
      </div>
    </div>
  );
}
