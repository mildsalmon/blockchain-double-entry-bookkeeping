import type { Wallet } from '@/types/wallet';

interface SyncStatusProps {
  wallets: Wallet[];
}

const statusColor: Record<string, string> = {
  PENDING: 'text-slate-500',
  SYNCING: 'text-amber-600',
  COMPLETED: 'text-mint',
  FAILED: 'text-ember'
};

export function SyncStatus({ wallets }: SyncStatusProps) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white/90 p-5">
      <h2 className="text-lg font-semibold text-ink">등록된 지갑</h2>
      <ul className="mt-3 space-y-3">
        {wallets.map((wallet) => (
          <li key={wallet.address} className="rounded-lg border border-slate-200 p-3">
            <p className="font-mono text-xs text-slate-600">{wallet.address}</p>
            <p className={`mt-1 text-sm font-semibold ${statusColor[wallet.syncStatus] ?? 'text-slate-500'}`}>
              {wallet.syncStatus}
            </p>
          </li>
        ))}
        {wallets.length === 0 && <li className="text-sm text-slate-500">아직 등록된 지갑이 없습니다.</li>}
      </ul>
    </div>
  );
}
