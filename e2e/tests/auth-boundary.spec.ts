import { test, expect, Browser } from '@playwright/test';
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const AUTH_A = path.join(__dirname, '../playwright/.auth/user-a.json');
const AUTH_B = path.join(__dirname, '../playwright/.auth/user-b.json');

// This test manages its own browser contexts — does NOT use project-level storageState.
// Start unauthenticated; the test creates separate contexts for each user.
test.use({ storageState: { cookies: [], origins: [] } });

/**
 * Extract the JWT bearer token from a Playwright storageState JSON file.
 */
function readJwtFromStorageState(storageStatePath: string): string | null {
  try {
    const data = JSON.parse(fs.readFileSync(storageStatePath, 'utf8'));
    const origins: Array<{ localStorage: Array<{ name: string; value: string }> }> = data.origins ?? [];
    for (const origin of origins) {
      const entry = (origin.localStorage ?? []).find((e) => e.name === 'booktracker_token');
      if (entry) return entry.value;
    }
  } catch {
    // Auth file may not exist yet — no-op
  }
  return null;
}

test('User B sees no User A data after sign-in boundary', async ({ browser, request }: { browser: Browser; request: import('@playwright/test').APIRequestContext }) => {
  // Pre-condition: ensure User A's Hobbit shelf entry is removed so we can add it fresh.
  // This makes the test idempotent across re-runs on a persistent local E2E database.
  const jwtA = readJwtFromStorageState(AUTH_A);
  if (jwtA) {
    const headers = { Authorization: `Bearer ${jwtA}` };
    const allStatuses = ['WANT_TO_READ', 'CURRENTLY_READING', 'READ', 'ABANDONED'] as const;
    for (const shelfStatus of allStatuses) {
      const listRes = await request.get(`/api/shelf?status=${shelfStatus}&page=0&size=50`, { headers });
      if (listRes.ok()) {
        const body = await listRes.json() as { content?: Array<{ entryId: string; olKey: string }> };
        for (const entry of (body.content ?? [])) {
          if (entry.olKey === 'OL82563W' || entry.olKey === '/works/OL82563W') {
            await request.delete(`/api/shelf/${entry.entryId}`, { headers });
          }
        }
      }
    }
  }

  // Part 1 — User A adds a book to shelf
  // Uses ctxA with User A's stored auth token — a distinct browser context from ctxB.
  const ctxA = await browser.newContext({ storageState: AUTH_A });
  const pageA = await ctxA.newPage();

  await pageA.goto('/search');
  await pageA.getByRole('searchbox').fill('The Hobbit');
  await pageA.keyboard.press('Enter');
  await expect(pageA.getByTestId('book-search-results')).toBeVisible({ timeout: 15_000 });
  await pageA.getByTestId('book-search-results').getByRole('link').first().click();

  // The book was just removed from User A's shelf (cleanup above), so the add buttons appear.
  // Click "Want to Read" to add the book.
  await pageA.getByRole('button', { name: 'Want to Read' }).click();
  await expect(pageA.getByText('On shelf: Want to Read')).toBeVisible({ timeout: 15_000 });

  await ctxA.close();

  // Part 2 — User B opens a fresh context and checks their shelf
  // User B has never added any books — their shelf must be empty.
  // This test pins the critical security property: User B's JWT returns only User B's data.
  const ctxB = await browser.newContext({ storageState: AUTH_B });
  const pageB = await ctxB.newPage();

  await pageB.goto('/shelf');
  // User B's shelf must be empty — no cards should render
  await expect(pageB.getByTestId('shelf-entry-card')).toHaveCount(0);
  // Additionally confirm The Hobbit title is not visible in User B's shelf
  await expect(pageB.getByText('The Hobbit')).toHaveCount(0);

  await ctxB.close();
});
