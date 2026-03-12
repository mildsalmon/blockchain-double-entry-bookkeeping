import { NextRequest, NextResponse } from 'next/server';
import {
  buildClearedAdminCorrectionSessionCookie,
  getAdminCorrectionSessionCookieName,
  proxyAdminCorrectionRequest,
  readAdminCorrectionSession
} from '@/lib/adminCorrectionServer';

export const runtime = 'nodejs';

export async function POST(
  request: NextRequest,
  context: { params: Promise<{ address: string }> }
) {
  const session = readAdminCorrectionSession(
    request.cookies.get(getAdminCorrectionSessionCookieName())?.value
  );
  if (!session) {
    return NextResponse.json(
      { message: 'Admin correction session is not authenticated.' },
      { status: 401 }
    );
  }

  const { address } = await context.params;
  const body = await request.text();
  const { response: backendResponse, shouldClearSession } = await proxyAdminCorrectionRequest(
    `/api/wallets/${address}/admin-corrections/apply`,
    body,
    session
  );
  const responseBody = await backendResponse.text();

  const response = new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: {
      'Content-Type': backendResponse.headers.get('content-type') ?? 'application/json'
    }
  });
  if (shouldClearSession) {
    const clearedCookie = buildClearedAdminCorrectionSessionCookie();
    response.cookies.set(clearedCookie.name, clearedCookie.value, clearedCookie.options);
  }
  return response;
}
