import type { Wallet } from '@/types/wallet';

interface SyncStatusProps {
  wallets: Wallet[];
  onRetry?: (address: string) => Promise<void>;
  onDelete?: (address: string) => Promise<void>;
  busyActionKey?: string | null;
}

const statusColor: Record<string, string> = {
  PENDING: 'text-slate-500',
  SYNCING: 'text-amber-600',
  COMPLETED: 'text-mint',
  FAILED: 'text-ember'
};

const phaseLabel: Record<string, string> = {
  NONE: '기본 모드',
  SNAPSHOT_PENDING: '스냅샷 대기',
  SNAPSHOTTING: '스냅샷 수집 중',
  SNAPSHOT_COMPLETED: '스냅샷 완료',
  DELTA_SYNCING: '이후 블록 동기화 중',
  DELTA_COMPLETED: '이후 블록 동기화 완료',
  FAILED: '동기화 실패'
};

export function SyncStatus({ wallets, onRetry, onDelete, busyActionKey }: SyncStatusProps) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white/90 p-5">
      <h2 className="text-lg font-semibold text-ink">등록된 지갑</h2>
      <ul className="mt-3 space-y-3">
        {wallets.map((wallet) => {
          const retryKey = `retry:${wallet.address}`;
          const deleteKey = `delete:${wallet.address}`;
          const retrying = busyActionKey === retryKey;
          const deleting = busyActionKey === deleteKey;
          const busy = Boolean(busyActionKey);

          return (
            <li key={wallet.address} className="rounded-lg border border-slate-200 p-3">
              <p className="font-mono text-xs text-slate-600">{wallet.address}</p>
              <p className="mt-1 text-xs text-slate-500">
                {wallet.mode === 'BALANCE_FLOW_CUTOFF' ? '컷오프 흐름 모드' : '기본 전체 동기화 모드'}
              </p>
              <p className={`mt-1 text-sm font-semibold ${statusColor[wallet.syncStatus] ?? 'text-slate-500'}`}>
                {wallet.syncStatus}
              </p>
              <p className="mt-1 text-xs text-slate-600">{phaseLabel[wallet.syncPhase] ?? wallet.syncPhase}</p>
              {wallet.mode === 'BALANCE_FLOW_CUTOFF' && (
                <p className="mt-1 text-xs text-slate-500">
                  cutoff: {wallet.cutoffBlock ?? '-'} / snapshot: {wallet.snapshotBlock ?? '-'} / delta:{' '}
                  {wallet.deltaSyncedBlock ?? '-'}
                </p>
              )}
              <div className="mt-3 flex gap-2">
                <button
                  type="button"
                  className="rounded-md border border-slate-300 px-2 py-1 text-xs font-medium text-slate-700 disabled:cursor-not-allowed disabled:opacity-60"
                  disabled={!onRetry || busy}
                  onClick={() => {
                    if (onRetry) void onRetry(wallet.address);
                  }}
                >
                  {retrying ? '재시도 중...' : '재시도'}
                </button>
                <button
                  type="button"
                  className="rounded-md border border-rose-300 px-2 py-1 text-xs font-medium text-rose-700 disabled:cursor-not-allowed disabled:opacity-60"
                  disabled={!onDelete || busy}
                  onClick={() => {
                    if (onDelete) void onDelete(wallet.address);
                  }}
                >
                  {deleting ? '삭제 중...' : '삭제'}
                </button>
              </div>
            </li>
          );
        })}
        {wallets.length === 0 && <li className="text-sm text-slate-500">아직 등록된 지갑이 없습니다.</li>}
      </ul>
    </div>
  );
}
