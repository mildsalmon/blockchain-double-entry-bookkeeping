'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import type {
  AdminCorrectionLoginPayload,
  AdminCorrectionSession,
  Wallet,
  WalletAdminCorrectionApplyPayload,
  WalletAdminCorrectionPreflight,
  WalletAdminCorrectionPreflightPayload,
  WalletOmittedSuspected,
  WalletTokenPreview
} from '@/types/wallet';

interface SyncStatusProps {
  wallets: Wallet[];
  onRetry?: (address: string) => Promise<void>;
  onDelete?: (address: string) => Promise<void>;
  onApplyCorrection?: (
    address: string,
    payload: WalletAdminCorrectionApplyPayload
  ) => Promise<void>;
  busyActionKey?: string | null;
}

const TOKEN_ADDRESS_REGEX = /^0x[a-fA-F0-9]{40}$/;

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

export function SyncStatus({ wallets, onRetry, onDelete, onApplyCorrection, busyActionKey }: SyncStatusProps) {
  const hasAdminCorrectionSurface = wallets.some(
    (wallet) => wallet.mode === 'BALANCE_FLOW_CUTOFF' && wallet.adminCorrectionEnabled
  );
  const [adminSession, setAdminSession] = useState<AdminCorrectionSession | null>(null);
  const [sessionLoading, setSessionLoading] = useState(false);
  const [sessionBusy, setSessionBusy] = useState(false);
  const [sessionError, setSessionError] = useState<string | null>(null);

  useEffect(() => {
    if (!hasAdminCorrectionSurface) {
      setAdminSession(null);
      setSessionLoading(false);
      setSessionBusy(false);
      setSessionError(null);
      return;
    }

    let cancelled = false;

    async function loadAdminSession() {
      setSessionLoading(true);
      try {
        const session = await api.getAdminCorrectionSession();
        if (!cancelled) {
          setAdminSession(session);
        }
      } catch (error) {
        if (!cancelled) {
          setSessionError(readErrorMessage(error));
        }
      } finally {
        if (!cancelled) {
          setSessionLoading(false);
        }
      }
    }

    void loadAdminSession();

    return () => {
      cancelled = true;
    };
  }, [hasAdminCorrectionSurface]);

  async function handleAdminSessionSignIn(payload: AdminCorrectionLoginPayload) {
    setSessionBusy(true);
    setSessionError(null);
    try {
      const session = await api.createAdminCorrectionSession(payload);
      setAdminSession(session);
    } catch (error) {
      setSessionError(readErrorMessage(error));
      throw error;
    } finally {
      setSessionBusy(false);
    }
  }

  async function handleAdminSessionSignOut() {
    setSessionBusy(true);
    setSessionError(null);
    try {
      await api.clearAdminCorrectionSession();
      setAdminSession({
        authenticated: false,
        username: '',
        expiresAt: ''
      });
    } catch (error) {
      setSessionError(readErrorMessage(error));
      throw error;
    } finally {
      setSessionBusy(false);
    }
  }

  return (
    <div className="rounded-xl border border-slate-200 bg-white/90 p-5">
      <h2 className="text-lg font-semibold text-ink">등록된 지갑</h2>
      {hasAdminCorrectionSurface && (
        <AdminCorrectionSessionPanel
          session={adminSession}
          loading={sessionLoading}
          busy={sessionBusy}
          error={sessionError}
          onSignIn={handleAdminSessionSignIn}
          onSignOut={handleAdminSessionSignOut}
        />
      )}
      <ul className="mt-3 space-y-3">
        {wallets.map((wallet) => {
          const retryKey = `retry:${wallet.address}`;
          const deleteKey = `delete:${wallet.address}`;
          const correctionKey = `correction:${wallet.address}`;
          const retrying = busyActionKey === retryKey;
          const deleting = busyActionKey === deleteKey;
          const applyingCorrection = busyActionKey === correctionKey;
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
                        ? `${wallet.latestCutoffSignOff.reviewedBy} / ${formatDateTime(wallet.latestCutoffSignOff.reviewedAt)} / ${formatSignOffSource(wallet.latestCutoffSignOff.source)}`
                        : '-'}
                    </p>
                    {wallet.latestCutoffSignOff?.approvalReference && (
                      <p>승인 참조: {wallet.latestCutoffSignOff.approvalReference}</p>
                    )}
                    {wallet.latestCutoffSignOff?.reason && (
                      <p>사유: {wallet.latestCutoffSignOff.reason}</p>
                    )}
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
                  {!wallet.adminCorrectionEnabled ? (
                    <CorrectionDisabledNotice reason={wallet.adminCorrectionUnavailableReason} />
                  ) : !wallet.adminCorrectionEligible ? (
                    <CorrectionDisabledNotice reason={wallet.adminCorrectionIneligibleReason} />
                  ) : (
                    <CorrectionPolicySection
                      key={`${wallet.address}:${wallet.trackedTokens.join(',')}:${wallet.omittedSuspectedTokens.map((item) => item.tokenAddress).join(',')}:${adminSession?.username ?? 'anonymous'}`}
                      wallet={wallet}
                      session={adminSession}
                      sessionLoading={sessionLoading}
                      applying={applyingCorrection}
                      busy={busy || sessionBusy}
                      onApplyCorrection={onApplyCorrection}
                    />
                  )}
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

function AdminCorrectionSessionPanel({
  session,
  loading,
  busy,
  error,
  onSignIn,
  onSignOut
}: {
  session: AdminCorrectionSession | null;
  loading: boolean;
  busy: boolean;
  error: string | null;
  onSignIn: (payload: AdminCorrectionLoginPayload) => Promise<void>;
  onSignOut: () => Promise<void>;
}) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [localError, setLocalError] = useState<string | null>(null);

  async function handleSignIn() {
    const normalizedUsername = username.trim();
    if (!normalizedUsername || !password) {
      setLocalError('username과 password를 모두 입력해야 합니다.');
      return;
    }

    setLocalError(null);
    try {
      await onSignIn({
        username: normalizedUsername,
        password
      });
      setPassword('');
    } catch {
      setPassword('');
    }
  }

  const sessionLabel = session?.authenticated ? session.username : null;

  return (
    <section className="mt-3 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="font-semibold text-ink">Admin Correction Session</p>
          <p className="mt-1 text-[11px] text-slate-600">
            correction request는 브라우저 Basic Auth 대신 server-side session cookie를 통해 전달됩니다.
          </p>
        </div>
        {sessionLabel && (
          <button
            type="button"
            className="rounded-md border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 disabled:cursor-not-allowed disabled:opacity-60"
            disabled={busy}
            onClick={() => {
              void onSignOut();
            }}
          >
            {busy ? '세션 해제 중...' : '세션 해제'}
          </button>
        )}
      </div>

      {sessionLabel ? (
        <div className="mt-3 rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-[11px] text-emerald-900">
          <p>현재 인증된 admin: {sessionLabel}</p>
          <p>세션 만료 시각: {formatDateTime(session?.expiresAt ?? null)}</p>
        </div>
      ) : (
        <div className="mt-3 grid gap-3 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]">
          <Field
            label="Admin Username"
            value={username}
            placeholder="ops-kim"
            onChange={setUsername}
          />
          <Field
            label="Admin Password"
            value={password}
            placeholder="••••••••"
            onChange={setPassword}
            type="password"
          />
          <div className="flex items-end">
            <button
              type="button"
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-xs font-medium text-slate-700 disabled:cursor-not-allowed disabled:opacity-60"
              disabled={busy || loading}
              onClick={() => {
                void handleSignIn();
              }}
            >
              {busy ? '세션 생성 중...' : loading ? '세션 확인 중...' : 'Admin Sign-In'}
            </button>
          </div>
        </div>
      )}

      {(localError || error) && (
        <p className="mt-3 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-[11px] text-rose-700">
          {localError ?? error}
        </p>
      )}
    </section>
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

function CorrectionDisabledNotice({ reason }: { reason: string | null }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
      <p className="font-semibold text-slate-900">Correction Policy Unavailable</p>
      <p className="mt-1 text-[11px] text-slate-700">
        {reason ?? '이 환경에서는 admin correction이 비활성화되어 있습니다.'}
      </p>
    </section>
  );
}

function CorrectionPolicySection({
  wallet,
  session,
  sessionLoading,
  applying,
  busy,
  onApplyCorrection
}: {
  wallet: Wallet;
  session: AdminCorrectionSession | null;
  sessionLoading: boolean;
  applying: boolean;
  busy: boolean;
  onApplyCorrection?: (
    address: string,
    payload: WalletAdminCorrectionApplyPayload
  ) => Promise<void>;
}) {
  const [approvalReference, setApprovalReference] = useState('');
  const [reason, setReason] = useState('');
  const [tokenAddresses, setTokenAddresses] = useState(defaultTokenAddressText(wallet.omittedSuspectedTokens));
  const [preflight, setPreflight] = useState<WalletAdminCorrectionPreflight | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [preflighting, setPreflighting] = useState(false);
  const hasOmittedCandidates = wallet.omittedSuspectedTokens.length > 0;
  const isAuthenticated = Boolean(session?.authenticated);

  function clearPreviewAndError() {
    setPreflight(null);
    setError(null);
  }

  function updateApprovalReference(value: string) {
    clearPreviewAndError();
    setApprovalReference(value);
  }

  function updateReason(value: string) {
    clearPreviewAndError();
    setReason(value);
  }

  function updateTokenAddresses(value: string) {
    clearPreviewAndError();
    setTokenAddresses(value);
  }

  function fillFromOmittedCandidates() {
    updateTokenAddresses(defaultTokenAddressText(wallet.omittedSuspectedTokens));
  }

  function buildPayload(): WalletAdminCorrectionPreflightPayload | string {
    const parsedTokens = tokenAddresses
      .split(/[,\n ]+/)
      .map((token) => token.trim())
      .filter(Boolean);

    if (parsedTokens.length === 0) {
      return '추가할 토큰 주소를 하나 이상 입력해야 합니다.';
    }

    const invalidToken = parsedTokens.find((token) => !TOKEN_ADDRESS_REGEX.test(token));
    if (invalidToken) {
      return `유효하지 않은 토큰 주소입니다: ${invalidToken}`;
    }

    if (parsedTokens.length > 20) {
      return '한 번에 최대 20개의 토큰만 correction 대상으로 입력할 수 있습니다.';
    }

    const normalizedApprovalReference = approvalReference.trim();
    const normalizedReason = reason.trim();

    if (!normalizedApprovalReference || !normalizedReason) {
      return 'approvalReference와 reason을 모두 입력해야 합니다.';
    }

    if (normalizedApprovalReference.length > 120) {
      return 'approvalReference는 120자를 초과할 수 없습니다.';
    }

    if (normalizedReason.length > 500) {
      return 'reason은 500자를 초과할 수 없습니다.';
    }

    return {
      tokenAddresses: Array.from(new Set(parsedTokens.map((token) => token.toLowerCase()))),
      approvalReference: normalizedApprovalReference,
      reason: normalizedReason
    };
  }

  async function handlePreflight() {
    if (!isAuthenticated) {
      setError('상단 Admin Correction Session에서 인증 후 진행해야 합니다.');
      setPreflight(null);
      return;
    }

    const payload = buildPayload();
    if (typeof payload === 'string') {
      setError(payload);
      setPreflight(null);
      return;
    }

    setPreflighting(true);
    setError(null);
    try {
      const response = await api.preflightWalletAdminCorrection(wallet.address, payload);
      setPreflight(response);
    } catch (preflightError) {
      setPreflight(null);
      setError(readErrorMessage(preflightError));
    } finally {
      setPreflighting(false);
    }
  }

  async function handleApply() {
    if (!preflight || !onApplyCorrection) {
      return;
    }
    if (!isAuthenticated) {
      setError('상단 Admin Correction Session에서 인증 후 진행해야 합니다.');
      setPreflight(null);
      return;
    }

    const payload = buildPayload();
    if (typeof payload === 'string') {
      setError(payload);
      setPreflight(null);
      return;
    }

    const confirmed = window.confirm(
      `고위험 admin correction을 적용합니다.\n\n지갑: ${wallet.address}\n전략: ${preflight.strategy}\n추가 토큰 수: ${preflight.requestedTokens.length}\n승인 참조: ${payload.approvalReference}`
    );
    if (!confirmed) {
      return;
    }

    setError(null);
    try {
      await onApplyCorrection(wallet.address, {
        ...payload,
        summaryHash: preflight.summaryHash
      });
      setPreflight(null);
    } catch (applyError) {
      setError(readErrorMessage(applyError));
    }
  }

  return (
    <section
      className={`rounded-lg border px-3 py-2 ${
        hasOmittedCandidates
          ? 'border-amber-300 bg-amber-50'
          : 'border-slate-200 bg-slate-50'
      }`}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className={`font-semibold ${hasOmittedCandidates ? 'text-amber-900' : 'text-ink'}`}>
            {hasOmittedCandidates ? 'Correction Policy: Action Required' : 'Correction Policy'}
          </p>
          <p className={`mt-1 text-[11px] ${hasOmittedCandidates ? 'text-amber-800' : 'text-slate-600'}`}>
            current phase 기본 원칙은 `re-onboarding only` 입니다. Sprint 3 admin correction은 승인된 예외만 snapshot restate 방식으로 처리합니다.
          </p>
        </div>
        <span className="rounded-full border border-rose-300 bg-rose-100 px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.08em] text-rose-900">
          privileged
        </span>
      </div>

      <ul className={`mt-2 space-y-1 text-[11px] ${hasOmittedCandidates ? 'text-amber-900' : 'text-slate-700'}`}>
        <li>operator 임의 수정 금지, 승인 근거와 요청 사유가 있어야 합니다.</li>
        <li>audit actor는 인증된 admin session principal로 기록됩니다.</li>
        <li>preflight에서 restate 결과를 확인한 뒤 apply 해야 합니다.</li>
        <li>`FULL` 모드는 조사/감사용 fallback 이며 cutoff opening balance correction 자체가 아닙니다.</li>
      </ul>

      {!isAuthenticated && (
        <p className="mt-3 rounded-md border border-slate-200 bg-white px-3 py-2 text-[11px] text-slate-700">
          {sessionLoading
            ? 'admin session을 확인하고 있습니다.'
            : '상단 Admin Correction Session에서 인증을 완료하면 correction preflight/apply를 사용할 수 있습니다.'}
        </p>
      )}

      <div className="mt-3 grid gap-3 md:grid-cols-2">
        <Field
          label="Approval Reference"
          value={approvalReference}
          placeholder="FIN-2026-0311"
          onChange={updateApprovalReference}
        />
        <Field
          label="Reason"
          value={reason}
          placeholder="cutoff 시 USDC seed 누락"
          onChange={updateReason}
        />
      </div>

      <div className="mt-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <label className="text-[11px] font-medium text-slate-700" htmlFor={`wallet-correction-tokens-${wallet.address}`}>
            Token Addresses
          </label>
          {wallet.omittedSuspectedTokens.length > 0 && (
            <button
              type="button"
              className="rounded-md border border-amber-300 px-2 py-1 text-[11px] font-medium text-amber-900 disabled:cursor-not-allowed disabled:opacity-60"
              disabled={busy || preflighting}
              onClick={fillFromOmittedCandidates}
            >
              omitted 후보 채우기
            </button>
          )}
        </div>
        <textarea
          id={`wallet-correction-tokens-${wallet.address}`}
          className="mt-1 min-h-28 w-full rounded-md border border-slate-300 px-3 py-2 font-mono text-[11px] text-slate-900 outline-none transition focus:border-amber-400 focus:ring-2 focus:ring-amber-100"
          placeholder="0x...\n0x..."
          value={tokenAddresses}
          onChange={(event) => updateTokenAddresses(event.target.value)}
        />
        <p className="mt-1 text-[11px] text-slate-500">
          쉼표, 공백, 줄바꿈으로 여러 주소를 입력할 수 있습니다. 한 번에 최대 20개까지 허용됩니다.
        </p>
      </div>

      {error && (
        <p className="mt-3 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-[11px] text-rose-700">{error}</p>
      )}

      <div className="mt-3 flex flex-wrap gap-2">
        <button
          type="button"
          className="rounded-md border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 disabled:cursor-not-allowed disabled:opacity-60"
          disabled={busy || preflighting || !isAuthenticated}
          onClick={() => {
            void handlePreflight();
          }}
        >
          {preflighting ? 'preflight 확인 중...' : 'Preflight'}
        </button>
        <button
          type="button"
          className="rounded-md border border-rose-300 px-3 py-1.5 text-xs font-medium text-rose-700 disabled:cursor-not-allowed disabled:opacity-60"
          disabled={!preflight || !onApplyCorrection || busy || preflighting || !isAuthenticated}
          onClick={() => {
            void handleApply();
          }}
        >
          {applying ? '적용 중...' : 'Apply Correction'}
        </button>
      </div>

      {preflight && <CorrectionPreview preview={preflight} />}
    </section>
  );
}

function Field({
  label,
  value,
  placeholder,
  onChange,
  type = 'text'
}: {
  label: string;
  value: string;
  placeholder: string;
  onChange: (value: string) => void;
  type?: 'text' | 'password';
}) {
  return (
    <label className="block">
      <span className="text-[11px] font-medium text-slate-700">{label}</span>
      <input
        type={type}
        className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-xs text-slate-900 outline-none transition focus:border-amber-400 focus:ring-2 focus:ring-amber-100"
        value={value}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

function CorrectionPreview({ preview }: { preview: WalletAdminCorrectionPreflight }) {
  return (
    <div className="mt-3 rounded-lg border border-slate-200 bg-white px-3 py-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="font-semibold text-ink">Correction Preflight</p>
          <p className="mt-1 text-[11px] text-slate-500">
            strategy `{preview.strategy}` / cutoff {formatBlock(preview.cutoffBlock)}
          </p>
        </div>
        <span className="rounded-full border border-slate-200 bg-slate-50 px-2 py-1 text-[10px] font-medium text-slate-600">
          summaryHash {preview.summaryHash.slice(0, 12)}...
        </span>
      </div>

      <div className="mt-3 grid gap-3 md:grid-cols-2">
        <PreviewList title="Requested Tokens" items={preview.requestedTokens} emptyText="요청된 토큰이 없습니다." monospace />
        <PreviewList
          title="Matched Omitted Candidates"
          items={preview.omittedCandidateMatches.map((item) => `${item.displayLabel} (${item.tokenAddress})`)}
          emptyText="자동 매칭된 omitted 후보가 없습니다."
        />
        <PreviewList title="Current Tracked Tokens" items={preview.currentTrackedTokens} emptyText="현재 tracked token이 없습니다." monospace />
        <PreviewList title="Resulting Tracked Tokens" items={preview.resultingTrackedTokens} emptyText="결과 tracked token이 없습니다." monospace />
      </div>

      <div className="mt-3 grid gap-3 md:grid-cols-2">
        <TokenPreviewList title="Current Seeded Tokens" tokens={preview.currentSeededTokens} emptyText="현재 seeded token이 없습니다." />
        <TokenPreviewList title="Resulting Seeded Tokens" tokens={preview.resultingSeededTokens} emptyText="결과 seeded token이 없습니다." />
      </div>

      <CorrectionImpactSummary impact={preview.impact} />

      <PreviewList
        title="Warnings"
        items={preview.warnings}
        emptyText="추가 경고가 없습니다."
        className="mt-3 border-amber-200 bg-amber-50 text-amber-900"
      />
    </div>
  );
}

function CorrectionImpactSummary({ impact }: { impact: WalletAdminCorrectionPreflight['impact'] }) {
  return (
    <section className="mt-3 rounded-md border border-slate-200 bg-slate-50 px-3 py-2">
      <p className="font-medium text-slate-700">Rebuild Impact</p>
      <ul className="mt-2 space-y-1 text-[11px] text-slate-700">
        <li>purge snapshots: {formatCount(impact.snapshotCount)}</li>
        <li>purge raw tx: {formatCount(impact.rawTransactionCount)}</li>
        <li>purge accounting events: {formatCount(impact.accountingEventCount)}</li>
        <li>purge journal entries: {formatCount(impact.journalEntryCount)}</li>
        <li>purge cost lots: {formatCount(impact.costBasisLotCount)}</li>
        <li>
          replay scope: {formatBlock(impact.replayFromBlock)} - {formatBlock(impact.replayToBlock)} (
          {impact.replayBlockSpan !== null ? `${formatBlock(impact.replayBlockSpan)} blocks` : '-'})
        </li>
      </ul>
    </section>
  );
}

function PreviewList({
  title,
  items,
  emptyText,
  monospace = false,
  className = 'border-slate-200 bg-slate-50 text-slate-700'
}: {
  title: string;
  items: string[];
  emptyText: string;
  monospace?: boolean;
  className?: string;
}) {
  return (
    <section className={`rounded-md border px-3 py-2 ${className}`}>
      <p className="font-medium">{title}</p>
      {items.length === 0 ? (
        <p className="mt-2 text-[11px]">{emptyText}</p>
      ) : (
        <ul className="mt-2 space-y-1">
          {items.map((item) => (
            <li key={`${title}-${item}`} className={monospace ? 'break-all font-mono text-[11px]' : 'text-[11px]'}>
              {item}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function TokenPreviewList({
  title,
  tokens,
  emptyText
}: {
  title: string;
  tokens: WalletTokenPreview[];
  emptyText: string;
}) {
  return (
    <section className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2">
      <p className="font-medium text-slate-700">{title}</p>
      {tokens.length === 0 ? (
        <p className="mt-2 text-[11px] text-slate-500">{emptyText}</p>
      ) : (
        <ul className="mt-2 space-y-1">
          {tokens.map((token) => (
            <li key={`${title}-${token.tokenAddress ?? 'eth'}-${token.displayLabel}`} className="text-[11px] text-slate-700">
              <span className="font-medium text-ink">{token.displayLabel}</span>
              <span className="ml-2 font-mono text-slate-500">{token.tokenAddress ?? 'native ETH'}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function defaultTokenAddressText(items: WalletOmittedSuspected[]): string {
  return items.map((item) => item.tokenAddress).join('\n');
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

function formatCount(value: number | null): string {
  if (value === null || value === undefined) return '-';
  return value.toLocaleString('ko-KR');
}

function formatSignOffSource(value: string | null): string {
  if (!value) return 'UNKNOWN';
  if (value === 'ADMIN_CORRECTION') return 'ADMIN_CORRECTION';
  if (value === 'INITIAL_REGISTRATION') return 'INITIAL_REGISTRATION';
  return value;
}

function readErrorMessage(error: unknown): string {
  if (!(error instanceof Error)) return '요청 중 오류가 발생했습니다.';
  const message = error.message.trim();
  if (!message) return '요청 중 오류가 발생했습니다.';

  try {
    const parsed = JSON.parse(message) as { message?: string; error?: string; detail?: string };
    const merged = [parsed.message, parsed.error, parsed.detail].filter(Boolean).join(': ');
    return merged || message;
  } catch {
    return message;
  }
}
