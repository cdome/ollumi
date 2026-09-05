import {describe, expect, it, vi, beforeEach, afterEach} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {SeriesCollapseFilter} from './SeriesCollapseFilter';
import {UserService} from '../../../../settings/user-management/user.service';
import {MessageService} from 'primeng/api';
import {createMockBook, createMockUser} from '../../../../../../testing/factories';
import {BehaviorSubject} from 'rxjs';
import {first, take} from 'rxjs/operators';

function createMockUserService(user: any = createMockUser()) {
  const userStateSubject = new BehaviorSubject({user, loaded: true, error: null});
  return {
    userState$: userStateSubject.asObservable(),
    userStateSubject,
    getCurrentUser: vi.fn(() => user),
    setInitialUser: vi.fn(),
    updateUserSetting: vi.fn()
  };
}

function createMockMessageService() {
  return {
    add: vi.fn()
  };
}

describe('SeriesCollapseFilter', () => {
  let userServiceMock: ReturnType<typeof createMockUserService>;
  let messageServiceMock: ReturnType<typeof createMockMessageService>;

  beforeEach(() => {
    userServiceMock = createMockUserService();
    messageServiceMock = createMockMessageService();

    TestBed.configureTestingModule({
      providers: [
        SeriesCollapseFilter,
        {provide: UserService, useValue: userServiceMock},
        {provide: MessageService, useValue: messageServiceMock}
      ]
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should be created', () => {
    const filter = TestBed.inject(SeriesCollapseFilter);
    expect(filter).toBeTruthy();
  });

  describe('setCollapsed / isSeriesCollapsed', () => {
    it('should update collapsed state', () => {
      const filter = TestBed.inject(SeriesCollapseFilter);
      filter.setCollapsed(true);
      expect(filter.isSeriesCollapsed).toBe(true);
      filter.setCollapsed(false);
      expect(filter.isSeriesCollapsed).toBe(false);
    });

    it('should emit collapsed value on seriesCollapse$', async () => {
      const filter = TestBed.inject(SeriesCollapseFilter);
      filter.setCollapsed(true);
      const value = await filter.seriesCollapse$.pipe(first()).toPromise();
      expect(value).toBe(true);
    });
  });

  describe('setContext', () => {
    it('should set context and apply preference', () => {
      const filter = TestBed.inject(SeriesCollapseFilter);
      filter.setContext('LIBRARY', 1);
      expect(filter.isSeriesCollapsed).toBe(false);
    });

    it('should clear context when type or id is null', () => {
      const filter = TestBed.inject(SeriesCollapseFilter);
      filter.setContext('LIBRARY', 1);
      filter.setContext(null, 1);
      expect(filter.isSeriesCollapsed).toBe(false);
      filter.setContext('LIBRARY', null as any);
      expect(filter.isSeriesCollapsed).toBe(false);
    });
  });

  describe('applyPreference', () => {
    it('should apply global seriesCollapsed preference', () => {
      const user = createMockUser({
        userSettings: {
          ...createMockUser().userSettings,
          entityViewPreferences: {
            global: {seriesCollapsed: true},
            overrides: []
          }
        }
      });
      userServiceMock.getCurrentUser.mockReturnValue(user);
      const filter = TestBed.inject(SeriesCollapseFilter);
      expect(filter.isSeriesCollapsed).toBe(true);
    });

    it('should fall back to legacy seriesCollapse field', () => {
      const user = createMockUser({
        userSettings: {
          ...createMockUser().userSettings,
          entityViewPreferences: {
            global: {seriesCollapse: true} as any,
            overrides: []
          }
        }
      });
      userServiceMock.getCurrentUser.mockReturnValue(user);
      const filter = TestBed.inject(SeriesCollapseFilter);
      expect(filter.isSeriesCollapsed).toBe(true);
    });

    it('should apply override preference for current context', () => {
      const user = createMockUser({
        userSettings: {
          ...createMockUser().userSettings,
          entityViewPreferences: {
            global: {seriesCollapsed: false},
            overrides: [{
              entityType: 'LIBRARY',
              entityId: 1,
              preferences: {seriesCollapsed: true}
            }]
          }
        }
      });
      userServiceMock.getCurrentUser.mockReturnValue(user);
      const filter = TestBed.inject(SeriesCollapseFilter);
      filter.setContext('LIBRARY', 1);
      expect(filter.isSeriesCollapsed).toBe(true);
    });

    it('should fall back to legacy seriesCollapse in override', () => {
      const user = createMockUser({
        userSettings: {
          ...createMockUser().userSettings,
          entityViewPreferences: {
            global: {seriesCollapsed: false},
            overrides: [{
              entityType: 'SHELF',
              entityId: 2,
              preferences: {seriesCollapse: true} as any
            }]
          }
        }
      });
      userServiceMock.getCurrentUser.mockReturnValue(user);
      const filter = TestBed.inject(SeriesCollapseFilter);
      filter.setContext('SHELF', 2);
      expect(filter.isSeriesCollapsed).toBe(true);
    });
  });

  describe('filter', () => {
    it('should return original state when not collapsed', async () => {
      const filter = TestBed.inject(SeriesCollapseFilter);
      filter.setCollapsed(false);
      const state = {
        books: [createMockBook()],
        loaded: true,
        error: null
      };
      const result = await filter.filter(state).pipe(first()).toPromise();
      expect(result).toBe(state);
    });

    it('should return original state when forceExpandSeries is true', async () => {
      const filter = TestBed.inject(SeriesCollapseFilter);
      filter.setCollapsed(true);
      const state = {
        books: [createMockBook()],
        loaded: true,
        error: null
      };
      const result = await filter.filter(state, true).pipe(first()).toPromise();
      expect(result).toBe(state);
    });

    it('should return original state when books is null/undefined', async () => {
      const filter = TestBed.inject(SeriesCollapseFilter);
      filter.setCollapsed(true);
      const state = {books: null, loaded: true, error: null};
      const result = await filter.filter(state).pipe(first()).toPromise();
      expect(result).toBe(state);
    });

    it('should collapse books in the same series', async () => {
      const filter = TestBed.inject(SeriesCollapseFilter);
      filter.setCollapsed(true);
      const books = [
        createMockBook({id: 1, metadata: {bookId: 1, title: 'Book 2', seriesName: 'Dune', seriesNumber: 2}}),
        createMockBook({id: 2, metadata: {bookId: 2, title: 'Book 1', seriesName: 'Dune', seriesNumber: 1}}),
        createMockBook({id: 3, metadata: {bookId: 3, title: 'Standalone'}})
      ];
      const state = {books, loaded: true, error: null};
      const result = await filter.filter(state).pipe(first()).toPromise();
      expect(result.books?.length).toBe(2);
      const collapsed = result.books?.find((b: any) => b.metadata?.seriesName === 'Dune');
      expect(collapsed?.seriesCount).toBe(2);
      expect(collapsed?.seriesBooks?.length).toBe(2);
      expect(collapsed?.id).toBe(2);
    });

    it('should sort series books by seriesNumber with missing numbers last', async () => {
      const filter = TestBed.inject(SeriesCollapseFilter);
      filter.setCollapsed(true);
      const books = [
        createMockBook({id: 1, metadata: {bookId: 1, title: 'No Number', seriesName: 'Dune'}}),
        createMockBook({id: 2, metadata: {bookId: 2, title: 'Book 1', seriesName: 'Dune', seriesNumber: 1}})
      ];
      const state = {books, loaded: true, error: null};
      const result = await filter.filter(state).pipe(first()).toPromise();
      const collapsed = result.books?.find((b: any) => b.metadata?.seriesName === 'Dune');
      expect(collapsed?.id).toBe(2);
    });

    it('should preserve non-series books', async () => {
      const filter = TestBed.inject(SeriesCollapseFilter);
      filter.setCollapsed(true);
      const standalone = createMockBook({id: 1, metadata: {bookId: 1, title: 'Standalone'}});
      const state = {books: [standalone], loaded: true, error: null};
      const result = await filter.filter(state).pipe(first()).toPromise();
      expect(result.books?.length).toBe(1);
      expect(result.books?.[0].id).toBe(1);
    });
  });

  describe('persistCollapsePreference', () => {
    it('should update global preference when no context', async () => {
      const filter = TestBed.inject(SeriesCollapseFilter);
      filter.setCollapsed(true);
      // Wait for debounce
      await filter.seriesCollapse$.pipe(take(1)).toPromise();
      await new Promise(r => setTimeout(r, 600));
      expect(userServiceMock.updateUserSetting).toHaveBeenCalled();
      expect(messageServiceMock.add).toHaveBeenCalled();
    });

    it('should create override for context', async () => {
      const filter = TestBed.inject(SeriesCollapseFilter);
      filter.setContext('LIBRARY', 1);
      filter.setCollapsed(true);
      await new Promise(r => setTimeout(r, 600));
      const call = userServiceMock.updateUserSetting.mock.calls[0];
      const prefs = call[2] as any;
      expect(prefs.overrides.length).toBe(1);
      expect(prefs.overrides[0].entityType).toBe('LIBRARY');
      expect(prefs.overrides[0].entityId).toBe(1);
      expect(prefs.overrides[0].preferences.seriesCollapsed).toBe(true);
    });

    it('should not persist if no current user', async () => {
      userServiceMock.getCurrentUser.mockReturnValue(null);
      const filter = TestBed.inject(SeriesCollapseFilter);
      filter.setCollapsed(true);
      await new Promise(r => setTimeout(r, 600));
      expect(userServiceMock.updateUserSetting).not.toHaveBeenCalled();
    });
  });

  describe('ngOnDestroy', () => {
    it('should complete destroy subject', () => {
      const filter = TestBed.inject(SeriesCollapseFilter);
      filter.ngOnDestroy();
      let completed = false;
      (filter as any).destroy$.subscribe({
        complete: () => completed = true
      });
      expect(completed).toBe(true);
    });
  });
});
