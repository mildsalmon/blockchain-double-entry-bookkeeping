import type { Wallet, WalletOmittedSuspected, WalletTokenPreview } from '@/types/wallet';

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
          const syncedBlock = wallet.deltaSyncedBlock ?? wallet.lastSyncedBlock;
          const snapshotBaseBlock = wallet.snapshotBlock ?? wallet.cutoffBlock;
          const syncedSinceSnapshot =
            snapshotBaseBlock !== null && syncedBlock !== null && syncedBlock >= snapshotBaseBlock
              ? syncedBlock - snapshotBaseBlock
              : null;

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
              <div className="mt-2 space-y-1 text-xs text-slate-600">
                {wallet.mode === 'BALANCE_FLOW_CUTOFF' && (
                  <>
                    <p>컷오프 기준 블록: {formatBlock(wallet.cutoffBlock)}</p>
                    <p>스냅샷 생성 블록: {formatBlock(wallet.snapshotBlock)}</p>
                    <p>스냅샷 이후 반영 블록 수: {syncedSinceSnapshot !== null ? `${formatBlock(syncedSinceSnapshot)} blocks` : '-'}</p>
                    <p>
                      최근 cutoff sign-off:{' '}
                      {wallet.latestCutoffSignOff
                        ? `${wallet.latestCutoffSignOff.reviewedBy} / ${formatDateTime(wallet.latestCutoffSignOff.reviewedAt)}`
                        : '-'}
                    </p>
                  </>
                )}
                <p>현재 동기화 도달 블록: {formatBlock(syncedBlock)}</p>
                <p>마지막 동기화 시각: {formatDateTime(wallet.lastSyncedAt)}</p>
              </div>

              {wallet.mode === 'BALANCE_FLOW_CUTOFF' && (
                <div className="mt-3 space-y-3 text-xs">
                  <TokenSection
                    title="Seeded At Cutoff"
                    description="opening balance에 포함된 ETH + seed 토큰"
                    tokens={wallet.seededTokens}
                    emptyText="기본 ETH만 포함되었거나 seed token이 없습니다."
                  />
                  <TokenSection
                    title="Discovered After Cutoff"
                    description="cutoff 이후 tx/log 활동으로 관측된 토큰"
                    tokens={wallet.discoveredTokens}
                    emptyText="아직 자동 발견된 토큰이 없습니다."
                  />
                  <OmittedSection items={wallet.omittedSuspectedTokens} />
                  <CorrectionPolicySection hasOmittedCandidates={wallet.omittedSuspectedTokens.length > 0} />
                  <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-[11px] text-amber-800">
                    `discovered after cutoff`는 cutoff 시점 누락 토큰을 자동 보정하지 않습니다. `omitted-suspected`는 예외 후보 신호일 뿐이며, 실제 누락 여부는 운영 확인이 필요합니다.
                  </p>
                </div>
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

function TokenSection({
  title,
  description,
  tokens,
  emptyText
}: {
  title: string;
  description: string;
  tokens: WalletTokenPreview[];
  emptyText: string;
}) {
  return (
    <section className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
      <p className="font-semibold text-ink">{title}</p>
      <p className="mt-1 text-[11px] text-slate-500">{description}</p>
      {tokens.length === 0 ? (
        <p className="mt-2 text-[11px] text-slate-500">{emptyText}</p>
      ) : (
        <ul className="mt-2 space-y-1">
          {tokens.map((token) => (
            <li key={`${title}-${token.tokenAddress ?? 'eth'}-${token.displayLabel}`} className="rounded-md bg-white px-2 py-1">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-medium text-ink">{token.displayLabel}</span>
                {token.firstSeenBlock !== null && (
                  <span className="text-[11px] text-slate-500">first seen @ {formatBlock(token.firstSeenBlock)}</span>
                )}
              </div>
              <p className="mt-1 font-mono text-[11px] text-slate-500">{token.tokenAddress ?? 'native ETH'}</p>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function OmittedSection({ items }: { items: WalletOmittedSuspected[] }) {
  return (
    <section className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2">
      <p className="font-semibold text-rose-900">Omitted-Suspected Candidates</p>
      <p className="mt-1 text-[11px] text-rose-700">컷오프 직후 활동 때문에 누락 가능성이 의심되는 예외 후보</p>
      {items.length === 0 ? (
        <p className="mt-2 text-[11px] text-rose-700">현재 예외로 분류된 토큰이 없습니다.</p>
      ) : (
        <ul className="mt-2 space-y-1">
          {items.map((item) => (
            <li key={`${item.tokenAddress}-${item.firstSeenBlock}`} className="rounded-md bg-white/80 px-2 py-1">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-medium text-rose-950">{item.displayLabel}</span>
                <span className="text-[11px] text-rose-700">first seen @ {formatBlock(item.firstSeenBlock)}</span>
              </div>
              <p className="mt-1 font-mono text-[11px] text-rose-700">{item.tokenAddress}</p>
              <p className="mt-1 text-[11px] text-rose-700">{item.reason}</p>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function CorrectionPolicySection({ hasOmittedCandidates }: { hasOmittedCandidates: boolean }) {
  return (
    <section
      className={`rounded-lg border px-3 py-2 ${
        hasOmittedCandidates
          ? 'border-amber-300 bg-amber-50'
          : 'border-slate-200 bg-slate-50'
      }`}
    >
      <p className={`font-semibold ${hasOmittedCandidates ? 'text-amber-900' : 'text-ink'}`}>
        {hasOmittedCandidates ? 'Correction Policy: Action Required' : 'Correction Policy'}
      </p>
      <p className={`mt-1 text-[11px] ${hasOmittedCandidates ? 'text-amber-800' : 'text-slate-600'}`}>
        current phase 기본 원칙은 `re-onboarding only` 입니다. 기존 cutoff wallet 의 seed 를 in-place 로 수정하지 않습니다.
      </p>
      <ul className={`mt-2 space-y-1 text-[11px] ${hasOmittedCandidates ? 'text-amber-900' : 'text-slate-700'}`}>
        <li>close 전 omitted token 확인 시: wallet 삭제 후 complete seed list 로 재등록</li>
        <li>close 후 발견 시: operator 임의 수정 금지, finance/accounting approval 로 escalation</li>
        <li>`FULL` 모드는 조사/감사용 fallback 이며 cutoff opening balance correction 자체가 아님</li>
      </ul>
    </section>
  );
}

function formatBlock(value: number | null): string {
  if (value === null || value === undefined) return '-';
  return value.toLocaleString('ko-KR');
}

function formatDateTime(value: string | null): string {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('ko-KR', { hour12: false });
}
