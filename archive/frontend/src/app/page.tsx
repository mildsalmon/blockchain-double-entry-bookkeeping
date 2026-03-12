import Link from 'next/link';

export default function HomePage() {
  return (
    <main className="mx-auto flex min-h-screen w-full max-w-5xl flex-col gap-8 px-6 py-14">
      <section className="rounded-2xl border border-slate-200 bg-white/85 p-8 shadow-lg shadow-slate-100">
        <h1 className="text-4xl font-bold text-ink">Blockchain Double-Entry Bookkeeping</h1>
        <p className="mt-4 max-w-2xl text-slate-600">
          Ethereum 거래를 회계 분개로 변환하고 검토/승인/내보내기까지 처리하는 감사 워크벤치 MVP입니다.
        </p>
        <div className="mt-8 flex gap-3">
          <Link className="rounded-lg bg-teal-700 px-4 py-2 text-sm font-semibold text-white" href="/dashboard">
            잔고 대시보드
          </Link>
          <Link className="rounded-lg bg-ink px-4 py-2 text-sm font-semibold text-white" href="/wallets">
            지갑 등록
          </Link>
          <Link className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700" href="/journals">
            분개 보기
          </Link>
          <Link className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700" href="/unclassified">
            미분류 처리
          </Link>
        </div>
      </section>
    </main>
  );
}
