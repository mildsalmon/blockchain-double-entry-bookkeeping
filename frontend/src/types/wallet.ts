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
  reviewedBy?: string;
  preflightSummaryHash?: string;
  label?: string;
  mode?: WalletMode;
  cutoffBlock?: number;
  startBlock?: number;
  trackedTokens?: string[];
}

export interface WalletTokenPreview {
  tokenAddress: string | null;
  tokenSymbol: string;
  displayLabel: string;
  firstSeenBlock: number | null;
  firstSeenAt: string | null;
}

export interface WalletOmittedSuspected {
  tokenAddress: string;
  tokenSymbol: string;
  displayLabel: string;
  firstSeenBlock: number;
  firstSeenAt: string | null;
  reason: string;
}

export interface WalletCutoffSignOff {
  reviewedBy: string;
  reviewedAt: string;
  cutoffBlock: number;
  seededTokenCount: number;
  summaryHash: string;
}

export interface WalletCutoffPreflight {
  address: string;
  cutoffBlock: number;
  includesNativeEth: boolean;
  seededTokens: WalletTokenPreview[];
  summaryHash: string;
  warning: string;
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
  seededTokens: WalletTokenPreview[];
  discoveredTokens: WalletTokenPreview[];
  omittedSuspectedTokens: WalletOmittedSuspected[];
  latestCutoffSignOff: WalletCutoffSignOff | null;
  lastSyncedAt: string | null;
  lastSyncedBlock: number | null;
}
