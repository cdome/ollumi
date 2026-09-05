import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {HttpParams} from '@angular/common/http';
import {config, firstValueFrom, of, throwError} from 'rxjs';
import {BookMetadataManageService} from './book-metadata-manage.service';
import {BookStateService} from './book-state.service';
import {BookSocketService} from './book-socket.service';
import {BookService} from './book.service';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {
  mockHttpClientProvider,
  mockMessageServiceProvider,
  mockTranslocoServiceProvider
} from '../../../../testing/providers';
import {createMockBook, createMockBookMetadata} from '../../../../testing/factories';
import {BulkMetadataUpdateRequest, MetadataUpdateWrapper} from '../model/book.model';

describe('BookMetadataManageService', () => {
  let service: BookMetadataManageService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};
  let bookStateService: BookStateService;
  let messageService: {add: ReturnType<typeof vi.fn>; clear: ReturnType<typeof vi.fn>};
  let bookService: {refreshBooks: ReturnType<typeof vi.fn>};
  let originalUnhandledError: typeof config.onUnhandledError;

  beforeAll(() => {
    originalUnhandledError = config.onUnhandledError;
    config.onUnhandledError = () => {};
  });

  afterAll(async () => {
    await new Promise(resolve => setTimeout(resolve, 50));
    config.onUnhandledError = originalUnhandledError;
  });

  beforeEach(() => {
    bookService = {refreshBooks: vi.fn()};

    TestBed.configureTestingModule({
      providers: [
        BookMetadataManageService,
        BookStateService,
        BookSocketService,
        mockHttpClientProvider,
        mockMessageServiceProvider,
        mockTranslocoServiceProvider,
        {provide: BookService, useValue: bookService}
      ]
    });

    service = TestBed.inject(BookMetadataManageService);
    httpClient = TestBed.inject(HttpClient) as any;
    bookStateService = TestBed.inject(BookStateService);
    messageService = TestBed.inject(MessageService) as any;

    vi.clearAllMocks();
    httpClient.get.mockReturnValue(of({}));
    httpClient.post.mockReturnValue(of({}));
    httpClient.put.mockReturnValue(of(undefined));
    httpClient.delete.mockReturnValue(of({}));
    httpClient.patch.mockReturnValue(of({}));
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('updateBookMetadata', () => {
    it('should update metadata and refresh the book in state', async () => {
      const book = createMockBook({id: 1, metadata: createMockBookMetadata({bookId: 1, title: 'Old'})});
      const updatedMetadata = createMockBookMetadata({bookId: 1, title: 'New'});
      bookStateService.updateBookState({books: [book], loaded: true, error: null});
      httpClient.put.mockReturnValue(of(updatedMetadata));
      const wrapper: MetadataUpdateWrapper = {metadata: updatedMetadata, clearFlags: {}};

      const result = await firstValueFrom(service.updateBookMetadata(1, wrapper, true, 'REPLACE_ALL'));

      expect(httpClient.put).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/1/metadata',
        wrapper,
        expect.objectContaining({params: expect.any(HttpParams)})
      );
      const params = httpClient.put.mock.calls[0][2].params as HttpParams;
      expect(params.get('mergeCategories')).toBe('true');
      expect(params.get('replaceMode')).toBe('REPLACE_ALL');
      expect(result).toEqual(updatedMetadata);
      expect(bookStateService.getCurrentBookState().books![0].metadata?.title).toBe('New');
    });
  });

  describe('updateBooksMetadata', () => {
    it('should send a bulk metadata update request', async () => {
      const request: BulkMetadataUpdateRequest = {bookIds: [1, 2], authors: ['A']};
      httpClient.put.mockReturnValue(of({}));

      await firstValueFrom(service.updateBooksMetadata(request));

      expect(httpClient.put).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/bulk-edit-metadata',
        request
      );
    });
  });

  describe('toggleAllLock', () => {
    it('should lock metadata for all selected books and update state', async () => {
      const meta1 = createMockBookMetadata({bookId: 1, titleLocked: false});
      const meta2 = createMockBookMetadata({bookId: 2, titleLocked: false});
      const book1 = createMockBook({id: 1, metadata: meta1});
      const book2 = createMockBook({id: 2, metadata: meta2});
      bookStateService.updateBookState({books: [book1, book2], loaded: true, error: null});
      httpClient.put.mockReturnValue(of([
        {...meta1, titleLocked: true},
        {...meta2, titleLocked: true}
      ]));

      await firstValueFrom(service.toggleAllLock(new Set([1, 2]), 'LOCK'));

      expect(httpClient.put).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/metadata/toggle-all-lock',
        {bookIds: [1, 2], lock: 'LOCK'}
      );
      expect(bookStateService.getCurrentBookState().books!.every(b => b.metadata?.titleLocked)).toBe(true);
    });
  });

  describe('toggleFieldLocks', () => {
    it('should update lock flags for the given fields and book ids', async () => {
      const book = createMockBook({
        id: 1,
        metadata: createMockBookMetadata({bookId: 1, titleLocked: false, descriptionLocked: true})
      });
      bookStateService.updateBookState({books: [book], loaded: true, error: null});
      httpClient.put.mockReturnValue(of(undefined));

      await firstValueFrom(service.toggleFieldLocks([1], {title: 'LOCK', descriptionLocked: 'UNLOCK'}));

      expect(httpClient.put).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/metadata/toggle-field-locks',
        {bookIds: [1], fieldActions: {title: 'LOCK', descriptionLocked: 'UNLOCK'}}
      );
      const updatedMeta = bookStateService.getCurrentBookState().books![0].metadata;
      expect(updatedMeta?.titleLocked).toBe(true);
      expect(updatedMeta?.descriptionLocked).toBe(false);
    });

    it('should accept a Set of book ids', async () => {
      const book = createMockBook({id: 1, metadata: createMockBookMetadata({bookId: 1})});
      bookStateService.updateBookState({books: [book], loaded: true, error: null});
      httpClient.put.mockReturnValue(of(undefined));

      await firstValueFrom(service.toggleFieldLocks(new Set([1]), {publisher: 'LOCK'}));

      expect(httpClient.put).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/metadata/toggle-field-locks',
        {bookIds: [1], fieldActions: {publisher: 'LOCK'}}
      );
    });

    it('should show an error toast and rethrow on failure', async () => {
      httpClient.put.mockReturnValue(throwError(() => ({message: 'Lock failed'})));

      service.toggleFieldLocks([1], {title: 'LOCK'}).subscribe({error: () => {}});
      await new Promise(resolve => setTimeout(resolve, 0));

      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
    });
  });

  describe('consolidateMetadata', () => {
    it('should post a consolidation request and refresh books', async () => {
      httpClient.post.mockReturnValue(of({}));

      await firstValueFrom(service.consolidateMetadata('authors', ['A'], ['a', 'aa']));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/metadata/manage/consolidate',
        {metadataType: 'authors', targetValues: ['A'], valuesToMerge: ['a', 'aa']}
      );
      expect(bookService.refreshBooks).toHaveBeenCalled();
    });
  });

  describe('deleteMetadata', () => {
    it('should post a metadata deletion request and refresh books', async () => {
      httpClient.post.mockReturnValue(of({}));

      await firstValueFrom(service.deleteMetadata('tags', ['old']));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/metadata/manage/delete',
        {metadataType: 'tags', valuesToDelete: ['old']}
      );
      expect(bookService.refreshBooks).toHaveBeenCalled();
    });
  });

  describe('cover helpers', () => {
    it('should return the upload cover URL', () => {
      expect(service.getUploadCoverUrl(5)).toBe('http://localhost:6060/api/v1/books/5/metadata/cover/upload');
    });

    it('should upload a cover from a URL', async () => {
      const metadata = createMockBookMetadata();
      httpClient.post.mockReturnValue(of(metadata));

      const result = await firstValueFrom(service.uploadCoverFromUrl(1, 'http://cover.jpg'));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/1/metadata/cover/from-url',
        {url: 'http://cover.jpg'}
      );
      expect(result).toEqual(metadata);
    });

    it('should regenerate all covers', async () => {
      await firstValueFrom(service.regenerateCovers(true));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/regenerate-covers?missingOnly=true',
        {}
      );
    });

    it('should regenerate a single cover', async () => {
      await firstValueFrom(service.regenerateCover(3));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/3/regenerate-cover',
        {}
      );
    });

    it('should fetch file metadata', async () => {
      const metadata = createMockBookMetadata();
      httpClient.get.mockReturnValue(of(metadata));

      const result = await firstValueFrom(service.getFileMetadata(2));

      expect(httpClient.get).toHaveBeenCalledWith('http://localhost:6060/api/v1/books/2/file-metadata');
      expect(result).toEqual(metadata);
    });

    it('should generate a custom cover', async () => {
      await firstValueFrom(service.generateCustomCover(4));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/4/generate-custom-cover',
        {}
      );
    });

    it('should generate custom covers for multiple books', async () => {
      await firstValueFrom(service.generateCustomCoversForBooks([1, 2]));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/bulk-generate-custom-covers',
        {bookIds: [1, 2]}
      );
    });

    it('should regenerate covers for multiple books', async () => {
      await firstValueFrom(service.regenerateCoversForBooks([3, 4]));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/bulk-regenerate-covers',
        {bookIds: [3, 4]}
      );
    });
  });

  describe('audiobook cover helpers', () => {
    it('should upload an audiobook cover from a URL', async () => {
      const metadata = createMockBookMetadata();
      httpClient.post.mockReturnValue(of(metadata));

      const result = await firstValueFrom(service.uploadAudiobookCoverFromUrl(1, 'http://audio-cover.jpg'));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/1/metadata/audiobook-cover/from-url',
        {url: 'http://audio-cover.jpg'}
      );
      expect(result).toEqual(metadata);
    });

    it('should upload an audiobook cover from a file', async () => {
      const file = new File(['cover'], 'cover.jpg');
      httpClient.post.mockReturnValue(of(undefined));

      await firstValueFrom(service.uploadAudiobookCoverFromFile(1, file));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/1/metadata/audiobook-cover/upload',
        expect.any(FormData)
      );
    });

    it('should return the upload audiobook cover URL', () => {
      expect(service.getUploadAudiobookCoverUrl(2)).toBe(
        'http://localhost:6060/api/v1/books/2/metadata/audiobook-cover/upload'
      );
    });

    it('should regenerate an audiobook cover', async () => {
      await firstValueFrom(service.regenerateAudiobookCover(5));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/5/regenerate-audiobook-cover',
        {}
      );
    });

    it('should generate a custom audiobook cover', async () => {
      await firstValueFrom(service.generateCustomAudiobookCover(6));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/6/generate-custom-audiobook-cover',
        {}
      );
    });
  });

  describe('supportsDualCovers', () => {
    it('should return true when the book has both audiobook and ebook files', () => {
      const book = createMockBook({
        id: 1,
        primaryFile: {id: 1, bookId: 1, bookType: 'AUDIOBOOK'},
        alternativeFormats: [{id: 2, bookId: 1, bookType: 'EPUB'}]
      });

      expect(service.supportsDualCovers(book)).toBe(true);
    });

    it('should return false when the book only has ebook files', () => {
      const book = createMockBook({
        id: 1,
        primaryFile: {id: 1, bookId: 1, bookType: 'EPUB'},
        alternativeFormats: [{id: 2, bookId: 1, bookType: 'PDF'}]
      });

      expect(service.supportsDualCovers(book)).toBe(false);
    });
  });

  describe('bulkUploadCover', () => {
    it('should upload a cover for multiple books', async () => {
      const file = new File(['cover'], 'cover.jpg');
      httpClient.post.mockReturnValue(of(undefined));

      await firstValueFrom(service.bulkUploadCover([1, 2], file));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/bulk-upload-cover',
        expect.any(FormData)
      );
    });
  });
});
