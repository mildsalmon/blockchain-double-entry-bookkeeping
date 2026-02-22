'use client';

import Link from 'next/link';
import type { Journal } from '@/types/journal';

interface JournalTableProps {
  journals: Journal[];
}

const statusColor: Record<string, string> = {
  REVIEW_REQUIRED: 'bg-yellow-100 text-yellow-800',
  APPROVED: 'bg-emerald-100 text-emerald-700',
  UNCLASSIFIED: 'bg-red-100 text-red-700',
  AUTO_CLASSIFIED: 'bg-blue-100 text-blue-700',
  MANUAL_CLASSIFIED: 'bg-indigo-100 text-indigo-700'
};

export function JournalTable({ journals }: JournalTableProps) {
  return (
    <div className="overflow-hidden rounded-xl border border-slate-200 bg-white/90">
      <table className="w-full border-collapse text-sm">
        <thead className="bg-slate-50 text-left text-slate-600">
          <tr>
            <th className="px-4 py-3">날짜</th>
            <th className="px-4 py-3">설명</th>
            <th className="px-4 py-3">차변</th>
            <th className="px-4 py-3">대변</th>
            <th className="px-4 py-3">상태</th>
          </tr>
        </thead>
        <tbody>
          {journals.map((journal) => {
            const debit = journal.lines.find((line) => Number(line.debitAmount) > 0);
            const credit = journal.lines.find((line) => Number(line.creditAmount) > 0);
            return (
              <tr key={journal.id ?? `${journal.rawTransactionId}-${journal.entryDate}`} className="border-t border-slate-100">
                <td className="px-4 py-3">{journal.entryDate.slice(0, 10)}</td>
                <td className="px-4 py-3">
                  <Link className="font-semibold text-slate-800 hover:underline" href={`/journals/${journal.id}`}>
                    {journal.description}
                  </Link>
                </td>
                <td className="px-4 py-3">{debit?.accountCode ?? '-'}</td>
                <td className="px-4 py-3">{credit?.accountCode ?? '-'}</td>
                <td className="px-4 py-3">
                  <span className={`rounded-full px-2 py-1 text-xs font-semibold ${statusColor[journal.status] ?? 'bg-slate-100 text-slate-700'}`}>
                    {journal.status}
                  </span>
                </td>
              </tr>
            );
          })}
          {journals.length === 0 && (
            <tr>
              <td className="px-4 py-8 text-center text-slate-500" colSpan={5}>
                조회된 분개가 없습니다.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
