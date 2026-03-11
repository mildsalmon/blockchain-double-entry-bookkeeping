import type { Journal, JournalDetail } from '@/types/journal';
import type {
  AdminCorrectionLoginPayload,
  AdminCorrectionSession,
  Wallet,
  WalletAdminCorrectionApplyPayload,
  WalletAdminCorrectionPreflight,
  WalletAdminCorrectionPreflightPayload,
  WalletCreatePayload,
  WalletCutoffPreflight
} from '@/types/wallet';
import type { BalanceDashboard } from '@/types/dashboard';

const JSON_HEADERS = {
  'Content-Type': 'application/json'
};

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...options,
    headers: {
      ...JSON_HEADERS,
      ...(options?.headers ?? {})
    },
    cache: 'no-store'
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || `Request failed: ${response.status}`);
  }

  return response.json() as Promise<T>;
}

export const api = {
  getAdminCorrectionSession: () => request<AdminCorrectionSession>('/api/admin-correction/session'),
  createAdminCorrectionSession: (payload: AdminCorrectionLoginPayload) =>
    request<AdminCorrectionSession>('/api/admin-correction/session', {
      method: 'POST',
      body: JSON.stringify(payload)
    }),
  clearAdminCorrectionSession: async () => {
    const response = await fetch('/api/admin-correction/session', {
      method: 'DELETE',
      cache: 'no-store'
    });
    if (!response.ok) {
      const body = await response.text();
      throw new Error(body || `Request failed: ${response.status}`);
    }
  },
  createWallet: (payload: WalletCreatePayload) =>
    request<Wallet>('/api/wallets', {
      method: 'POST',
      body: JSON.stringify(payload)
    }),
  preflightCutoffWallet: (payload: Pick<WalletCreatePayload, 'address' | 'cutoffBlock' | 'trackedTokens'>) =>
    request<WalletCutoffPreflight>('/api/wallets/cutoff-preflight', {
      method: 'POST',
      body: JSON.stringify(payload)
    }),
  listWallets: () => request<Wallet[]>('/api/wallets'),
  getWalletStatus: (address: string) => request<Wallet>(`/api/wallets/${address}/status`),
  preflightWalletAdminCorrection: (address: string, payload: WalletAdminCorrectionPreflightPayload) =>
    request<WalletAdminCorrectionPreflight>(`/api/admin-corrections/${address}/preflight`, {
      method: 'POST',
      body: JSON.stringify(payload)
    }),
  applyWalletAdminCorrection: (address: string, payload: WalletAdminCorrectionApplyPayload) =>
    request<Wallet>(`/api/admin-corrections/${address}/apply`, {
      method: 'POST',
      body: JSON.stringify(payload)
    }),
  retryWallet: (address: string) =>
    request<Wallet>(`/api/wallets/${address}/retry`, {
      method: 'POST'
    }),
  deleteWallet: async (address: string) => {
    const response = await fetch(`/api/wallets/${address}`, {
      method: 'DELETE',
      cache: 'no-store'
    });
    if (!response.ok) {
      const body = await response.text();
      throw new Error(body || `Request failed: ${response.status}`);
    }
  },

  listJournals: (params: Record<string, string | number | undefined>) => {
    const query = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '') query.set(key, String(value));
    });
    return request<Journal[]>(`/api/journals?${query.toString()}`);
  },
  getJournal: (id: string) => request<JournalDetail>(`/api/journals/${id}`),
  patchJournal: (id: number, payload: unknown) =>
    request<Journal>(`/api/journals/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(payload)
    }),
  approveJournal: (id: number) =>
    request<Journal>(`/api/journals/${id}/approve`, {
      method: 'POST'
    }),
  bulkApprove: (ids: number[]) =>
    request<Journal[]>('/api/journals/bulk-approve', {
      method: 'POST',
      body: JSON.stringify({ ids })
    }),

  getDashboardBalances: (params: Record<string, string | number | undefined>) => {
    const query = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '') query.set(key, String(value));
    });
    return request<BalanceDashboard>(`/api/dashboard/balances?${query.toString()}`);
  },

  listUnclassified: () => request<any[]>('/api/unclassified'),
  classifyUnclassified: (id: number, payload: unknown) =>
    request<Journal>(`/api/unclassified/${id}/classify`, {
      method: 'POST',
      body: JSON.stringify(payload)
    }),

  exportJournals: async (payload: unknown) => {
    const response = await fetch('/api/export', {
      method: 'POST',
      headers: JSON_HEADERS,
      body: JSON.stringify(payload)
    });

    if (!response.ok) {
      throw new Error(await response.text());
    }

    return response.blob();
  }
};
