import {defineConfig} from 'vitest/config';
import angular from '@analogjs/vite-plugin-angular';

export default defineConfig({
  plugins: [angular({tsconfig: 'tsconfig.spec.json'})],
  test: {
    globals: true,
    environment: 'jsdom',
    isolate: true,
    setupFiles: ['./src/test-setup.ts'],
    include: ['src/app/**/*.spec.ts'],
    exclude: ['node_modules/**', 'e2e/**'],
    reporters: [
      ['default', {summary: false}],
      ['junit', {outputFile: 'test-results/vitest-results.xml'}]
    ],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      include: ['src/app/**/*.ts'],
      exclude: ['src/app/**/*.spec.ts', 'src/app/**/*.module.ts'],
      thresholds: {
        statements: 42.5,
        branches: 28,
        functions: 38.5,
        lines: 42.75
      }
    }
  }
});
