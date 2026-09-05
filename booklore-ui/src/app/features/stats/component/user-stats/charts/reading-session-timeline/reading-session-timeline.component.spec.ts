import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BehaviorSubject} from 'rxjs';
import {of} from 'rxjs';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {ReadingSessionTimelineComponent} from './reading-session-timeline.component';
import {BookService} from '../../../../../book/service/book.service';
import {LibraryFilterService} from '../../../library-stats/service/library-filter.service';
import {UserStatsService} from '../../../../../settings/user-management/user-stats.service';
import {UrlHelperService} from '../../../../../../shared/service/url-helper.service';
import {createMockBook} from '../../../../../../../testing/factories';

describe('ReadingSessionTimelineComponent', () => {
  let fixture: ComponentFixture<ReadingSessionTimelineComponent>;
  let component: ReadingSessionTimelineComponent;

  let bookServiceMock: {
    bookState$: BehaviorSubject<{loaded: boolean; books: any[]; error: null}>;
    getCurrentBookState: ReturnType<typeof vi.fn>;
  };

  let libraryFilterServiceMock: {
    selectedLibrary$: BehaviorSubject<number | null>;
    getCurrentSelectedLibrary: ReturnType<typeof vi.fn>;
  };

  const userStatsServiceMock = {
    getHeatmapForYear: vi.fn(() => of([])),
    getTimelineForWeek: vi.fn(() => of([])),
    getGenreStats: vi.fn(() => of([])),
    getCompletionTimelineForYear: vi.fn(() => of([])),
    getFavoriteDays: vi.fn(() => of([])),
    getPeakHours: vi.fn(() => of([])),
    getPageTurnerScores: vi.fn(() => of([])),
    getCompletionRace: vi.fn(() => of([])),
    getReadingDates: vi.fn(() => of([])),
    getSessionScatter: vi.fn(() => of([]))
  };

  const urlHelperServiceMock = {
    getDirectThumbnailUrl: vi.fn(() => '')
  };

  beforeEach(() => {
    const mockBooks = [createMockBook()];

    bookServiceMock = {
      bookState$: new BehaviorSubject({loaded: true, books: mockBooks, error: null}),
      getCurrentBookState: vi.fn(() => ({loaded: true, books: mockBooks, error: null}))
    };

    libraryFilterServiceMock = {
      selectedLibrary$: new BehaviorSubject<number | null>(null),
      getCurrentSelectedLibrary: vi.fn(() => null)
    };

    TestBed.configureTestingModule({
      imports: [ReadingSessionTimelineComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        {provide: BookService, useValue: bookServiceMock},
        {provide: LibraryFilterService, useValue: libraryFilterServiceMock},
        {provide: UserStatsService, useValue: userStatsServiceMock},
        {provide: UrlHelperService, useValue: urlHelperServiceMock}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    TestBed.overrideComponent(ReadingSessionTimelineComponent, {
      set: {
        imports: [CommonModule, TranslocoTestingModule]
      }
    });

    fixture = TestBed.createComponent(ReadingSessionTimelineComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have timeline data with seven days', () => {
    expect(component.timelineData).toBeDefined();
    expect(component.timelineData.length).toBe(7);
  });
});
