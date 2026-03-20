import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  retries: 1,
  use: {
    baseURL: process.env.REGISTRY_URL || 'https://registry.ssdid.my',
    extraHTTPHeaders: {
      'Content-Type': 'application/json',
    },
  },
  reporter: [
    ['html', { open: 'never' }],
    ['list'],
  ],
  projects: [
    {
      name: 'registry-api',
      testMatch: /registry-.*\.spec\.ts/,
    },
    {
      name: 'drive-integration',
      testMatch: /drive-.*\.spec\.ts/,
    },
    {
      name: 'deeplink',
      testMatch: /deeplink-.*\.spec\.ts/,
    },
  ],
});
