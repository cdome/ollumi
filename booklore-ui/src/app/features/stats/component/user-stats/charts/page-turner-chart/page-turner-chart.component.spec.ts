import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BehaviorSubject} from 'rxjs';
import {firstValueFrom, of} from 'rxjs';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {PageTurnerChartComponent} from './page-turner-chart.component';
import {BookService} from '../../../../../book/service/book.service';
import {LibraryFilterService} from '../../../library-stats/service/library-filter.service';
import {UserStatsService} from '../../../../../settings/user-management/user-stats.service';
import {createMockBook} from '../../../../../../../testing/factories';

describe('PageTurnerChartComponent', () => {
  let fixture: ComponentFixture<PageTurnerChartComponent>;
  let component: PageTurnerChartComponent;

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
      imports: [PageTurnerChartComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        {provide: BookService, useValue: bookServiceMock},
        {provide: LibraryFilterService, useValue: libraryFilterServiceMock},
        {provide: UserStatsService, useValue: userStatsServiceMock}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    TestBed.overrideComponent(PageTurnerChartComponent, {
      set: {
        imports: [CommonModule, TranslocoTestingModule]
      }
    });

    fixture = TestBed.createComponent(PageTurnerChartComponent);
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

  it('should emit chart data', async () => {
    const data = await firstValueFrom(component.chartData$);
    expect(data).toBeDefined();
    expect(data.datasets).toBeDefined();
  });
});
