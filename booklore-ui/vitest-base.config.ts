import {defineConfig} from 'vitest/config';

// Shared vitest settings for both entry points:
//  - `ng test` (@angular/build:unit-test builder, runnerConfig: this file)
//  - `npx vitest` (vitest.config.ts merges this file)
export default defineConfig({
  test: {
    environment: 'jsdom',
    isolate: true,
    reporters: [
      ['default', {summary: false}],
      ['junit', {outputFile: 'test-results/vitest-results.xml'}]
    ],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      include: ['src/app/**/*.ts'],
      exclude: ['src/app/**/*.spec.ts', 'src/app/**/*.module.ts'],
      // Baseline floor (2026-07). Raise as service tests land — never lower.
      thresholds: {
        statements: 23,
        branches: 24,
        functions: 14,
        lines: 21,
      },
    },
  },
});
