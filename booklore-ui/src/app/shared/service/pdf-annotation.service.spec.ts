import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom, of, throwError} from 'rxjs';
import {PdfAnnotationService} from './pdf-annotation.service';
import {mockHttpClientProvider} from '../../../testing/providers';

describe('PdfAnnotationService', () => {
  let service: PdfAnnotationService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        PdfAnnotationService,
        mockHttpClientProvider
      ]
    });

    service = TestBed.inject(PdfAnnotationService);
    httpClient = TestBed.inject(HttpClient) as any;

    vi.resetAllMocks();
    httpClient.get.mockReturnValue(of({data: ''}));
    httpClient.put.mockReturnValue(of(undefined));
    httpClient.delete.mockReturnValue(of(undefined));
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch annotations for a book', async () => {
    const payload = {data: '[{"page":1}]'};
    httpClient.get.mockReturnValue(of(payload));

    const result = await firstValueFrom(service.getAnnotations(42));

    expect(httpClient.get).toHaveBeenCalledWith('http://localhost:6060/api/v1/pdf-annotations/book/42');
    expect(result).toEqual(payload);
  });

  it('should save annotations for a book', async () => {
    httpClient.put.mockReturnValue(of(undefined));

    const result = await firstValueFrom(service.saveAnnotations(42, 'annotation-data'));

    expect(httpClient.put).toHaveBeenCalledWith(
      'http://localhost:6060/api/v1/pdf-annotations/book/42',
      {data: 'annotation-data'}
    );
    expect(result).toBeUndefined();
  });

  it('should delete annotations for a book', async () => {
    httpClient.delete.mockReturnValue(of(undefined));

    const result = await firstValueFrom(service.deleteAnnotations(42));

    expect(httpClient.delete).toHaveBeenCalledWith('http://localhost:6060/api/v1/pdf-annotations/book/42');
    expect(result).toBeUndefined();
  });

  it('should propagate errors from the backend', () => {
    httpClient.get.mockReturnValue(throwError(() => ({message: 'fetch failed'})));

    let error: any;
    service.getAnnotations(42).subscribe({error: e => error = e});

    expect(error).toEqual({message: 'fetch failed'});
  });
});
