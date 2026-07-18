import { FullConfig } from '@playwright/test';

export default async function globalSetup(config: FullConfig) {
  // webServer[0] (mock) and webServer[1] (jar) are BOTH healthy by the time
  // globalSetup runs — Playwright guarantees webServers boot before globalSetup.
  const base = config.projects[0].use.baseURL ?? 'http://localhost:8080';
  await seedUser(base, 'e2e-user-a@example.com', 'password123', 'User A');
  await seedUser(base, 'e2e-user-b@example.com', 'password123', 'User B');
}

async function seedUser(
  base: string,
  email: string,
  password: string,
  displayName: string,
): Promise<void> {
  const res = await fetch(`${base}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password, displayName }),
  });
  // 200 = created, 409 = already exists (safe on re-run)
  if (!res.ok && res.status !== 409) {
    throw new Error(`Failed to seed user ${email}: HTTP ${res.status}`);
  }
}
