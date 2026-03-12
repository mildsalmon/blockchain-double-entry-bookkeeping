'use client';

interface BulkApproveBarProps {
  selectedIds: number[];
  onApprove: () => Promise<void>;
}

export function BulkApproveBar({ selectedIds, onApprove }: BulkApproveBarProps) {
  if (selectedIds.length === 0) return null;

  return (
    <div className="flex items-center justify-between rounded-xl border border-mint/40 bg-mint/10 px-4 py-3">
      <p className="text-sm text-slate-700">{selectedIds.length}건 선택됨</p>
      <button className="rounded-lg bg-mint px-4 py-2 text-sm font-semibold text-white" onClick={() => void onApprove()}>
        일괄 승인
      </button>
    </div>
  );
}
