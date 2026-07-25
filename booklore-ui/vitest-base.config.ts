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
      exclude: ['src/app/**/*.spec.ts', 'src/app/**/*.module.ts', 'src/app/testing/**'],
      // Ratchet floor — raise as tests land, never lower.
      // WP0.3 23/24/14/21 → WP1.1 27/28/18/25 → WP1.2 29/30/21/27
      // → WP1.3 31/31/24/29 → WP2.2 32/31/24/30.
      thresholds: {
        statements: 32,
        branches: 31,
        functions: 24,
        lines: 30,
      },
    },
  },
});
