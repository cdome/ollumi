import 'zone.js';
import 'zone.js/testing';
import {TestBed} from '@angular/core/testing';
import {BrowserDynamicTestingModule, platformBrowserDynamicTesting} from '@angular/platform-browser-dynamic/testing';

// Only initialize if not already initialized
const testGlobal = globalThis as typeof globalThis & { __ANGULAR_TESTBED_INITIALIZED__?: boolean };
if (!testGlobal.__ANGULAR_TESTBED_INITIALIZED__) {
  testGlobal.__ANGULAR_TESTBED_INITIALIZED__ = true;
  TestBed.initTestEnvironment(
    BrowserDynamicTestingModule,
    platformBrowserDynamicTesting(),
    {teardown: {destroyAfterEach: true}}
  );
}
