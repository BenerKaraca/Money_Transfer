import { defineConfig } from 'vitest/config'

export default defineConfig({
  server: {
    host: '127.0.0.1',
    proxy: { '/api': 'http://127.0.0.1:8082' },
  },
  test: {
    environment: 'jsdom',
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}'],
      reporter: ['text', 'html', 'json-summary'],
      thresholds: { lines: 100, branches: 100, functions: 100, statements: 100 },
    },
  },
})
