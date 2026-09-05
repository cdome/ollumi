import {Component, SimpleChange} from '@angular/core';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {Observable, of, throwError} from 'rxjs';
import {By} from '@angular/platform-browser';
import {SecureSrcDirective} from './secure-src.directive';

@Component({
  template: `<img [appSecureSrc]="url" data-fallback-src="fallback.jpg">`,
  standalone: true,
  imports: [SecureSrcDirective]
})
class TestHostComponent {
  url = 'http://localhost:6060/api/v1/cover/1';
}

@Component({
  template: `<img [appSecureSrc]="url">`,
  standalone: true,
  imports: [SecureSrcDirective]
})
class TestHostNoFallbackComponent {
  url = 'http://localhost:6060/api/v1/cover/2';
}

describe('SecureSrcDirective', () => {
  let http: { get: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    http = {get: vi.fn()};
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock-url');
    vi.spyOn(URL, 'revokeObjectURL').mockReturnValue(undefined);

    TestBed.configureTestingModule({
      imports: [TestHostComponent, TestHostNoFallbackComponent],
      providers: [{provide: HttpClient, useValue: http}]
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should set the element src to a blob URL when the image loads successfully', () => {
    http.get.mockReturnValue(of(new Blob(['image-data'])));
    const fixture = TestBed.createComponent(TestHostComponent);
    fixture.detectChanges();

    const img = fixture.nativeElement.querySelector('img');
    expect(http.get).toHaveBeenCalledWith('http://localhost:6060/api/v1/cover/1', {responseType: 'blob'});
    expect(img.getAttribute('src')).toBe('blob:mock-url');
  });

  it('should fall back to data-fallback-src on error', () => {
    http.get.mockReturnValue(throwError(() => new Error('load failed')));
    const fixture = TestBed.createComponent(TestHostComponent);
    fixture.detectChanges();

    const img = fixture.nativeElement.querySelector('img');
    expect(img.getAttribute('src')).toBe('fallback.jpg');
  });

  it('should fall back to the default placeholder when no data-fallback-src is present', () => {
    http.get.mockReturnValue(throwError(() => new Error('load failed')));
    const fixture = TestBed.createComponent(TestHostNoFallbackComponent);
    fixture.detectChanges();

    const img = fixture.nativeElement.querySelector('img');
    expect(img.getAttribute('src')).toBe('assets/images/missing-cover.jpg');
  });

  it('should unsubscribe from the previous request when the input changes', () => {
    const unsubscribeSpy = vi.fn();
    http.get.mockReturnValue(new Observable<Blob>(subscriber => {
      subscriber.next(new Blob(['image-data']));
      return unsubscribeSpy;
    }));

    const fixture = TestBed.createComponent(TestHostComponent);
    fixture.detectChanges();

    expect(unsubscribeSpy).not.toHaveBeenCalled();

    const directive = fixture.debugElement.query(By.directive(SecureSrcDirective)).injector.get(SecureSrcDirective);
    directive.src = 'http://localhost:6060/api/v1/cover/updated';
    directive.ngOnChanges({src: new SimpleChange('http://localhost:6060/api/v1/cover/1', directive.src, false)});

    expect(unsubscribeSpy).toHaveBeenCalledTimes(1);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-url');
  });

  it('should clean up the subscription and revoke the blob URL on destroy', () => {
    const unsubscribeSpy = vi.fn();
    http.get.mockReturnValue(new Observable<Blob>(subscriber => {
      subscriber.next(new Blob(['image-data']));
      return unsubscribeSpy;
    }));

    const fixture = TestBed.createComponent(TestHostComponent);
    fixture.detectChanges();
    fixture.destroy();

    expect(unsubscribeSpy).toHaveBeenCalledTimes(1);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-url');
  });
});
