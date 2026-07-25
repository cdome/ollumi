import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {provideTanStackQuery, QueryClient} from '@tanstack/angular-query-experimental';

import {BookSocketService} from './book-socket.service';
import {BOOKS_QUERY_KEY, bookDetailQueryPrefix} from './book-query-keys';
import {Book} from '../model/book.model';
import {makeBook} from '../../../testing/book.fixture';

describe('BookSocketService', () => {
  let service: BookSocketService;
  let qc: QueryClient;

  beforeEach(() => {
    qc = new QueryClient();
    TestBed.configureTestingModule({
      providers: [provideTanStackQuery(qc), BookSocketService],
    });
    service = TestBed.inject(BookSocketService);
  });

  const list = () => qc.getQueryData(BOOKS_QUERY_KEY) as Book[];

  it('handleNewlyCreatedBook upserts into the list cache', () => {
    qc.setQueryData(BOOKS_QUERY_KEY, [makeBook({id: 1})]);
    service.handleNewlyCreatedBook(makeBook({id: 2}));
    expect(list().map(b => b.id)).toEqual([1, 2]);
  });

  it('handleBookUpdate replaces the matching book in the list', () => {
    qc.setQueryData(BOOKS_QUERY_KEY, [makeBook({id: 1, metadata: {title: 'Old'}})]);
    service.handleBookUpdate(makeBook({id: 1, metadata: {title: 'New'}}));
    expect(list()[0].metadata!.title).toBe('New');
  });

  it('handleMultipleBookUpdates patches several books at once', () => {
    qc.setQueryData(BOOKS_QUERY_KEY, [makeBook({id: 1, personalRating: 1}), makeBook({id: 2, personalRating: 2})]);
    service.handleMultipleBookUpdates([
      makeBook({id: 1, personalRating: 8}),
      makeBook({id: 2, personalRating: 9}),
    ]);
    expect(list().map(b => b.personalRating)).toEqual([8, 9]);
  });

  it('handleRemovedBookIds invalidates the list and removes detail queries', () => {
    const invalidate = vi.spyOn(qc, 'invalidateQueries');
    const remove = vi.spyOn(qc, 'removeQueries');
    service.handleRemovedBookIds([3, 4]);
    expect(invalidate).toHaveBeenCalledWith({queryKey: BOOKS_QUERY_KEY, exact: true});
    expect(remove).toHaveBeenCalledWith({queryKey: bookDetailQueryPrefix(3)});
    expect(remove).toHaveBeenCalledWith({queryKey: bookDetailQueryPrefix(4)});
  });

  it('handleBookMetadataUpdate invalidates the list and the book detail', () => {
    const invalidate = vi.spyOn(qc, 'invalidateQueries');
    service.handleBookMetadataUpdate(5);
    expect(invalidate).toHaveBeenCalledWith({queryKey: BOOKS_QUERY_KEY, exact: true});
    expect(invalidate).toHaveBeenCalledWith({queryKey: bookDetailQueryPrefix(5)});
  });

  describe('handleMultipleBookCoverPatches', () => {
    it('merges coverUpdatedOn into the metadata of matching books only', () => {
      qc.setQueryData(BOOKS_QUERY_KEY, [
        makeBook({id: 1, metadata: {title: 'A', coverUpdatedOn: 'old'}}),
        makeBook({id: 2, metadata: {title: 'B', coverUpdatedOn: 'old'}}),
      ]);

      service.handleMultipleBookCoverPatches([{id: 1, coverUpdatedOn: 'fresh'}]);

      const [first, second] = list();
      expect(first.metadata!.coverUpdatedOn).toBe('fresh');
      expect(first.metadata!.title).toBe('A');
      expect(second.metadata!.coverUpdatedOn).toBe('old');
    });

    it('leaves a book without metadata untouched', () => {
      qc.setQueryData(BOOKS_QUERY_KEY, [makeBook({id: 1, metadata: undefined})]);
      service.handleMultipleBookCoverPatches([{id: 1, coverUpdatedOn: 'fresh'}]);
      expect(list()[0].metadata).toBeUndefined();
    });

    it('is a no-op for an empty patch list', () => {
      const setData = vi.spyOn(qc, 'setQueryData');
      service.handleMultipleBookCoverPatches([]);
      expect(setData).not.toHaveBeenCalled();
    });

    it('invalidates detail queries for each patched cover', () => {
      const invalidate = vi.spyOn(qc, 'invalidateQueries');
      qc.setQueryData(BOOKS_QUERY_KEY, [makeBook({id: 1})]);
      service.handleMultipleBookCoverPatches([{id: 1, coverUpdatedOn: 'fresh'}]);
      expect(invalidate).toHaveBeenCalledWith({queryKey: bookDetailQueryPrefix(1)});
    });
  });
});
