import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {ApplicationRef} from '@angular/core';
import {LibraryLoadingService} from './library-loading.service';
import {mockTranslocoServiceProvider} from '../../../testing/providers';

describe('LibraryLoadingService', () => {
  let service: LibraryLoadingService;
  let appRef: ApplicationRef;
  let attachViewSpy: ReturnType<typeof vi.spyOn>;
  let detachViewSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    document.body.innerHTML = '';
    document.body.style.overflow = '';

    TestBed.configureTestingModule({
      providers: [
        LibraryLoadingService,
        mockTranslocoServiceProvider
      ]
    });

    appRef = TestBed.inject(ApplicationRef);
    attachViewSpy = vi.spyOn(appRef, 'attachView');
    detachViewSpy = vi.spyOn(appRef, 'detachView');

    service = TestBed.inject(LibraryLoadingService);
  });

  afterEach(() => {
    attachViewSpy.mockRestore();
    detachViewSpy.mockRestore();
    document.body.innerHTML = '';
    document.body.style.overflow = '';
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  function getComponentInstance() {
    return (service as any).componentRef?.instance;
  }

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should create the loading component on first show and attach its view', () => {
    service.showBookLoadingProgress('Dune', 1, 5);

    const component = getComponentInstance();
    expect(component).toBeTruthy();
    expect(attachViewSpy).toHaveBeenCalledOnce();
    expect(document.body.style.overflow).toBe('hidden');
  });

  it('should update progress values on the component', () => {
    service.showBookLoadingProgress('Book One', 1, 5);

    service.updateProgress('Book Two', 3, 5);

    const component = getComponentInstance();
    expect(component.bookTitle).toBe('Book Two');
    expect(component.current).toBe(3);
    expect(component.percentage).toBe(60);
  });

  it('should not recreate the component when show is called again', () => {
    service.showBookLoadingProgress('First', 1, 5);
    service.showBookLoadingProgress('Second', 2, 5);

    const component = getComponentInstance();
    expect(component.bookTitle).toBe('Second');
    expect(attachViewSpy).toHaveBeenCalledOnce();
  });

  it('should mark completion when current reaches total', () => {
    service.showBookLoadingProgress('Done', 5, 5);

    const component = getComponentInstance();
    expect(component.isComplete).toBe(true);
    expect(component.percentage).toBe(100);
  });

  it('should detach and destroy the component when hiding', () => {
    service.showBookLoadingProgress('Last', 1, 5);
    const componentRef = (service as any).componentRef;
    const destroySpy = vi.spyOn(componentRef, 'destroy');

    service.hide();

    expect(detachViewSpy).toHaveBeenCalledOnce();
    expect(destroySpy).toHaveBeenCalledOnce();
    expect((service as any).componentRef).toBeNull();
    expect(document.body.style.overflow).toBe('');
  });

  it('should be safe to hide when nothing is shown', () => {
    expect(() => service.hide()).not.toThrow();
    expect(detachViewSpy).not.toHaveBeenCalled();
  });
});
