import {defineConfig, mergeConfig} from 'vitest/config';
import angular from '@analogjs/vite-plugin-angular';
import baseConfig from './vitest-base.config';

// Standalone vitest CLI entry point (`npx vitest`). The `ng test` builder uses
// vitest-base.config.ts directly and compiles Angular itself, so the analog
// plugin and setup file are only wired here.
export default mergeConfig(
  baseConfig,
  defineConfig({
    plugins: [angular({tsconfig: 'tsconfig.spec.json'})],
    test: {
      globals: true,
      include: ['src/**/*.spec.ts'],
      setupFiles: ['./src/test-setup.ts'],
      sequence: {
        hooks: 'stack'
      },
    },
  })
);
