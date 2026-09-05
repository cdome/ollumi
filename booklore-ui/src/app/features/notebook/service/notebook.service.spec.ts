import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient, HttpParams} from '@angular/common/http';
import {of} from 'rxjs';
import {firstValueFrom} from 'rxjs';
import {NotebookService} from './notebook.service';
import {mockHttpClientProvider} from '../../../../testing/providers';
import type {NotebookBookOption, NotebookEntry, NotebookPage} from '../model/notebook.model';

const baseUrl = 'http://localhost:6060/api/v1/notebook';

describe('NotebookService', () => {
  let service: NotebookService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        NotebookService,
        mockHttpClientProvider
      ]
    });

    service = TestBed.inject(NotebookService);
    httpClient = TestBed.inject(HttpClient) as any;

    vi.resetAllMocks();
    httpClient.get.mockReturnValue(of({}));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getNotebookEntries', () => {
    it('should fetch notebook entries with required parameters', async () => {
      const page: NotebookPage = {
        content: [] as NotebookEntry[],
        page: {totalElements: 0, totalPages: 0, number: 0, size: 10}
      };
      httpClient.get.mockReturnValue(of(page));

      const result = await firstValueFrom(service.getNotebookEntries(0, 10, ['HIGHLIGHT', 'NOTE'], null, '', 'createdAt,desc'));

      const expectedParams = new HttpParams()
        .set('page', 0)
        .set('size', 10)
        .set('sort', 'createdAt,desc')
        .append('types', 'HIGHLIGHT')
        .append('types', 'NOTE');

      expect(httpClient.get).toHaveBeenCalledWith(baseUrl, {params: expectedParams});
      expect(result).toEqual(page);
    });

    it('should include bookId and search when provided', async () => {
      const page: NotebookPage = {
        content: [] as NotebookEntry[],
        page: {totalElements: 1, totalPages: 1, number: 0, size: 10}
      };
      httpClient.get.mockReturnValue(of(page));

      const result = await firstValueFrom(service.getNotebookEntries(0, 10, ['NOTE'], 42, 'search-term', 'updatedAt,desc'));

      const expectedParams = new HttpParams()
        .set('page', 0)
        .set('size', 10)
        .set('sort', 'updatedAt,desc')
        .append('types', 'NOTE')
        .set('bookId', 42)
        .set('search', 'search-term');

      expect(httpClient.get).toHaveBeenCalledWith(baseUrl, {params: expectedParams});
      expect(result).toEqual(page);
    });

    it('should trim search text', async () => {
      httpClient.get.mockReturnValue(of({content: [], page: {totalElements: 0, totalPages: 0, number: 0, size: 10}}));

      await firstValueFrom(service.getNotebookEntries(0, 10, [], null, '  trimmed  ', 'createdAt,desc'));

      const params = httpClient.get.mock.calls[0][1].params as HttpParams;
      expect(params.get('search')).toBe('trimmed');
    });
  });

  describe('getExportEntries', () => {
    it('should fetch export entries with required parameters', async () => {
      const entries: NotebookEntry[] = [];
      httpClient.get.mockReturnValue(of(entries));

      const result = await firstValueFrom(service.getExportEntries(['HIGHLIGHT'], null, '', 'createdAt,desc'));

      const expectedParams = new HttpParams()
        .set('sort', 'createdAt,desc')
        .append('types', 'HIGHLIGHT');

      expect(httpClient.get).toHaveBeenCalledWith(`${baseUrl}/export`, {params: expectedParams});
      expect(result).toEqual(entries);
    });

    it('should include bookId and search when provided', async () => {
      const entries: NotebookEntry[] = [];
      httpClient.get.mockReturnValue(of(entries));

      const result = await firstValueFrom(service.getExportEntries(['NOTE'], 7, 'term', 'updatedAt,desc'));

      const expectedParams = new HttpParams()
        .set('sort', 'updatedAt,desc')
        .append('types', 'NOTE')
        .set('bookId', 7)
        .set('search', 'term');

      expect(httpClient.get).toHaveBeenCalledWith(`${baseUrl}/export`, {params: expectedParams});
      expect(result).toEqual(entries);
    });
  });

  describe('getBooksWithAnnotations', () => {
    it('should fetch books without search', async () => {
      const books: NotebookBookOption[] = [{bookId: 1, bookTitle: 'Book One'}];
      httpClient.get.mockReturnValue(of(books));

      const result = await firstValueFrom(service.getBooksWithAnnotations());

      expect(httpClient.get).toHaveBeenCalledWith(`${baseUrl}/books`, {params: new HttpParams()});
      expect(result).toEqual(books);
    });

    it('should fetch books with trimmed search', async () => {
      const books: NotebookBookOption[] = [{bookId: 2, bookTitle: 'Book Two'}];
      httpClient.get.mockReturnValue(of(books));

      const result = await firstValueFrom(service.getBooksWithAnnotations('  query  '));

      const expectedParams = new HttpParams().set('search', 'query');

      expect(httpClient.get).toHaveBeenCalledWith(`${baseUrl}/books`, {params: expectedParams});
      expect(result).toEqual(books);
    });
  });
});
