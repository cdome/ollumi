import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom, of} from 'rxjs';
import {BookdropService, BookdropFileStatus} from './bookdrop.service';
import {mockHttpClientProvider} from '../../../../testing/providers';

describe('BookdropService', () => {
  let service: BookdropService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        BookdropService,
        mockHttpClientProvider
      ]
    });

    service = TestBed.inject(BookdropService);
    httpClient = TestBed.inject(HttpClient) as any;

    vi.clearAllMocks();
    httpClient.get.mockReturnValue(of({content: [], page: {totalElements: 0, totalPages: 0, number: 0, size: 50}}));
    httpClient.post.mockReturnValue(of({}));
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getPendingFiles', () => {
    it('should fetch pending files with default pagination', async () => {
      const page = {content: [], page: {totalElements: 0, totalPages: 0, number: 0, size: 50}};
      httpClient.get.mockReturnValue(of(page));

      const result = await firstValueFrom(service.getPendingFiles());

      expect(httpClient.get).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/bookdrop/files?status=pending&page=0&size=50'
      );
      expect(result).toEqual(page);
    });

    it('should fetch pending files with custom pagination', async () => {
      const page = {
        content: [],
        page: {totalElements: 10, totalPages: 1, number: 2, size: 10}
      };
      httpClient.get.mockReturnValue(of(page));

      const result = await firstValueFrom(service.getPendingFiles(2, 10));

      expect(httpClient.get).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/bookdrop/files?status=pending&page=2&size=10'
      );
      expect(result).toEqual(page);
    });
  });

  describe('finalizeImport', () => {
    it('should post the finalize payload and return the result', async () => {
      const payload = {
        selectAll: true,
        excludedIds: [1],
        defaultLibraryId: 2,
        defaultPathId: 3
      };
      const result = {
        totalFiles: 1,
        successfullyImported: 1,
        failed: 0,
        processedAt: '2026-01-01T00:00:00.000Z',
        results: [{fileName: 'book.epub', success: true, message: 'OK'}]
      };
      httpClient.post.mockReturnValue(of(result));

      const emitted = await firstValueFrom(service.finalizeImport(payload));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/bookdrop/imports/finalize',
        payload
      );
      expect(emitted).toEqual(result);
    });
  });

  describe('discardFiles', () => {
    it('should post the discard payload', async () => {
      const payload = {selectAll: true, excludedIds: [1], selectedIds: [2, 3]};
      httpClient.post.mockReturnValue(of(undefined));

      await firstValueFrom(service.discardFiles(payload));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/bookdrop/files/discard',
        payload
      );
    });
  });

  describe('rescan', () => {
    it('should trigger a rescan', async () => {
      httpClient.post.mockReturnValue(of(undefined));

      await firstValueFrom(service.rescan());

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/bookdrop/rescan',
        {}
      );
    });
  });

  describe('extractFromPattern', () => {
    it('should post a pattern extraction request', async () => {
      const payload = {
        pattern: '{title} - {author}',
        selectAll: true,
        preview: true
      };
      const result = {
        totalFiles: 2,
        successfullyExtracted: 2,
        failed: 0,
        results: []
      };
      httpClient.post.mockReturnValue(of(result));

      const emitted = await firstValueFrom(service.extractFromPattern(payload));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/bookdrop/files/extract-pattern',
        payload
      );
      expect(emitted).toEqual(result);
    });
  });

  describe('bulkEditMetadata', () => {
    it('should post a bulk edit request and return the result', async () => {
      const payload = {
        fields: {title: 'Updated'},
        enabledFields: ['title'],
        mergeArrays: false,
        selectAll: true
      };
      const result = {totalFiles: 3, successfullyUpdated: 3, failed: 0};
      httpClient.post.mockReturnValue(of(result));

      const emitted = await firstValueFrom(service.bulkEditMetadata(payload));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/bookdrop/files/bulk-edit',
        payload
      );
      expect(emitted).toEqual(result);
    });
  });
});
