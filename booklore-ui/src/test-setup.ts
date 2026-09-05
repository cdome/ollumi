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
