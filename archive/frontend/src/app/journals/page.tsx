'use client';

import { useEffect, useMemo, useState } from 'react';
import { api } from '@/lib/api';
import type { Journal } from '@/types/journal';
import { BulkApproveBar } from '@/components/BulkApproveBar';
import { ExportDialog } from '@/components/ExportDialog';
import { JournalFilters } from '@/components/JournalFilters';
import { JournalTable } from '@/components/JournalTable';

export default function JournalsPage() {
  const [journals, setJournals] = useState<Journal[]>([]);
  const [filters, setFilters] = useState<{ fromDate?: string; toDate?: string; status?: string }>({});

  const selectableIds = useMemo(() => journals.map((journal) => journal.id).filter((id): id is number => id !== null), [journals]);

  async function load() {
    const data = await api.listJournals(filters);
    setJournals(data);
  }

  useEffect(() => {
    void load();
  }, [filters.fromDate, filters.toDate, filters.status]);

  async function bulkApprove() {
    await api.bulkApprove(selectableIds);
    await load();
  }

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-6xl flex-col gap-4 px-6 py-12">
      <h1 className="text-3xl font-bold text-ink">분개 목록</h1>
      <JournalFilters onApply={setFilters} />
      <BulkApproveBar selectedIds={selectableIds} onApprove={bulkApprove} />
      <JournalTable journals={journals} />
      <ExportDialog />
    </main>
  );
}
