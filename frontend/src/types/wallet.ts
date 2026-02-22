export type SyncStatus = 'PENDING' | 'SYNCING' | 'COMPLETED' | 'FAILED';

export interface Wallet {
  id: number | null;
  address: string;
  label: string | null;
  syncStatus: SyncStatus;
  lastSyncedAt: string | null;
  lastSyncedBlock: number | null;
}
