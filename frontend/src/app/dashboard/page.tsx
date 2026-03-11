'use client';

import { useCallback, useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { SyncStatus } from '@/components/SyncStatus';
import type { BalanceDashboard } from '@/types/dashboard';
import type { Wallet } from '@/types/wallet';

const EMPTY_DASHBOARD: BalanceDashboard = {
  generatedAt: '',
  summary: {
    walletCount: 0,
    tokenCount: 0,
    positionCount: 0
  },
  positions: []
};

export default function DashboardPage() {
  const [dashboard, setDashboard] = useState<BalanceDashboard>(EMPTY_DASHBOARD);
  const [wallets, setWallets] = useState<Wallet[]>([]);
  const [walletAddress, setWalletAddress] = useState('');
  const [status, setStatus] = useState('');
  const [loading, setLoading] = useState(false);

  const load = useCallback(async (filters: { walletAddress?: string; status?: string } = {}) => {
    setLoading(true);
    try {
      const resolvedWalletAddress = filters.walletAddress ?? walletAddress.trim();
      const resolvedStatus = filters.status ?? status;
      const [dashboardResponse, walletsResponse] = await Promise.all([
        api.getDashboardBalances({ walletAddress: resolvedWalletAddress, status: resolvedStatus }),
        api.listWallets()
      ]);
      setDashboard(dashboardResponse);
      setWallets(walletsResponse);
    } finally {
      setLoading(false);
    }
  }, [status, walletAddress]);

  useEffect(() => {
    void load({ walletAddress: '', status: '' });
  }, [load]);

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-6xl flex-col gap-4 px-6 py-12">
      <h1 className="text-3xl font-bold text-ink">잔고 대시보드</h1>

      <section className="rounded-xl border border-slate-200 bg-white/90 p-4">
        <div className="grid gap-3 md:grid-cols-4">
          <input
            value={walletAddress}
            onChange={(event) => setWalletAddress(event.target.value)}
            placeholder="지갑 주소 필터 (옵션)"
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
          />
          <select
            value={status}
            onChange={(event) => setStatus(event.target.value)}
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="">전체 상태</option>
            <option value="AUTO_CLASSIFIED">자동분류</option>
            <option value="MANUAL_CLASSIFIED">수동분류</option>
            <option value="REVIEW_REQUIRED">검토필요</option>
            <option value="APPROVED">승인됨</option>
          </select>
          <button
            className="rounded-lg bg-ink px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"
            onClick={() => {
              void load({ walletAddress: walletAddress.trim(), status });
            }}
            disabled={loading}
          >
            {loading ? '조회 중...' : '필터 적용'}
          </button>
          <p className="self-center text-xs text-slate-500">기준 시각: {formatDateTime(dashboard.generatedAt)}</p>
        </div>
      </section>

      <section className="grid gap-3 md:grid-cols-3">
        <SummaryCard label="지갑 수" value={dashboard.summary.walletCount} />
        <SummaryCard label="토큰 수" value={dashboard.summary.tokenCount} />
        <SummaryCard label="포지션 수" value={dashboard.summary.positionCount} />
      </section>

      <section className="overflow-hidden rounded-xl border border-slate-200 bg-white/90">
        <table className="w-full border-collapse text-sm">
          <thead className="bg-slate-50 text-left text-slate-600">
            <tr>
              <th className="px-4 py-3">지갑</th>
              <th className="px-4 py-3">토큰</th>
              <th className="px-4 py-3">계정</th>
              <th className="px-4 py-3">수량</th>
              <th className="px-4 py-3">마지막 분개 시각</th>
            </tr>
          </thead>
          <tbody>
            {dashboard.positions.flatMap((position, index, positions) => {
              const rows: JSX.Element[] = [];
              if (isNewAccountGroup(position, positions[index - 1])) {
                rows.push(
                  <tr
                    key={`group-${position.walletAddress}-${position.accountCode}-${index}`}
                    className="border-t border-slate-200 bg-slate-50/70"
                  >
                    <td className="px-4 py-2 text-xs font-semibold text-slate-600" colSpan={5}>
                      계정 그룹: {position.accountCode}
                    </td>
                  </tr>
                );
              }

              rows.push(
                <tr
                  key={`position-${position.walletAddress}-${position.accountCode}-${position.tokenSymbol}-${index}`}
                  className="border-t border-slate-100"
                >
                  <td className="px-4 py-3 font-mono text-xs text-slate-600">{position.walletAddress}</td>
                  <td className="px-4 py-3" title={position.tokenAddress ?? undefined}>
                    {position.displayLabel}
                  </td>
                  <td className="px-4 py-3">{position.accountCode}</td>
                  <td className="px-4 py-3 font-semibold text-slate-800">{position.quantity}</td>
                  <td className="px-4 py-3 text-slate-600">{formatDateTime(position.lastEntryDate)}</td>
                </tr>
              );

              return rows;
            })}
            {dashboard.positions.length === 0 && (
              <tr>
                <td className="px-4 py-8 text-center text-slate-500" colSpan={5}>
                  조회된 잔고 포지션이 없습니다.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </section>

      <SyncStatus wallets={wallets} />
    </main>
  );
}

function SummaryCard({ label, value }: { label: string; value: number }) {
  return (
    <article className="rounded-xl border border-slate-200 bg-white/90 p-4">
      <p className="text-xs text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-bold text-ink">{value.toLocaleString('ko-KR')}</p>
    </article>
  );
}

function formatDateTime(value: string | null): string {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('ko-KR', { hour12: false });
}

function isNewAccountGroup(
  current: BalanceDashboard['positions'][number],
  previous?: BalanceDashboard['positions'][number]
): boolean {
  if (!previous) return true;
  return previous.walletAddress !== current.walletAddress || previous.accountCode !== current.accountCode;
}
