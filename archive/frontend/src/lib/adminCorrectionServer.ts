import { createCipheriv, createDecipheriv, createHash, randomBytes } from 'crypto';
import type { AdminCorrectionLoginPayload, AdminCorrectionSession } from '@/types/wallet';

const SESSION_COOKIE_NAME = 'admin_correction_session';
const SESSION_TTL_MS = 30 * 60 * 1000;

interface StoredAdminCorrectionSession {
  authorization: string;
  username: string;
  expiresAt: string;
}

interface ValidatedAdminCorrectionSession {
  session: AdminCorrectionSession;
  shouldClearSession: boolean;
}

function getBackendApiBaseUrl(): string {
  const configured = process.env.BACKEND_API_BASE_URL?.trim();
  return (configured && configured.length > 0 ? configured : 'http://localhost:8080').replace(/\/$/, '');
}

function getSessionSecret(): string {
  const secret = process.env.ADMIN_CORRECTION_SESSION_SECRET?.trim();
  if (!secret) {
    throw new Error('ADMIN_CORRECTION_SESSION_SECRET is required for admin correction session handling.');
  }
  return secret;
}

function getSessionKey(): Buffer {
  return createHash('sha256').update(getSessionSecret(), 'utf8').digest();
}

function encodeSession(session: StoredAdminCorrectionSession): string {
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', getSessionKey(), iv);
  const encrypted = Buffer.concat([
    cipher.update(JSON.stringify(session), 'utf8'),
    cipher.final()
  ]);
  const tag = cipher.getAuthTag();
  return `${iv.toString('base64url')}.${tag.toString('base64url')}.${encrypted.toString('base64url')}`;
}

function decodeSession(value: string | undefined): StoredAdminCorrectionSession | null {
  if (!value) {
    return null;
  }

  try {
    const [ivPart, tagPart, encryptedPart] = value.split('.');
    if (!ivPart || !tagPart || !encryptedPart) {
      return null;
    }

    const decipher = createDecipheriv(
      'aes-256-gcm',
      getSessionKey(),
      Buffer.from(ivPart, 'base64url')
    );
    decipher.setAuthTag(Buffer.from(tagPart, 'base64url'));

    const decrypted = Buffer.concat([
      decipher.update(Buffer.from(encryptedPart, 'base64url')),
      decipher.final()
    ]);
    const session = JSON.parse(decrypted.toString('utf8')) as StoredAdminCorrectionSession;
    if (!session.authorization || !session.username || !session.expiresAt) {
      return null;
    }
    if (Date.parse(session.expiresAt) <= Date.now()) {
      return null;
    }
    return session;
  } catch {
    return null;
  }
}

function toPublicSession(session: StoredAdminCorrectionSession): AdminCorrectionSession {
  return {
    authenticated: true,
    username: session.username,
    expiresAt: session.expiresAt
  };
}

function encodeBasicAuth(credentials: AdminCorrectionLoginPayload): string {
  const value = `${credentials.username}:${credentials.password}`;
  return `Basic ${Buffer.from(value, 'utf8').toString('base64')}`;
}

async function fetchBackendAdminCorrectionSession(authorization: string): Promise<Response> {
  return fetch(`${getBackendApiBaseUrl()}/api/admin-corrections/session`, {
    method: 'GET',
    headers: {
      Authorization: authorization
    },
    cache: 'no-store'
  });
}

export function getAdminCorrectionSessionCookieName(): string {
  return SESSION_COOKIE_NAME;
}

export function readAdminCorrectionSession(rawValue: string | undefined): StoredAdminCorrectionSession | null {
  return decodeSession(rawValue);
}

export function buildAdminCorrectionSessionCookie(session: StoredAdminCorrectionSession) {
  return {
    name: SESSION_COOKIE_NAME,
    value: encodeSession(session),
    options: {
      httpOnly: true,
      sameSite: 'strict' as const,
      secure: process.env.NODE_ENV === 'production',
      path: '/',
      expires: new Date(session.expiresAt)
    }
  };
}

export function buildClearedAdminCorrectionSessionCookie() {
  return {
    name: SESSION_COOKIE_NAME,
    value: '',
    options: {
      httpOnly: true,
      sameSite: 'strict' as const,
      secure: process.env.NODE_ENV === 'production',
      path: '/',
      expires: new Date(0)
    }
  };
}

export function unauthenticatedAdminCorrectionSession(): AdminCorrectionSession {
  return {
    authenticated: false,
    username: '',
    expiresAt: ''
  };
}

export async function createAdminCorrectionSession(
  credentials: AdminCorrectionLoginPayload
): Promise<{ stored: StoredAdminCorrectionSession; session: AdminCorrectionSession }> {
  const authorization = encodeBasicAuth(credentials);
  const response = await fetchBackendAdminCorrectionSession(authorization);

  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || `Admin correction auth failed: ${response.status}`);
  }

  const body = (await response.json()) as { username?: string };
  const username = body.username?.trim() || credentials.username.trim();
  const stored = {
    authorization,
    username,
    expiresAt: new Date(Date.now() + SESSION_TTL_MS).toISOString()
  };

  return {
    stored,
    session: toPublicSession(stored)
  };
}

export async function proxyAdminCorrectionRequest(
  path: string,
  body: string,
  session: StoredAdminCorrectionSession
): Promise<{ response: Response; shouldClearSession: boolean }> {
  const response = await fetch(`${getBackendApiBaseUrl()}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: session.authorization
    },
    body,
    cache: 'no-store'
  });

  return {
    response,
    shouldClearSession: response.status === 401 || response.status === 403
  };
}

export async function validateAdminCorrectionSession(
  session: StoredAdminCorrectionSession | null
): Promise<ValidatedAdminCorrectionSession> {
  if (!session) {
    return {
      session: unauthenticatedAdminCorrectionSession(),
      shouldClearSession: false
    };
  }

  const response = await fetchBackendAdminCorrectionSession(session.authorization);
  if (response.status === 401 || response.status === 403) {
    return {
      session: unauthenticatedAdminCorrectionSession(),
      shouldClearSession: true
    };
  }
  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || `Admin correction session validation failed: ${response.status}`);
  }

  const body = (await response.json()) as { username?: string };
  return {
    session: {
      authenticated: true,
      username: body.username?.trim() || session.username,
      expiresAt: session.expiresAt
    },
    shouldClearSession: false
  };
}

export function toPublicAdminCorrectionSession(
  session: StoredAdminCorrectionSession | null
): AdminCorrectionSession {
  if (!session) {
    return unauthenticatedAdminCorrectionSession();
  }
  return toPublicSession(session);
}
