export interface BalancePosition {
  walletAddress: string;
  accountCode: string;
  tokenSymbol: string;
  quantity: string;
  lastEntryDate: string;
}

export interface BalanceSummary {
  walletCount: number;
  tokenCount: number;
  positionCount: number;
}

export interface BalanceDashboard {
  generatedAt: string;
  summary: BalanceSummary;
  positions: BalancePosition[];
}
