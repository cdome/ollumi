import {beforeEach, describe, expect, it} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {firstValueFrom} from 'rxjs';
import {BookSelectionService, CheckboxClickEvent} from './book-selection.service';
import {createMockBook} from '../../../../../testing/factories';

describe('BookSelectionService', () => {
  let service: BookSelectionService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [BookSelectionService]
    });
    service = TestBed.inject(BookSelectionService);
  });

  it('should be created with an empty selection', async () => {
    expect(service).toBeTruthy();
    expect(await firstValueFrom(service.selectedBooks$)).toEqual(new Set());
    expect(service.selectedCount).toBe(0);
    expect(service.hasSelection()).toBe(false);
  });

  describe('selectBook / deselectBook', () => {
    it('should select a single book', async () => {
      const book = createMockBook({id: 7});

      service.selectBook(book);

      expect((await firstValueFrom(service.selectedBooks$)).has(7)).toBe(true);
      expect(service.selectedBooks.has(7)).toBe(true);
      expect(service.selectedCount).toBe(1);
      expect(service.hasSelection()).toBe(true);
    });

    it('should select all books in a series group', async () => {
      const seriesBook1 = createMockBook({id: 10});
      const seriesBook2 = createMockBook({id: 11});
      const book = createMockBook({
        id: 9,
        seriesBooks: [seriesBook1, seriesBook2]
      });

      service.selectBook(book);

      const selected = await firstValueFrom(service.selectedBooks$);
      expect(selected.has(10)).toBe(true);
      expect(selected.has(11)).toBe(true);
      expect(selected.has(9)).toBe(false);
    });

    it('should deselect a single book', async () => {
      const book = createMockBook({id: 7});
      service.selectBook(book);

      service.deselectBook(book);

      expect(service.selectedBooks.has(7)).toBe(false);
      expect(service.selectedCount).toBe(0);
    });

    it('should deselect all books in a series group', async () => {
      const seriesBook1 = createMockBook({id: 10});
      const seriesBook2 = createMockBook({id: 11});
      const book = createMockBook({
        id: 9,
        seriesBooks: [seriesBook1, seriesBook2]
      });
      service.selectBook(book);

      service.deselectBook(book);

      const selected = await firstValueFrom(service.selectedBooks$);
      expect(selected.has(10)).toBe(false);
      expect(selected.has(11)).toBe(false);
    });
  });

  describe('handleBookSelection', () => {
    it('should delegate to selectBook when selected is true', async () => {
      const book = createMockBook({id: 4});

      service.handleBookSelection(book, true);

      expect(service.selectedBooks.has(4)).toBe(true);
    });

    it('should delegate to deselectBook when selected is false', async () => {
      const book = createMockBook({id: 4});
      service.selectBook(book);

      service.handleBookSelection(book, false);

      expect(service.selectedBooks.has(4)).toBe(false);
    });
  });

  describe('handleCheckboxClick', () => {
    beforeEach(() => {
      service.setCurrentBooks([
        createMockBook({id: 1}),
        createMockBook({id: 2}),
        createMockBook({id: 3}),
        createMockBook({id: 4})
      ]);
    });

    it('should select a book on a normal click', () => {
      const event: CheckboxClickEvent = {index: 1, book: createMockBook({id: 2}), selected: true, shiftKey: false};

      service.handleCheckboxClick(event);

      expect(service.selectedBooks.has(2)).toBe(true);
      expect(service.selectedBooks.size).toBe(1);
    });

    it('should deselect a book on a normal uncheck', () => {
      service.selectBook(createMockBook({id: 2}));
      const event: CheckboxClickEvent = {index: 1, book: createMockBook({id: 2}), selected: false, shiftKey: false};

      service.handleCheckboxClick(event);

      expect(service.selectedBooks.has(2)).toBe(false);
    });

    it('should select a range with shift-click', () => {
      service.handleCheckboxClick({index: 0, book: createMockBook({id: 1}), selected: true, shiftKey: false});

      service.handleCheckboxClick({index: 2, book: createMockBook({id: 3}), selected: true, shiftKey: true});

      expect(service.selectedBooks.has(1)).toBe(true);
      expect(service.selectedBooks.has(2)).toBe(true);
      expect(service.selectedBooks.has(3)).toBe(true);
      expect(service.selectedBooks.has(4)).toBe(false);
    });

    it('should deselect a range with shift-uncheck', () => {
      service.selectAll();
      expect(service.selectedBooks.size).toBe(4);

      service.handleCheckboxClick({index: 0, book: createMockBook({id: 1}), selected: false, shiftKey: false});
      service.handleCheckboxClick({index: 2, book: createMockBook({id: 3}), selected: false, shiftKey: true});

      expect(service.selectedBooks.has(1)).toBe(false);
      expect(service.selectedBooks.has(2)).toBe(false);
      expect(service.selectedBooks.has(3)).toBe(false);
      expect(service.selectedBooks.has(4)).toBe(true);
    });
  });

  describe('selectAll / deselectAll', () => {
    it('should select all current books', async () => {
      service.setCurrentBooks([createMockBook({id: 1}), createMockBook({id: 2})]);

      service.selectAll();

      const selected = await firstValueFrom(service.selectedBooks$);
      expect(selected.has(1)).toBe(true);
      expect(selected.has(2)).toBe(true);
      expect(selected.size).toBe(2);
    });

    it('should do nothing when there are no current books', () => {
      service.setCurrentBooks([]);
      service.selectAll();

      expect(service.selectedBooks.size).toBe(0);
    });

    it('should clear the selection and reset the last index on deselectAll', () => {
      service.setCurrentBooks([createMockBook({id: 1}), createMockBook({id: 2})]);
      service.selectAll();
      service.handleCheckboxClick({index: 0, book: createMockBook({id: 1}), selected: true, shiftKey: false});

      service.deselectAll();

      expect(service.selectedBooks.size).toBe(0);
      expect(service.hasSelection()).toBe(false);
    });
  });

  describe('setSelectedBooks', () => {
    it('should replace the current selection', async () => {
      service.selectBook(createMockBook({id: 1}));

      service.setSelectedBooks(new Set([5, 6]));

      const selected = await firstValueFrom(service.selectedBooks$);
      expect(selected.has(5)).toBe(true);
      expect(selected.has(6)).toBe(true);
      expect(selected.has(1)).toBe(false);
    });
  });
});
