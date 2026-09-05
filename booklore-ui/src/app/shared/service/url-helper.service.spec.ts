import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {Router} from '@angular/router';
import {UrlHelperService} from './url-helper.service';
import {AuthService} from './auth.service';
import {BookService} from '../../features/book/service/book.service';
import {mockRouterProvider} from '../../../testing/providers';
import {createMockBook, createMockBookMetadata} from '../../../testing/factories';
import {BookType} from '../../features/book/model/book.model';

describe('UrlHelperService', () => {
  let service: UrlHelperService;
  let authServiceMock: {getInternalAccessToken: ReturnType<typeof vi.fn>};
  let bookServiceMock: {getBookByIdFromState: ReturnType<typeof vi.fn>};
  let router: Router;

  beforeEach(() => {
    authServiceMock = {getInternalAccessToken: vi.fn(() => 'test-token')};
    bookServiceMock = {getBookByIdFromState: vi.fn()};

    TestBed.configureTestingModule({
      providers: [
        UrlHelperService,
        {provide: AuthService, useValue: authServiceMock},
        {provide: BookService, useValue: bookServiceMock},
        mockRouterProvider
      ]
    });

    service = TestBed.inject(UrlHelperService);
    router = TestBed.inject(Router);

    vi.clearAllMocks();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('thumbnail URLs', () => {
    it('should return a direct thumbnail URL without a token', () => {
      authServiceMock.getInternalAccessToken.mockReturnValue(null);

      const url = service.getDirectThumbnailUrl(1);

      expect(url).toBe('http://localhost:6060/api/v1/media/book/1/thumbnail');
    });

    it('should append the token and coverUpdatedOn to a direct thumbnail URL', () => {
      const url = service.getDirectThumbnailUrl(1, '123456');

      expect(url).toBe('http://localhost:6060/api/v1/media/book/1/thumbnail?123456&token=test-token');
    });

    it('should return a thumbnail URL when coverUpdatedOn is provided', () => {
      const url = service.getThumbnailUrl(1, '123456');

      expect(url).toBe('http://localhost:6060/api/v1/media/book/1/thumbnail?123456&token=test-token');
    });

    it('should generate a fallback cover when no coverUpdatedOn and book has metadata', () => {
      const book = createMockBook({
        id: 1,
        metadata: createMockBookMetadata({title: 'Fallback', authors: ['Author']})
      });
      bookServiceMock.getBookByIdFromState.mockReturnValue(book);

      const url = service.getThumbnailUrl(1);

      expect(url.startsWith('data:image/svg+xml;base64,')).toBe(true);
    });

    it('should return a plain thumbnail URL when no coverUpdatedOn and book is not in state', () => {
      bookServiceMock.getBookByIdFromState.mockReturnValue(undefined);

      const url = service.getThumbnailUrl(1);

      expect(url).toBe('http://localhost:6060/api/v1/media/book/1/thumbnail?token=test-token');
    });
  });

  describe('cover URLs', () => {
    it('should return a cover URL with token and coverUpdatedOn', () => {
      const url = service.getCoverUrl(1, 'updated');

      expect(url).toBe('http://localhost:6060/api/v1/media/book/1/cover?updated&token=test-token');
    });

    it('should generate a fallback cover when no coverUpdatedOn and book has metadata', () => {
      const book = createMockBook({
        id: 1,
        metadata: createMockBookMetadata({title: 'Fallback Cover', authors: ['Author']})
      });
      bookServiceMock.getBookByIdFromState.mockReturnValue(book);

      const url = service.getCoverUrl(1);

      expect(url.startsWith('data:image/svg+xml;base64,')).toBe(true);
    });

    it('should return a backup cover URL', () => {
      const url = service.getBackupCoverUrl(1);

      expect(url).toBe('http://localhost:6060/api/v1/media/book/1/backup-cover?token=test-token');
    });
  });

  describe('audiobook URLs', () => {
    it('should return an audiobook cover URL with token and updatedOn', () => {
      const url = service.getAudiobookCoverUrl(1, 'audioupdated');

      expect(url).toBe('http://localhost:6060/api/v1/media/book/1/audiobook-cover?audioupdated&token=test-token');
    });

    it('should generate a square fallback cover when no audiobookCoverUpdatedOn and book has metadata', () => {
      const book = createMockBook({
        id: 1,
        metadata: createMockBookMetadata({title: 'Audio Fallback', authors: ['Author']})
      });
      bookServiceMock.getBookByIdFromState.mockReturnValue(book);

      const url = service.getAudiobookCoverUrl(1);

      expect(url.startsWith('data:image/svg+xml;base64,')).toBe(true);
    });

    it('should return an audiobook thumbnail URL with token and updatedOn', () => {
      const url = service.getAudiobookThumbnailUrl(1, 'thumbupdated');

      expect(url).toBe('http://localhost:6060/api/v1/media/book/1/audiobook-thumbnail?thumbupdated&token=test-token');
    });
  });

  describe('bookdrop URLs', () => {
    it('should return a bookdrop cover URL with token', () => {
      const url = service.getBookdropCoverUrl(5);

      expect(url).toBe('http://localhost:6060/api/v1/media/bookdrop/5/cover?token=test-token');
    });
  });

  describe('navigation URLs', () => {
    it('should create a book detail URL', () => {
      const book = createMockBook({id: 42});
      const expectedUrlTree = {} as ReturnType<typeof router.createUrlTree>;
      vi.mocked(router.createUrlTree).mockReturnValue(expectedUrlTree);

      const result = service.getBookUrl(book);

      expect(router.createUrlTree).toHaveBeenCalledWith(['/book', 42], {
        queryParams: {tab: 'view'}
      });
      expect(result).toBe(expectedUrlTree);
    });

    it('should create a PDF reading URL', () => {
      const book = createMockBook({id: 1, primaryFile: {id: 1, bookId: 1, bookType: 'PDF' as BookType}});

      service.getBookPrimaryReadingUrl(book);

      expect(router.createUrlTree).toHaveBeenCalledWith(['/pdf-reader/book/1'], undefined);
    });

    it('should create an EPUB reading URL', () => {
      const book = createMockBook({id: 2, primaryFile: {id: 1, bookId: 2, bookType: 'EPUB' as BookType}});

      service.getBookPrimaryReadingUrl(book);

      expect(router.createUrlTree).toHaveBeenCalledWith(['/ebook-reader/book/2'], undefined);
    });

    it('should create a CBX reading URL', () => {
      const book = createMockBook({id: 3, primaryFile: {id: 1, bookId: 3, bookType: 'CBX' as BookType}});

      service.getBookPrimaryReadingUrl(book);

      expect(router.createUrlTree).toHaveBeenCalledWith(['/cbx-reader/book/3'], undefined);
    });

    it('should create an audiobook reading URL', () => {
      const book = createMockBook({
        id: 4,
        primaryFile: {id: 1, bookId: 4, bookType: 'AUDIOBOOK' as BookType}
      });

      service.getBookPrimaryReadingUrl(book);

      expect(router.createUrlTree).toHaveBeenCalledWith(['/audiobook-player/book/4'], undefined);
    });

    it('should fall back to the book URL for an unsupported book type', () => {
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
      const book = createMockBook({
        id: 5,
        primaryFile: {id: 1, bookId: 5, bookType: 'UNKNOWN' as unknown as BookType}
      });

      service.getBookPrimaryReadingUrl(book);

      expect(consoleSpy).toHaveBeenCalledWith('Unsupported book type:', 'UNKNOWN');
      expect(router.createUrlTree).toHaveBeenCalledWith(['/book', 5], {queryParams: {tab: 'view'}});
      consoleSpy.mockRestore();
    });

    it('should create a series filter URL', () => {
      service.filterBooksBy('series', 'Dune');

      expect(router.createUrlTree).toHaveBeenCalledWith(['/series', encodeURIComponent('Dune')]);
    });

    it('should create an all-books filter URL', () => {
      service.filterBooksBy('author', 'Frank Herbert');

      expect(router.createUrlTree).toHaveBeenCalledWith(['/all-books'], {
        queryParams: {
          view: 'grid',
          sort: 'title',
          direction: 'asc',
          sidebar: true,
          filter: `author:${encodeURIComponent('Frank Herbert')}`
        }
      });
    });
  });
});
