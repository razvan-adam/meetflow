/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Same-origin API in development, as behind CloudFront in production (docs/architecture.md).
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // Vitest empties CSS files by default; src/styles/contrast.test.ts reads the colors in this one.
    css: { include: [/src\/styles\/global\.css/] },
    // Above asyncUtilTimeout in src/test/setup.ts, so a query that fails reports what it looked for.
    testTimeout: 15000,
    // Meeting times are entered and shown in local time; fixing it keeps the tests repeatable.
    env: { TZ: 'UTC' },
  },
})
