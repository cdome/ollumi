import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {firstValueFrom, of} from 'rxjs';
import {convertToParamMap} from '@angular/router';
import {BookFilterOrchestrationService} from './book-filter-orchestration.service';
import {SortService} from '../../service/sort.service';
import {BookState} from '../../model/state/book-state.model';
import {SortDirection, SortOption} from '../../model/sort.model';
import {Book} from '../../model/book.model';
import {HeaderFilter} from './filters/HeaderFilter';
import {SideBarFilter} from './filters/sidebar-filter';
import {SeriesCollapseFilter} from './filters/SeriesCollapseFilter';

describe('BookFilterOrchestrationService', () => {
  let service: BookFilterOrchestrationService;
  let applySortSpy: ReturnType<typeof vi.fn>;
  let headerFilterSpy: ReturnType<typeof vi.fn>;
  let sideBarFilterSpy: ReturnType<typeof vi.fn>;
  let seriesCollapseFilterSpy: ReturnType<typeof vi.fn>;

  const sortOption: SortOption = {label: 'Title', field: 'title', direction: SortDirection.ASCENDING};

  beforeEach(() => {
    applySortSpy = vi.fn((books: Book[]) => books);
    headerFilterSpy = vi.fn();
    sideBarFilterSpy = vi.fn();
    seriesCollapseFilterSpy = vi.fn();

    TestBed.configureTestingModule({
      providers: [
        BookFilterOrchestrationService,
        {provide: SortService, useValue: {applySort: applySortSpy}}
      ]
    });

    service = TestBed.inject(BookFilterOrchestrationService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.clearAllMocks();
  });

  function createHeaderFilter(): HeaderFilter {
    return {filter: headerFilterSpy} as unknown as HeaderFilter;
  }

  function createSideBarFilter(): SideBarFilter {
    return {filter: sideBarFilterSpy} as unknown as SideBarFilter;
  }

  function createSeriesCollapseFilter(): SeriesCollapseFilter {
    return {filter: seriesCollapseFilterSpy} as unknown as SeriesCollapseFilter;
  }

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('applyFilters', () => {
    it('should chain filters and apply sorting', async () => {
      const book1 = {id: 1} as Book;
      const book2 = {id: 2} as Book;
      const initialState: BookState = {books: [book1, book2], loaded: true, error: null};
      const headerState: BookState = {books: [book1], loaded: true, error: null};
      const sideBarState: BookState = {books: [book1, book2], loaded: true, error: null};
      const seriesState: BookState = {books: [book2], loaded: true, error: null};

      headerFilterSpy.mockReturnValue(of(headerState));
      sideBarFilterSpy.mockReturnValue(of(sideBarState));
      seriesCollapseFilterSpy.mockReturnValue(of(seriesState));

      const result = await firstValueFrom(service.applyFilters(
        initialState,
        createHeaderFilter(),
        createSideBarFilter(),
        createSeriesCollapseFilter(),
        false,
        sortOption
      ));

      expect(headerFilterSpy).toHaveBeenCalledWith(initialState);
      expect(sideBarFilterSpy).toHaveBeenCalledWith(headerState);
      expect(seriesCollapseFilterSpy).toHaveBeenCalledWith(sideBarState, false);
      expect(applySortSpy).toHaveBeenCalledWith(seriesState.books, sortOption);
      expect(result).toEqual(seriesState);
    });

    it('should pass forceExpandSeries to the series collapse filter', async () => {
      const initialState: BookState = {books: [{id: 1} as Book], loaded: true, error: null};
      headerFilterSpy.mockReturnValue(of(initialState));
      sideBarFilterSpy.mockReturnValue(of(initialState));
      seriesCollapseFilterSpy.mockReturnValue(of(initialState));

      await firstValueFrom(service.applyFilters(
        initialState,
        createHeaderFilter(),
        createSideBarFilter(),
        createSeriesCollapseFilter(),
        true,
        sortOption
      ));

      expect(seriesCollapseFilterSpy).toHaveBeenCalledWith(initialState, true);
    });

    it('should not sort when the final state is not loaded', async () => {
      const initialState: BookState = {books: null, loaded: false, error: null};
      headerFilterSpy.mockReturnValue(of(initialState));
      sideBarFilterSpy.mockReturnValue(of(initialState));
      seriesCollapseFilterSpy.mockReturnValue(of(initialState));

      const result = await firstValueFrom(service.applyFilters(
        initialState,
        createHeaderFilter(),
        createSideBarFilter(),
        createSeriesCollapseFilter(),
        false,
        sortOption
      ));

      expect(result).toBe(initialState);
      expect(applySortSpy).not.toHaveBeenCalled();
    });

    it('should not sort when the final state has an error', async () => {
      const initialState: BookState = {books: null, loaded: true, error: 'boom'};
      headerFilterSpy.mockReturnValue(of(initialState));
      sideBarFilterSpy.mockReturnValue(of(initialState));
      seriesCollapseFilterSpy.mockReturnValue(of(initialState));

      const result = await firstValueFrom(service.applyFilters(
        initialState,
        createHeaderFilter(),
        createSideBarFilter(),
        createSeriesCollapseFilter(),
        false,
        sortOption
      ));

      expect(result).toBe(initialState);
      expect(applySortSpy).not.toHaveBeenCalled();
    });
  });

  describe('shouldForceExpandSeries', () => {
    it('should return true when a series filter is present in the query params', () => {
      expect(service.shouldForceExpandSeries(convertToParamMap({filter: 'author:A,series:Dune'}))).toBe(true);
      expect(service.shouldForceExpandSeries(convertToParamMap({filter: 'series:Dune%20Messiah'}))).toBe(true);
    });

    it('should return false when no series filter is present', () => {
      expect(service.shouldForceExpandSeries(convertToParamMap({filter: 'author:A'}))).toBe(false);
      expect(service.shouldForceExpandSeries(convertToParamMap({}))).toBe(false);
    });
  });
});
