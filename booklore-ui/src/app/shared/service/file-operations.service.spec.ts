import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {of, throwError} from 'rxjs';
import {firstValueFrom} from 'rxjs';
import {FileOperationsService, type FileMoveRequest} from './file-operations.service';
import {mockHttpClientProvider} from '../../../testing/providers';

const baseUrl = 'http://localhost:6060/api/v1/files';

describe('FileOperationsService', () => {
  let service: FileOperationsService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        FileOperationsService,
        mockHttpClientProvider
      ]
    });

    service = TestBed.inject(FileOperationsService);
    httpClient = TestBed.inject(HttpClient) as any;

    vi.resetAllMocks();
    httpClient.post.mockReturnValue(of(undefined));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('moveFiles', () => {
    it('should post a file move request', async () => {
      const request: FileMoveRequest = {
        bookIds: [1, 2],
        moves: [
          {bookId: 1, targetLibraryId: 3, targetLibraryPathId: 4},
          {bookId: 2, targetLibraryId: 5, targetLibraryPathId: null}
        ]
      };
      httpClient.post.mockReturnValue(of(undefined));

      await firstValueFrom(service.moveFiles(request));

      expect(httpClient.post).toHaveBeenCalledWith(`${baseUrl}/move`, request);
    });

    it('should emit an error when the move request fails', async () => {
      const request: FileMoveRequest = {bookIds: [1], moves: []};
      const error = new Error('move failed');
      httpClient.post.mockReturnValue(throwError(() => error));

      await expect(firstValueFrom(service.moveFiles(request))).rejects.toThrow('move failed');
      expect(httpClient.post).toHaveBeenCalledWith(`${baseUrl}/move`, request);
    });
  });
});
