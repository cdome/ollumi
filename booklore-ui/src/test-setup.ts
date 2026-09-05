import 'zone.js';
import 'zone.js/testing';
import {getPlatform} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {BrowserDynamicTestingModule, platformBrowserDynamicTesting} from '@angular/platform-browser-dynamic/testing';

// Only initialize if the Angular test platform has not already been created.
// This keeps the setup safe when the Angular CLI test builder has already
// bootstrapped the platform internally.
if (!getPlatform() && !(globalThis as any).__ANGULAR_TESTBED_INITIALIZED__) {
  (globalThis as any).__ANGULAR_TESTBED_INITIALIZED__ = true;
  TestBed.initTestEnvironment(
    BrowserDynamicTestingModule,
    platformBrowserDynamicTesting(),
    {teardown: {destroyAfterEach: true}}
  );
}

// jsdom polyfills frequently required by PrimeNG / Chart.js components.
if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class {
    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
    constructor() {}
  } as any;
}
if (typeof globalThis.IntersectionObserver === 'undefined') {
  globalThis.IntersectionObserver = class {
    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
    root = null;
    rootMargin = '';
    thresholds: number[] = [];
    constructor() {}
  } as any;
}
if (typeof globalThis.matchMedia === 'undefined') {
  globalThis.matchMedia = vi.fn((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn()
  })) as any;
}

// jsdom does not implement Blob URL APIs used by favicon / upload previews.
if (typeof globalThis.URL.createObjectURL === 'undefined') {
  globalThis.URL.createObjectURL = vi.fn(() => 'blob:mock-url');
}
if (typeof globalThis.URL.revokeObjectURL === 'undefined') {
  globalThis.URL.revokeObjectURL = vi.fn();
}

// jsdom does not expose the Clipboard API.
if (typeof globalThis.navigator === 'undefined') {
  (globalThis as any).navigator = {};
}
if (!globalThis.navigator.clipboard) {
  globalThis.navigator.clipboard = {
    writeText: vi.fn(() => Promise.resolve())
  } as any;
}
