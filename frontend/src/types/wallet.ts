export type SyncStatus = 'PENDING' | 'SYNCING' | 'COMPLETED' | 'FAILED';
export type WalletMode = 'FULL' | 'BALANCE_FLOW_CUTOFF';
export type WalletSyncPhase =
  | 'NONE'
  | 'SNAPSHOT_PENDING'
  | 'SNAPSHOTTING'
  | 'SNAPSHOT_COMPLETED'
  | 'DELTA_SYNCING'
  | 'DELTA_COMPLETED'
  | 'FAILED';

export interface WalletCreatePayload {
  address: string;
  label?: string;
  mode?: WalletMode;
  cutoffBlock?: number;
  startBlock?: number;
  trackedTokens?: string[];
}

export interface Wallet {
  id: number | null;
  address: string;
  label: string | null;
  mode: WalletMode;
  syncPhase: WalletSyncPhase;
  syncStatus: SyncStatus;
  cutoffBlock: number | null;
  snapshotBlock: number | null;
  deltaSyncedBlock: number | null;
  trackedTokens: string[];
  lastSyncedAt: string | null;
  lastSyncedBlock: number | null;
}
