'use client';

import { useState } from 'react';
import type { Journal } from '@/types/journal';

interface JournalEditFormProps {
  journal: Journal;
  onSave: (payload: { lines: any[]; memo: string }) => Promise<void>;
}

export function JournalEditForm({ journal, onSave }: JournalEditFormProps) {
  const [memo, setMemo] = useState(journal.memo ?? '');
  const [saving, setSaving] = useState(false);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setSaving(true);
    try {
      await onSave({ lines: journal.lines, memo });
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="rounded-xl border border-slate-200 bg-white/90 p-4" onSubmit={handleSubmit}>
      <h3 className="text-base font-semibold text-ink">분개 수정</h3>
      <textarea
        className="mt-3 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
        rows={3}
        value={memo}
        onChange={(e) => setMemo(e.target.value)}
        placeholder="메모를 입력하세요"
      />
      <button className="mt-3 rounded-lg bg-ink px-4 py-2 text-sm font-semibold text-white disabled:opacity-60" disabled={saving}>
        {saving ? '저장 중...' : '저장'}
      </button>
    </form>
  );
}
