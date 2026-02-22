'use client';

import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { api } from '@/lib/api';
import { JournalDetailView } from '@/components/JournalDetail';
import { JournalEditForm } from '@/components/JournalEditForm';
import type { JournalDetail } from '@/types/journal';

export default function JournalDetailPage() {
  const params = useParams<{ id: string }>();
  const [detail, setDetail] = useState<JournalDetail | null>(null);

  async function load() {
    if (!params.id) return;
    const response = await api.getJournal(params.id);
    setDetail(response);
  }

  useEffect(() => {
    void load();
  }, [params.id]);

  if (!detail) {
    return <main className="mx-auto max-w-5xl px-6 py-12">로딩 중...</main>;
  }

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-5xl flex-col gap-4 px-6 py-12">
      <JournalDetailView detail={detail} />
      <JournalEditForm
        journal={detail.journal}
        onSave={async (payload) => {
          if (!detail.journal.id) return;
          await api.patchJournal(detail.journal.id, payload);
          await load();
        }}
      />
    </main>
  );
}
