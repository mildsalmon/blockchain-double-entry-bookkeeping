'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { SyncStatus } from '@/components/SyncStatus';
import { WalletInput } from '@/components/WalletInput';
import type { Wallet } from '@/types/wallet';

export default function WalletsPage() {
  const [wallets, setWallets] = useState<Wallet[]>([]);

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

  async function handleCreate(address: string) {
    await api.createWallet(address);
    await loadWallets();
  }

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-5xl flex-col gap-6 px-6 py-12">
      <h1 className="text-3xl font-bold text-ink">지갑 등록 및 동기화</h1>
      <WalletInput onSubmit={handleCreate} />
      <SyncStatus wallets={wallets} />
    </main>
  );
}
