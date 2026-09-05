import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, convertToParamMap, ParamMap, Router} from '@angular/router';
import {of} from 'rxjs';
import {
  BookBrowserQueryParamsService,
  QUERY_PARAMS,
  SORT_DIRECTION,
  VIEW_MODES
} from './book-browser-query-params.service';
import {SortDirection, SortOption} from '../../model/sort.model';
import {BookFilterMode, EntityViewPreferences} from '../../../settings/user-management/user.service';
import {EntityType} from './book-browser.component';

describe('BookBrowserQueryParamsService', () => {
  let service: BookBrowserQueryParamsService;
  let navigateSpy: ReturnType<typeof vi.fn>;
  let snapshot: {queryParams: Record<string, unknown>; queryParamMap: ParamMap};
  let activatedRouteMock: any;

  const sortOptions: SortOption[] = [
    {label: 'Title', field: 'title', direction: SortDirection.ASCENDING},
    {label: 'Added On', field: 'addedOn', direction: SortDirection.DESCENDING},
    {label: 'Author', field: 'author', direction: SortDirection.ASCENDING}
  ];

  beforeEach(() => {
    navigateSpy = vi.fn(() => Promise.resolve(true));
    snapshot = {queryParams: {}, queryParamMap: convertToParamMap({})};
    activatedRouteMock = {
      snapshot,
      queryParams: of({}),
      queryParamMap: of(convertToParamMap({}))
    };

    TestBed.configureTestingModule({
      providers: [
        BookBrowserQueryParamsService,
        {provide: Router, useValue: {navigate: navigateSpy}},
        {provide: ActivatedRoute, useValue: activatedRouteMock}
      ]
    });

    service = TestBed.inject(BookBrowserQueryParamsService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.clearAllMocks();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('parseQueryParams', () => {
    it('should fall back to addedOn ascending when there are no params or matching prefs', () => {
      const result = service.parseQueryParams(
        convertToParamMap({}),
        undefined,
        EntityType.ALL_BOOKS,
        NaN,
        sortOptions,
        'and'
      );

      expect(result.sortOption.field).toBe('addedOn');
      expect(result.sortOption.direction).toBe(SortDirection.ASCENDING);
      expect(result.viewMode).toBe(VIEW_MODES.GRID);
      expect(result.filterMode).toBe('and');
      expect(result.filters).toEqual({});
      expect(result.viewModeFromToggle).toBe(false);
    });

    it('should parse legacy single-sort query params', () => {
      const result = service.parseQueryParams(
        convertToParamMap({sort: 'title', direction: SORT_DIRECTION.DESCENDING}),
        undefined,
        EntityType.ALL_BOOKS,
        NaN,
        sortOptions,
        'and'
      );

      expect(result.sortOption).toEqual({label: 'Title', field: 'title', direction: SortDirection.DESCENDING});
      expect(result.sortCriteria).toHaveLength(1);
    });

    it('should default legacy sort direction to ascending', () => {
      const result = service.parseQueryParams(
        convertToParamMap({sort: 'author'}),
        undefined,
        EntityType.ALL_BOOKS,
        NaN,
        sortOptions,
        'and'
      );

      expect(result.sortOption.direction).toBe(SortDirection.ASCENDING);
    });

    it('should fall back to the default sort when the requested field is unknown', () => {
      const result = service.parseQueryParams(
        convertToParamMap({sort: 'unknown'}),
        undefined,
        EntityType.ALL_BOOKS,
        NaN,
        sortOptions,
        'and'
      );

      expect(result.sortOption.field).toBe('addedOn');
      expect(result.sortOption.direction).toBe(SortDirection.DESCENDING);
    });

    it('should parse multi-sort query params', () => {
      const result = service.parseQueryParams(
        convertToParamMap({sort: 'author:asc,title:desc'}),
        undefined,
        EntityType.ALL_BOOKS,
        NaN,
        sortOptions,
        'and'
      );

      expect(result.sortCriteria).toEqual([
        {label: 'Author', field: 'author', direction: SortDirection.ASCENDING},
        {label: 'Title', field: 'title', direction: SortDirection.DESCENDING}
      ]);
    });

    it('should apply user preference sort criteria', () => {
      const prefs: EntityViewPreferences = {
        global: {
          sortKey: 'addedOn',
          sortDir: 'ASC',
          view: 'GRID',
          coverSize: 1,
          seriesCollapsed: false,
          overlayBookType: true,
          sortCriteria: [{field: 'title', direction: 'DESC'}]
        },
        overrides: []
      };

      const result = service.parseQueryParams(
        convertToParamMap({}),
        prefs,
        EntityType.ALL_BOOKS,
        NaN,
        sortOptions,
        'and'
      );

      expect(result.sortOption).toEqual({label: 'Title', field: 'title', direction: SortDirection.DESCENDING});
    });

    it('should apply per-entity preference overrides', () => {
      const prefs: EntityViewPreferences = {
        global: {
          sortKey: 'addedOn',
          sortDir: 'ASC',
          view: 'GRID',
          coverSize: 1,
          seriesCollapsed: false,
          overlayBookType: true
        },
        overrides: [{
          entityType: 'LIBRARY',
          entityId: 5,
          preferences: {
            sortKey: 'title',
            sortDir: 'DESC',
            view: 'TABLE',
            coverSize: 1,
            seriesCollapsed: false,
            overlayBookType: true
          }
        }]
      };

      const result = service.parseQueryParams(
        convertToParamMap({}),
        prefs,
        EntityType.LIBRARY,
        5,
        sortOptions,
        'and'
      );

      expect(result.sortOption.field).toBe('title');
      expect(result.sortOption.direction).toBe(SortDirection.DESCENDING);
      expect(result.viewMode).toBe(VIEW_MODES.TABLE);
    });

    it('should deserialize filter params', () => {
      const result = service.parseQueryParams(
        convertToParamMap({filter: 'author:A|B,series:Dune'}),
        undefined,
        EntityType.ALL_BOOKS,
        NaN,
        sortOptions,
        'and'
      );

      expect(result.filters).toEqual({author: ['A', 'B'], series: ['Dune']});
    });

    it('should use the query-param filter mode or default', () => {
      const withParam = service.parseQueryParams(
        convertToParamMap({fmode: 'or'}),
        undefined,
        EntityType.ALL_BOOKS,
        NaN,
        sortOptions,
        'and'
      );
      expect(withParam.filterMode).toBe('or');

      const fallback = service.parseQueryParams(
        convertToParamMap({}),
        undefined,
        EntityType.ALL_BOOKS,
        NaN,
        sortOptions,
        'not'
      );
      expect(fallback.filterMode).toBe('not');
    });

    it('should honor view mode from a toggle action', () => {
      const table = service.parseQueryParams(
        convertToParamMap({view: VIEW_MODES.TABLE, from: 'toggle'}),
        undefined,
        EntityType.ALL_BOOKS,
        NaN,
        sortOptions,
        'and'
      );
      expect(table.viewMode).toBe(VIEW_MODES.TABLE);
      expect(table.viewModeFromToggle).toBe(true);

      const invalid = service.parseQueryParams(
        convertToParamMap({view: 'invalid', from: 'toggle'}),
        undefined,
        EntityType.ALL_BOOKS,
        NaN,
        sortOptions,
        'and'
      );
      expect(invalid.viewMode).toBe(VIEW_MODES.GRID);
    });

    it('should derive view mode from preferences when not toggled', () => {
      const prefs: EntityViewPreferences = {
        global: {
          sortKey: 'addedOn',
          sortDir: 'ASC',
          view: 'TABLE',
          coverSize: 1,
          seriesCollapsed: false,
          overlayBookType: true
        },
        overrides: []
      };

      const result = service.parseQueryParams(
        convertToParamMap({}),
        prefs,
        EntityType.ALL_BOOKS,
        NaN,
        sortOptions,
        'and'
      );

      expect(result.viewMode).toBe(VIEW_MODES.TABLE);
      expect(result.viewModeFromToggle).toBe(false);
    });
  });

  describe('updateViewMode', () => {
    it('should navigate with the view param and toggle origin', () => {
      service.updateViewMode('table');

      expect(navigateSpy).toHaveBeenCalledWith([], {
        queryParams: {[QUERY_PARAMS.VIEW]: VIEW_MODES.TABLE, [QUERY_PARAMS.FROM]: 'toggle'},
        queryParamsHandling: 'merge',
        replaceUrl: true
      });
    });
  });

  describe('updateSort / updateMultiSort', () => {
    it('should serialize a single sort option and clear legacy direction', () => {
      service.updateSort({label: 'Title', field: 'title', direction: SortDirection.ASCENDING});

      expect(navigateSpy).toHaveBeenCalledWith([], {
        queryParams: {sort: 'title:asc', direction: null},
        replaceUrl: true
      });
    });

    it('should merge multi-sort params with current query params', () => {
      snapshot.queryParams = {sort: 'old', direction: 'desc'};
      service.updateMultiSort([
        {label: 'Author', field: 'author', direction: SortDirection.ASCENDING},
        {label: 'Title', field: 'title', direction: SortDirection.DESCENDING}
      ]);

      expect(navigateSpy).toHaveBeenCalledWith([], {
        queryParams: {sort: 'author:asc,title:desc', direction: null},
        replaceUrl: true
      });
    });
  });

  describe('serializeSort / deserializeSort', () => {
    it('should round-trip multi-sort criteria', () => {
      const criteria: SortOption[] = [
        {label: 'Author', field: 'author', direction: SortDirection.ASCENDING},
        {label: 'Title', field: 'title', direction: SortDirection.DESCENDING}
      ];

      const serialized = service.serializeSort(criteria);
      const deserialized = service.deserializeSort(serialized, sortOptions);

      expect(serialized).toBe('author:asc,title:desc');
      expect(deserialized).toEqual(criteria);
    });
  });

  describe('serializeFilters / deserializeFilters', () => {
    it('should round-trip filters with encoding', () => {
      const filters = {author: ['A', 'B'], series: ['Dune: Messiah']};

      const serialized = service.serializeFilters(filters);
      const deserialized = service.deserializeFilters(serialized);

      expect(serialized).toContain('author:A|B');
      expect(serialized).toContain(`series:${encodeURIComponent('Dune: Messiah')}`);
      expect(deserialized).toEqual(filters);
    });

    it('should return an empty record for a null filter param', () => {
      expect(service.deserializeFilters(null)).toEqual({});
    });
  });

  describe('updateFilters', () => {
    it('should navigate when filters change', () => {
      service.updateFilters({author: ['A']});

      expect(navigateSpy).toHaveBeenCalledWith([], {
        relativeTo: activatedRouteMock,
        queryParams: {[QUERY_PARAMS.FILTER]: 'author:A'},
        queryParamsHandling: 'merge',
        replaceUrl: false
      });
    });

    it('should navigate with null when filters are cleared', () => {
      snapshot.queryParamMap = convertToParamMap({filter: 'author:A'});
      service.updateFilters(null);

      expect(navigateSpy).toHaveBeenCalledWith([], {
        relativeTo: activatedRouteMock,
        queryParams: {[QUERY_PARAMS.FILTER]: null},
        queryParamsHandling: 'merge',
        replaceUrl: false
      });
    });

    it('should not navigate when the filter param is unchanged', () => {
      snapshot.queryParamMap = convertToParamMap({filter: 'author:A'});
      service.updateFilters({author: ['A']});

      expect(navigateSpy).not.toHaveBeenCalled();
    });
  });

  describe('updateFilterMode', () => {
    it('should set the filter mode query param', () => {
      service.updateFilterMode('or', {});

      expect(navigateSpy).toHaveBeenCalledWith([], {
        relativeTo: activatedRouteMock,
        queryParams: {[QUERY_PARAMS.FMODE]: 'or'},
        queryParamsHandling: 'merge',
        replaceUrl: false
      });
    });

    it('should clear filters when switching to single mode with multiple selections', () => {
      service.updateFilterMode('single', {author: ['A', 'B']});

      expect(navigateSpy).toHaveBeenCalledWith([], {
        relativeTo: activatedRouteMock,
        queryParams: {[QUERY_PARAMS.FMODE]: 'single', [QUERY_PARAMS.FILTER]: null},
        queryParamsHandling: 'merge',
        replaceUrl: false
      });
    });

    it('should not clear filters when switching to single mode with one value', () => {
      service.updateFilterMode('single', {author: ['A']});

      expect(navigateSpy).toHaveBeenCalledWith([], {
        relativeTo: activatedRouteMock,
        queryParams: {[QUERY_PARAMS.FMODE]: 'single'},
        queryParamsHandling: 'merge',
        replaceUrl: false
      });
    });
  });

  describe('syncQueryParams', () => {
    it('should navigate when query params change', () => {
      service.syncQueryParams('table', 'or', {author: ['A']});

      expect(navigateSpy).toHaveBeenCalledWith([], {
        queryParams: {view: 'table', fmode: 'or', filter: 'author:A'},
        replaceUrl: true
      });
    });

    it('should merge with existing query params', () => {
      snapshot.queryParams = {page: '2'};
      service.syncQueryParams('grid', 'and', {});

      expect(navigateSpy).toHaveBeenCalledWith([], {
        queryParams: {page: '2', view: 'grid', fmode: 'and'},
        replaceUrl: true
      });
    });

    it('should not navigate when nothing changed', () => {
      snapshot.queryParams = {view: 'grid', fmode: 'and'};
      service.syncQueryParams('grid', 'and', {});

      expect(navigateSpy).not.toHaveBeenCalled();
    });
  });

  describe('shouldForceExpandSeries', () => {
    it('should return true when a series filter is present', () => {
      expect(service.shouldForceExpandSeries(convertToParamMap({filter: 'author:A,series:Dune'}))).toBe(true);
      expect(service.shouldForceExpandSeries(convertToParamMap({filter: 'series:Dune'}))).toBe(true);
    });

    it('should return false when no series filter is present', () => {
      expect(service.shouldForceExpandSeries(convertToParamMap({filter: 'author:A'}))).toBe(false);
      expect(service.shouldForceExpandSeries(convertToParamMap({}))).toBe(false);
    });
  });
});
