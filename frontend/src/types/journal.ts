export type JournalStatus = 'AUTO_CLASSIFIED' | 'MANUAL_CLASSIFIED' | 'REVIEW_REQUIRED' | 'APPROVED';

export interface JournalLine {
  id: number | null;
  accountCode: string;
  debitAmount: string;
  creditAmount: string;
  tokenSymbol: string | null;
  tokenQuantity: string | null;
}

export interface Journal {
  id: number | null;
  rawTransactionId: number;
  entryDate: string;
  description: string;
  status: JournalStatus;
  memo: string | null;
  lines: JournalLine[];
}

export interface JournalDetail {
  journal: Journal;
  txHash: string | null;
  classifierId: string | null;
  priceSource: string | null;
}
