import { test, expect } from '@playwright/test';
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const AUTH_A = path.join(__dirname, '../playwright/.auth/user-a.json');

// Use User A's authenticated session for this entire file.
test.use({ storageState: AUTH_A });

/**
 * Extract the JWT bearer token from a Playwright storageState JSON file.
 * The token is stored in localStorage under "booktracker_token".
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
    // Auth file may not exist yet on first run — no-op
  }
  return null;
}

test('complete reading loop: search → shelf → progress → finish → rate → stats', async ({ page, request }) => {
  // Pre-condition cleanup: remove any existing Hobbit shelf entry so the test is idempotent
  // across re-runs (local dev shares a persistent E2E database).
  const jwt = readJwtFromStorageState(AUTH_A);
  if (jwt) {
    const headers = { Authorization: `Bearer ${jwt}` };
    // All four shelf statuses — iterate to find any existing Hobbit entry in any status bucket
    const allStatuses = ['WANT_TO_READ', 'CURRENTLY_READING', 'READ', 'ABANDONED'] as const;
    for (const shelfStatus of allStatuses) {
      const listRes = await request.get(`/api/shelf?status=${shelfStatus}&page=0&size=50`, { headers });
      if (listRes.ok()) {
        const body = await listRes.json() as { content?: Array<{ entryId: string; olKey: string }> };
        const entries = body.content ?? [];
        for (const entry of entries) {
          // olKey in the ShelfEntryDto is the short form (e.g. "OL82563W")
          if (entry.olKey === 'OL82563W' || entry.olKey === '/works/OL82563W') {
            await request.delete(`/api/shelf/${entry.entryId}`, { headers });
          }
        }
      }
    }
  }

  // Step 1 — Navigate to Search
  await page.goto('/search');

  // Step 2 — Search for The Hobbit (mock server on port 9999 returns the stub response)
  await page.getByRole('searchbox').fill('The Hobbit');
  await page.keyboard.press('Enter');
  // Step 3 — Wait for results and click first (each book result is wrapped in a <Link> with role='link')
  const searchResults = page.getByTestId('book-search-results');
  await expect(searchResults).toBeVisible({ timeout: 15_000 });
  await searchResults.getByRole('link').first().click();

  // Step 4 — Add to Want to Read shelf
  await page.getByRole('button', { name: 'Want to Read' }).click();
  await expect(page.getByText('On shelf: Want to Read')).toBeVisible({ timeout: 15_000 });

  // Step 5 — Navigate to Shelf via the bottom navigation bar
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'Shelf', exact: true }).click();
  await expect(page).toHaveURL('/shelf');

  // Step 6 — Switch to Want to Read tab (the book was added there)
  await page.getByRole('tab', { name: 'Want to Read' }).click();
  const firstShelfCard = page.getByTestId('shelf-entry-card').first();
  await expect(firstShelfCard).toBeVisible({ timeout: 15_000 });

  // Step 7 — Open the shelf entry editor
  await firstShelfCard.click();
  // Wait for navigation to the edit page (URL contains /shelf/ and /edit)
  await expect(page).toHaveURL(/\/shelf\/.*\/edit/, { timeout: 15_000 });

  // Step 8 — Change status to Currently Reading and set current page
  await page.getByLabel('Status').selectOption('CURRENTLY_READING');
  await page.getByLabel('Current Page').fill('150');
  await page.getByRole('button', { name: 'Update Progress' }).click();
  // Wait for the button to return to enabled state (not in "Saving…" state)
  await expect(page.getByRole('button', { name: 'Update Progress' })).toBeEnabled({ timeout: 15_000 });

  // Step 9 — Mark as Read
  await page.getByLabel('Status').selectOption('READ');

  // Step 10 — Rate 5 stars
  await page.getByRole('button', { name: 'Rate 5 stars' }).click();

  // Step 11 — Write review
  await page.getByLabel('Review').fill('An E2E test review.');

  // Step 12 — Save changes and return to shelf
  await page.getByRole('button', { name: 'Save Changes' }).click();
  await expect(page).toHaveURL('/shelf', { timeout: 15_000 });

  // Step 13 — Verify stats show at least 1 book read all-time
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'Stats' }).click();
  await expect(page).toHaveURL('/stats');
  await expect(page.getByTestId('stat-books-all-time')).toContainText('1', { timeout: 15_000 });
});
