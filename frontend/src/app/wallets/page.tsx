'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { SyncStatus } from '@/components/SyncStatus';
import { WalletInput } from '@/components/WalletInput';
import type { Wallet, WalletAdminCorrectionApplyPayload, WalletCreatePayload } from '@/types/wallet';

export default function WalletsPage() {
  const [wallets, setWallets] = useState<Wallet[]>([]);
  const [busyActionKey, setBusyActionKey] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  async function loadWallets() {
    const items = await api.listWallets();
    setWallets(items);
  }

  useEffect(() => {
    void loadWallets();
    const timer = setInterval(() => {
      void loadWallets();
    }, 5000);
    return () => clearInterval(timer);
  }, []);

  async function handleCreate(payload: WalletCreatePayload) {
    await api.createWallet(payload);
    await loadWallets();
  }

  async function handleRetry(address: string) {
    const actionKey = `retry:${address}`;
    setBusyActionKey(actionKey);
    setActionError(null);
    try {
      await api.retryWallet(address);
      await loadWallets();
    } catch (error) {
      setActionError(readErrorMessage(error));
    } finally {
      setBusyActionKey(null);
    }
  }

  async function handleDelete(address: string) {
    const confirmed = window.confirm(`정말 이 지갑을 삭제할까요?\n\n${address}`);
    if (!confirmed) return;

    const actionKey = `delete:${address}`;
    setBusyActionKey(actionKey);
    setActionError(null);
    try {
      await api.deleteWallet(address);
      await loadWallets();
    } catch (error) {
      setActionError(readErrorMessage(error));
    } finally {
      setBusyActionKey(null);
    }
  }

  async function handleApplyCorrection(
    address: string,
    payload: WalletAdminCorrectionApplyPayload
  ) {
    const actionKey = `correction:${address}`;
    setBusyActionKey(actionKey);
    setActionError(null);
    try {
      await api.applyWalletAdminCorrection(address, payload);
      await loadWallets();
    } finally {
      setBusyActionKey(null);
    }
  }

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-5xl flex-col gap-6 px-6 py-12">
      <h1 className="text-3xl font-bold text-ink">지갑 등록 및 동기화</h1>
      <WalletInput onSubmit={handleCreate} />
      {actionError && (
        <p className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">{actionError}</p>
      )}
      <SyncStatus
        wallets={wallets}
        onRetry={handleRetry}
        onDelete={handleDelete}
        onApplyCorrection={handleApplyCorrection}
        busyActionKey={busyActionKey}
      />
    </main>
  );
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
