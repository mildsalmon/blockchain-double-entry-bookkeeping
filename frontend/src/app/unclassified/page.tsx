'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { ManualClassifyForm } from '@/components/ManualClassifyForm';

interface UnclassifiedEvent {
  id: number;
  classifierId: string;
  rawTransactionId: number;
  tokenSymbol: string | null;
}

export default function UnclassifiedPage() {
  const [events, setEvents] = useState<UnclassifiedEvent[]>([]);

  async function load() {
    const response = await api.listUnclassified();
    setEvents(response);
  }

  useEffect(() => {
    void load();
  }, []);

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-5xl flex-col gap-4 px-6 py-12">
      <h1 className="text-3xl font-bold text-ink">미분류 거래</h1>
      <div className="grid gap-3">
        {events.map((event) => (
          <article key={event.id} className="rounded-xl border border-slate-200 bg-white/90 p-4">
            <p className="text-sm text-slate-600">event #{event.id} / tx #{event.rawTransactionId}</p>
            <p className="text-xs text-slate-500">classifier: {event.classifierId}</p>
            <div className="mt-3">
              <ManualClassifyForm
                eventId={event.id}
                onSubmit={async (eventId, payload) => {
                  await api.classifyUnclassified(eventId, payload);
                  await load();
                }}
              />
            </div>
          </article>
        ))}
        {events.length === 0 && <p className="text-sm text-slate-500">미분류 거래가 없습니다.</p>}
      </div>
    </main>
  );
}
