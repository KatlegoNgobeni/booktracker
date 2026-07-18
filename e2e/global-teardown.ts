import { FullConfig } from '@playwright/test';

// Playwright manages webServer process teardown automatically.
// This file is a no-op — kept to satisfy globalTeardown config requirement.
export default async function globalTeardown(_config: FullConfig): Promise<void> {
  // no-op
}
