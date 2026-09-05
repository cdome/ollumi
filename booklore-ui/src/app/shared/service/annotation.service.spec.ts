import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom, of, throwError} from 'rxjs';
import {
  AnnotationService,
  Annotation,
  CreateAnnotationRequest,
  UpdateAnnotationRequest
} from './annotation.service';
import {mockHttpClientProvider} from '../../../testing/providers';

describe('AnnotationService', () => {
  let service: AnnotationService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AnnotationService,
        mockHttpClientProvider
      ]
    });

    service = TestBed.inject(AnnotationService);
    httpClient = TestBed.inject(HttpClient) as any;

    vi.resetAllMocks();
    httpClient.get.mockReturnValue(of([]));
    httpClient.post.mockReturnValue(of({}));
    httpClient.put.mockReturnValue(of({}));
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
    const annotations: Annotation[] = [
      {
        id: 1,
        bookId: 42,
        cfi: 'cfi-1',
        text: 'Important',
        color: 'yellow',
        style: 'highlight',
        createdAt: '2026-01-01T00:00:00Z'
      }
    ];
    httpClient.get.mockReturnValue(of(annotations));

    const result = await firstValueFrom(service.getAnnotationsForBook(42));

    expect(httpClient.get).toHaveBeenCalledWith('http://localhost:6060/api/v1/annotations/book/42');
    expect(result).toEqual(annotations);
  });

  it('should create an annotation', async () => {
    const request: CreateAnnotationRequest = {
      bookId: 42,
      cfi: 'cfi-2',
      text: 'Key point',
      color: 'green',
      style: 'underline'
    };
    const annotation: Annotation = {
      id: 2,
      bookId: 42,
      cfi: 'cfi-2',
      text: 'Key point',
      color: 'green',
      style: 'underline',
      createdAt: '2026-01-01T00:00:00Z'
    };
    httpClient.post.mockReturnValue(of(annotation));

    const result = await firstValueFrom(service.createAnnotation(request));

    expect(httpClient.post).toHaveBeenCalledWith('http://localhost:6060/api/v1/annotations', request);
    expect(result).toEqual(annotation);
  });

  it('should update an annotation', async () => {
    const request: UpdateAnnotationRequest = {color: 'pink', note: 'A comment'};
    const annotation: Annotation = {
      id: 1,
      bookId: 42,
      cfi: 'cfi-1',
      text: 'Important',
      color: 'pink',
      style: 'highlight',
      note: 'A comment',
      createdAt: '2026-01-01T00:00:00Z'
    };
    httpClient.put.mockReturnValue(of(annotation));

    const result = await firstValueFrom(service.updateAnnotation(1, request));

    expect(httpClient.put).toHaveBeenCalledWith('http://localhost:6060/api/v1/annotations/1', request);
    expect(result).toEqual(annotation);
  });

  it('should delete an annotation', async () => {
    httpClient.delete.mockReturnValue(of(undefined));

    const result = await firstValueFrom(service.deleteAnnotation(1));

    expect(httpClient.delete).toHaveBeenCalledWith('http://localhost:6060/api/v1/annotations/1');
    expect(result).toBeUndefined();
  });

  it('should propagate errors from the backend', () => {
    httpClient.put.mockReturnValue(throwError(() => ({message: 'update failed'})));

    let error: any;
    service.updateAnnotation(1, {}).subscribe({error: e => error = e});

    expect(error).toEqual({message: 'update failed'});
  });
});
