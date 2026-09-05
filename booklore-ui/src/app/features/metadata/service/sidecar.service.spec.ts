import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {Subscription, of, throwError} from 'rxjs';
import {SidecarService, type SidecarMetadata} from './sidecar.service';
import {mockHttpClientProvider} from '../../../../testing/providers';

describe('SidecarService', () => {
  let service: SidecarService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};
  let subscriptions: Subscription[];
  const baseUrl = 'http://localhost:6060/api/v1';

  beforeEach(() => {
    subscriptions = [];

    TestBed.configureTestingModule({
      providers: [
        SidecarService,
        mockHttpClientProvider
      ]
    });

    service = TestBed.inject(SidecarService);
    httpClient = TestBed.inject(HttpClient) as any;

    vi.resetAllMocks();
    httpClient.get.mockReturnValue(of({}));
    httpClient.post.mockReturnValue(of({}));
  });

  afterEach(() => {
    subscriptions.forEach(s => s.unsubscribe());
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch sidecar content for a book', () => {
    const metadata = {
      version: '1.0',
      generatedAt: '2026-01-01T00:00:00Z',
      generatedBy: 'test',
      metadata: {title: 'Test Book'}
    };
    httpClient.get.mockReturnValue(of(metadata));

    let result: SidecarMetadata | undefined;
    subscriptions.push(service.getSidecarContent(7).subscribe(m => result = m));

    expect(httpClient.get).toHaveBeenCalledWith(`${baseUrl}/books/7/sidecar`);
    expect(result).toEqual(metadata);
  });

  it('should fetch sidecar sync status for a book', () => {
    const status = {status: 'IN_SYNC' as const};
    httpClient.get.mockReturnValue(of(status));

    let result: {status: string} | undefined;
    subscriptions.push(service.getSyncStatus(7).subscribe(s => result = s));

    expect(httpClient.get).toHaveBeenCalledWith(`${baseUrl}/books/7/sidecar/status`);
    expect(result).toEqual(status);
  });

  it('should export sidecar metadata for a book', () => {
    const response = {message: 'Exported'};
    httpClient.post.mockReturnValue(of(response));

    let result: typeof response | undefined;
    subscriptions.push(service.exportToSidecar(7).subscribe(r => result = r));

    expect(httpClient.post).toHaveBeenCalledWith(`${baseUrl}/books/7/sidecar/export`, {});
    expect(result).toEqual(response);
  });

  it('should import sidecar metadata for a book', () => {
    const response = {message: 'Imported'};
    httpClient.post.mockReturnValue(of(response));

    let result: typeof response | undefined;
    subscriptions.push(service.importFromSidecar(7).subscribe(r => result = r));

    expect(httpClient.post).toHaveBeenCalledWith(`${baseUrl}/books/7/sidecar/import`, {});
    expect(result).toEqual(response);
  });

  it('should bulk-export sidecars for a library', () => {
    const response = {message: 'Bulk export complete', exported: 5};
    httpClient.post.mockReturnValue(of(response));

    let result: typeof response | undefined;
    subscriptions.push(service.bulkExport(3).subscribe(r => result = r));

    expect(httpClient.post).toHaveBeenCalledWith(`${baseUrl}/libraries/3/sidecar/export-all`, {});
    expect(result).toEqual(response);
  });

  it('should bulk-import sidecars for a library', () => {
    const response = {message: 'Bulk import complete', imported: 5};
    httpClient.post.mockReturnValue(of(response));

    let result: typeof response | undefined;
    subscriptions.push(service.bulkImport(3).subscribe(r => result = r));

    expect(httpClient.post).toHaveBeenCalledWith(`${baseUrl}/libraries/3/sidecar/import-all`, {});
    expect(result).toEqual(response);
  });

  it('should propagate errors from the backend', () => {
    httpClient.get.mockReturnValue(throwError(() => new Error('sidecar not found')));

    let capturedError: Error | undefined;
    subscriptions.push(
      service.getSidecarContent(7).subscribe({
        error: err => capturedError = err
      })
    );

    expect(capturedError?.message).toBe('sidecar not found');
  });
});
