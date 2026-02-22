import type { JournalDetail } from '@/types/journal';

interface JournalDetailProps {
  detail: JournalDetail;
}

export function JournalDetailView({ detail }: JournalDetailProps) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white/90 p-5">
      <h2 className="text-xl font-semibold text-ink">분개 상세</h2>
      <dl className="mt-4 grid gap-2 text-sm text-slate-700">
        <div><dt className="font-semibold">설명</dt><dd>{detail.journal.description}</dd></div>
        <div><dt className="font-semibold">상태</dt><dd>{detail.journal.status}</dd></div>
        <div><dt className="font-semibold">분류 규칙</dt><dd>{detail.classifierId ?? '-'}</dd></div>
        <div><dt className="font-semibold">가격 출처</dt><dd>{detail.priceSource ?? '-'}</dd></div>
        <div>
          <dt className="font-semibold">원본 트랜잭션</dt>
          <dd>
            {detail.txHash ? (
              <a
                className="text-blue-600 underline"
                href={`https://etherscan.io/tx/${detail.txHash}`}
                target="_blank"
                rel="noreferrer"
              >
                {detail.txHash}
              </a>
            ) : '-'}
          </dd>
        </div>
      </dl>
    </section>
  );
}
