import { NextRequest, NextResponse } from 'next/server';
import {
  buildAdminCorrectionSessionCookie,
  buildClearedAdminCorrectionSessionCookie,
  createAdminCorrectionSession,
  getAdminCorrectionSessionCookieName,
  readAdminCorrectionSession,
  unauthenticatedAdminCorrectionSession,
  validateAdminCorrectionSession
} from '@/lib/adminCorrectionServer';

export const runtime = 'nodejs';

export async function GET(request: NextRequest) {
  try {
    const storedSession = readAdminCorrectionSession(
      request.cookies.get(getAdminCorrectionSessionCookieName())?.value
    );
    const { session, shouldClearSession } = await validateAdminCorrectionSession(storedSession);
    const response = NextResponse.json(session);
    if (shouldClearSession) {
      const clearedCookie = buildClearedAdminCorrectionSessionCookie();
      response.cookies.set(clearedCookie.name, clearedCookie.value, clearedCookie.options);
    }
    return response;
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Admin correction session validation failed';
    return NextResponse.json({ message }, { status: 502 });
  }
}

export async function POST(request: NextRequest) {
  try {
    const payload = (await request.json()) as { username?: string; password?: string };
    const username = payload.username?.trim() ?? '';
    const password = payload.password ?? '';
    if (!username || !password) {
      return NextResponse.json(
        { message: 'username and password are required' },
        { status: 400 }
      );
    }

    const { stored, session } = await createAdminCorrectionSession({ username, password });
    const response = NextResponse.json(session);
    const cookie = buildAdminCorrectionSessionCookie(stored);
    response.cookies.set(cookie.name, cookie.value, cookie.options);
    return response;
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Admin correction sign-in failed';
    return NextResponse.json({ message }, { status: 401 });
  }
}

export async function DELETE() {
  const response = NextResponse.json(unauthenticatedAdminCorrectionSession());
  const cookie = buildClearedAdminCorrectionSessionCookie();
  response.cookies.set(cookie.name, cookie.value, cookie.options);
  return response;
}
