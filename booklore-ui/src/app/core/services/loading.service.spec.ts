import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {LoadingService} from './loading.service';

describe('LoadingService', () => {
  let service: LoadingService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [LoadingService]
    });
    service = TestBed.inject(LoadingService);
    document.body.innerHTML = '';
    document.body.style.cursor = 'default';
  });

  afterEach(() => {
    service.hideAll();
    document.body.innerHTML = '';
    document.body.style.cursor = 'default';
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should show a fullscreen loader with the default message', () => {
    const loader = service.show();

    expect(document.body.contains(loader)).toBe(true);
    expect(loader.className).toBe('fullscreen-loader');
    expect(loader.textContent).toContain('Loading...');
    expect(document.body.style.cursor).toBe('wait');
  });

  it('should show a loader with a custom message', () => {
    const loader = service.show('Saving changes...');

    expect(loader.textContent).toContain('Saving changes...');
  });

  it('should hide the loader and reset the cursor', () => {
    const loader = service.show();
    service.hide(loader);

    expect(document.body.contains(loader)).toBe(false);
    expect(document.body.style.cursor).toBe('default');
  });

  it('should hide only the targeted loader while others remain visible', () => {
    const loader1 = service.show('First');
    const loader2 = service.show('Second');

    service.hide(loader1);

    expect(document.body.contains(loader1)).toBe(false);
    expect(document.body.contains(loader2)).toBe(true);
    expect(document.body.style.cursor).toBe('wait');

    service.hide(loader2);
    expect(document.body.style.cursor).toBe('default');
  });

  it('should hide all loaders at once', () => {
    const loader1 = service.show('First');
    const loader2 = service.show('Second');

    service.hideAll();

    expect(document.body.contains(loader1)).toBe(false);
    expect(document.body.contains(loader2)).toBe(false);
    expect(document.body.style.cursor).toBe('default');
  });

  it('should do nothing when hiding an unknown loader element', () => {
    const unknown = document.createElement('div');

    service.hide(unknown);

    expect(document.body.style.cursor).toBe('default');
  });
});
