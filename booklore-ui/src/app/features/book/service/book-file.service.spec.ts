import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {config, firstValueFrom, of, throwError} from 'rxjs';
import {BookFileService} from './book-file.service';
import {BookStateService} from './book-state.service';
import {FileDownloadService} from '../../../shared/service/file-download.service';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {
  mockHttpClientProvider,
  mockMessageServiceProvider,
  mockTranslocoServiceProvider
} from '../../../../testing/providers';
import {createMockBook, createMockBookMetadata} from '../../../../testing/factories';
import {AdditionalFileType} from '../model/book.model';

describe('BookFileService', () => {
  let service: BookFileService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};
  let bookStateService: BookStateService;
  let messageService: {add: ReturnType<typeof vi.fn>; clear: ReturnType<typeof vi.fn>};
  let fileDownloadService: {downloadFile: ReturnType<typeof vi.fn>};
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
    fileDownloadService = {downloadFile: vi.fn()};

    TestBed.configureTestingModule({
      providers: [
        BookFileService,
        BookStateService,
        mockHttpClientProvider,
        mockMessageServiceProvider,
        mockTranslocoServiceProvider,
        {provide: FileDownloadService, useValue: fileDownloadService}
      ]
    });

    service = TestBed.inject(BookFileService);
    httpClient = TestBed.inject(HttpClient) as any;
    bookStateService = TestBed.inject(BookStateService);
    messageService = TestBed.inject(MessageService) as any;

    vi.clearAllMocks();
    httpClient.get.mockReturnValue(of(new Blob()));
    httpClient.post.mockReturnValue(of({}));
    httpClient.put.mockReturnValue(of(undefined));
    httpClient.delete.mockReturnValue(of(undefined));
    httpClient.patch.mockReturnValue(of({}));
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getFileContent', () => {
    it('should fetch the file content without a book type', async () => {
      await firstValueFrom(service.getFileContent(1));

      expect(httpClient.get).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/1/content',
        {responseType: 'blob' as 'json'}
      );
    });

    it('should append the book type query parameter when provided', async () => {
      await firstValueFrom(service.getFileContent(2, 'PDF'));

      expect(httpClient.get).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/2/content?bookType=PDF',
        {responseType: 'blob' as 'json'}
      );
    });
  });

  describe('downloadFile', () => {
    it('should delegate to the file download service with the primary file name', () => {
      const book = createMockBook({id: 1, primaryFile: {id: 1, bookId: 1, bookType: 'EPUB', fileName: 'book.epub'}});

      service.downloadFile(book);

      expect(fileDownloadService.downloadFile).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/1/download',
        'book.epub'
      );
    });

    it('should fall back to a generic file name when no primary file exists', () => {
      const book = createMockBook({id: 2, primaryFile: undefined});

      service.downloadFile(book);

      expect(fileDownloadService.downloadFile).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/2/download',
        'book'
      );
    });
  });

  describe('downloadAllFiles', () => {
    it('should generate a sanitized zip name from the metadata title', () => {
      const book = createMockBook({id: 1, metadata: createMockBookMetadata({title: 'A:B/C Book'})});

      service.downloadAllFiles(book);

      expect(fileDownloadService.downloadFile).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/1/download-all',
        'A_B_C_Book.zip'
      );
    });

    it('should fall back to a generic zip name when there is no title', () => {
      const book = createMockBook({id: 2, metadata: createMockBookMetadata({title: undefined})});

      service.downloadAllFiles(book);

      expect(fileDownloadService.downloadFile).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/2/download-all',
        'book-2.zip'
      );
    });
  });

  describe('deleteAdditionalFile', () => {
    it('should remove a supplementary file from the state and show a success toast', async () => {
      const file: AdditionalFile = {id: 5, bookId: 1, additionalFileType: AdditionalFileType.SUPPLEMENTARY};
      const book = createMockBook({id: 1, supplementaryFiles: [file], alternativeFormats: []});
      bookStateService.updateBookState({books: [book], loaded: true, error: null});

      await firstValueFrom(service.deleteAdditionalFile(1, 5));

      expect(httpClient.delete).toHaveBeenCalledWith('http://localhost:6060/api/v1/books/1/files/5');
      expect(bookStateService.getCurrentBookState().books![0].supplementaryFiles).toEqual([]);
      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    });

    it('should remove an alternative format from the state', async () => {
      const file: AdditionalFile = {id: 6, bookId: 1, additionalFileType: AdditionalFileType.ALTERNATIVE_FORMAT, bookType: 'PDF'};
      const book = createMockBook({id: 1, alternativeFormats: [file]});
      bookStateService.updateBookState({books: [book], loaded: true, error: null});

      await firstValueFrom(service.deleteAdditionalFile(1, 6));

      expect(bookStateService.getCurrentBookState().books![0].alternativeFormats).toEqual([]);
    });

    it('should show an error toast and rethrow on failure', async () => {
      const error = {error: {message: 'Delete failed'}};
      httpClient.delete.mockReturnValue(throwError(() => error));

      service.deleteAdditionalFile(1, 5).subscribe({error: () => {}});
      await new Promise(resolve => setTimeout(resolve, 0));

      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
    });
  });

  describe('deleteBookFile', () => {
    it('should promote an alternative format when the primary file is deleted', async () => {
      const primary: AdditionalFile = {id: 1, bookId: 1, bookType: 'EPUB'};
      const alt: AdditionalFile = {id: 2, bookId: 1, bookType: 'PDF'};
      const book = createMockBook({id: 1, primaryFile: primary, alternativeFormats: [alt]});
      bookStateService.updateBookState({books: [book], loaded: true, error: null});

      await firstValueFrom(service.deleteBookFile(1, 1, true));

      const updated = bookStateService.getCurrentBookState().books![0];
      expect(updated.primaryFile).toEqual(alt);
      expect(updated.alternativeFormats).toEqual([]);
    });

    it('should clear the primary file when deleting it with no alternatives', async () => {
      const primary: AdditionalFile = {id: 1, bookId: 1, bookType: 'EPUB'};
      const book = createMockBook({id: 1, primaryFile: primary, alternativeFormats: []});
      bookStateService.updateBookState({books: [book], loaded: true, error: null});

      await firstValueFrom(service.deleteBookFile(1, 1, true));

      const updated = bookStateService.getCurrentBookState().books![0];
      expect(updated.primaryFile).toBeUndefined();
    });

    it('should remove a non-primary alternative format', async () => {
      const alt1: AdditionalFile = {id: 2, bookId: 1, bookType: 'PDF'};
      const alt2: AdditionalFile = {id: 3, bookId: 1, bookType: 'MOBI'};
      const book = createMockBook({id: 1, alternativeFormats: [alt1, alt2]});
      bookStateService.updateBookState({books: [book], loaded: true, error: null});

      await firstValueFrom(service.deleteBookFile(1, 2, false));

      expect(bookStateService.getCurrentBookState().books![0].alternativeFormats).toEqual([alt2]);
    });
  });

  describe('uploadAdditionalFile', () => {
    it('should upload an alternative format and infer the book type from the extension', async () => {
      const file = new File(['content'], 'chapter.pdf', {type: 'application/pdf'});
      const newFile: AdditionalFile = {id: 7, bookId: 1, bookType: 'PDF'};
      const book = createMockBook({id: 1, alternativeFormats: []});
      bookStateService.updateBookState({books: [book], loaded: true, error: null});
      httpClient.post.mockReturnValue(of(newFile));

      await firstValueFrom(service.uploadAdditionalFile(1, file, AdditionalFileType.ALTERNATIVE_FORMAT));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/1/files',
        expect.any(FormData)
      );
      expect(bookStateService.getCurrentBookState().books![0].alternativeFormats).toEqual([newFile]);
    });

    it('should add a supplementary file to the state on success', async () => {
      const file = new File(['content'], 'notes.txt', {type: 'text/plain'});
      const newFile: AdditionalFile = {id: 8, bookId: 1, additionalFileType: AdditionalFileType.SUPPLEMENTARY};
      const book = createMockBook({id: 1, supplementaryFiles: []});
      bookStateService.updateBookState({books: [book], loaded: true, error: null});
      httpClient.post.mockReturnValue(of(newFile));

      await firstValueFrom(service.uploadAdditionalFile(1, file, AdditionalFileType.SUPPLEMENTARY));

      expect(bookStateService.getCurrentBookState().books![0].supplementaryFiles).toEqual([newFile]);
      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    });

    it('should show an error toast and rethrow on upload failure', async () => {
      const file = new File(['content'], 'notes.txt');
      httpClient.post.mockReturnValue(throwError(() => ({message: 'Upload failed'})));

      service.uploadAdditionalFile(1, file, AdditionalFileType.SUPPLEMENTARY).subscribe({error: () => {}});
      await new Promise(resolve => setTimeout(resolve, 0));

      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
    });
  });

  describe('downloadAdditionalFile', () => {
    it('should download a file by id', () => {
      const file: AdditionalFile = {id: 3, bookId: 1, fileName: 'extra.pdf'};
      const book = createMockBook({id: 1, alternativeFormats: [file], supplementaryFiles: []});

      service.downloadAdditionalFile(book, 3);

      expect(fileDownloadService.downloadFile).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/1/files/3/download',
        'extra.pdf'
      );
    });
  });

  describe('detachBookFile', () => {
    it('should replace the source book and add the new book to the state', async () => {
      const sourceBook = createMockBook({id: 1, fileName: 'old.epub'});
      const newBook = createMockBook({id: 2, fileName: 'new.epub'});
      const response = {sourceBook, newBook};
      bookStateService.updateBookState({books: [sourceBook], loaded: true, error: null});
      httpClient.post.mockReturnValue(of(response));

      await firstValueFrom(service.detachBookFile(1, 1, true));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/1/files/1/detach',
        {copyMetadata: true}
      );
      expect(bookStateService.getCurrentBookState().books).toEqual([sourceBook, newBook]);
      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    });
  });

  describe('findDuplicates', () => {
    it('should post a duplicate detection request', async () => {
      const request = {libraryId: 1, matchByIsbn: true, matchByExternalId: false, matchByTitleAuthor: false, matchByDirectory: false, matchByFilename: false};
      httpClient.post.mockReturnValue(of([]));

      await firstValueFrom(service.findDuplicates(request));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/duplicates',
        request
      );
    });
  });

  describe('attachBookFiles', () => {
    it('should merge files, remove deleted source books, and refresh the state', async () => {
      const targetBook = createMockBook({id: 1});
      const sourceBook = createMockBook({id: 2});
      const updatedBook = createMockBook({id: 1, fileName: 'merged.epub'});
      bookStateService.updateBookState({books: [targetBook, sourceBook], loaded: true, error: null});
      httpClient.post.mockReturnValue(of({updatedBook, deletedSourceBookIds: [2]}));

      await firstValueFrom(service.attachBookFiles(1, [2], true));

      expect(httpClient.post).toHaveBeenCalledWith(
        'http://localhost:6060/api/v1/books/1/attach-file',
        {sourceBookIds: [2], moveFiles: true}
      );
      const state = bookStateService.getCurrentBookState().books;
      expect(state).toEqual([updatedBook]);
      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    });

    it('should show an error toast and rethrow on attachment failure', async () => {
      httpClient.post.mockReturnValue(throwError(() => ({message: 'Attach failed'})));

      service.attachBookFiles(1, [2], false).subscribe({error: () => {}});
      await new Promise(resolve => setTimeout(resolve, 0));

      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
    });
  });
});
