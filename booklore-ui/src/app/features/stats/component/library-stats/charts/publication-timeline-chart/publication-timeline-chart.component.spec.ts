import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BehaviorSubject} from 'rxjs';
import {firstValueFrom} from 'rxjs';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {PublicationTimelineChartComponent} from './publication-timeline-chart.component';
import {LibraryFilterService} from '../../service/library-filter.service';
import {BookService} from '../../../../../book/service/book.service';
import {createMockBook} from '../../../../../../../testing/factories';

describe('PublicationTimelineChartComponent', () => {
  let fixture: ComponentFixture<PublicationTimelineChartComponent>;
  let component: PublicationTimelineChartComponent;

  const mockBooks = [
    createMockBook({id: 1, metadata: {publishedDate: '2015-06-15', title: 'Book 2015'} as any}),
    createMockBook({id: 2, metadata: {publishedDate: '2020-03-20', title: 'Book 2020'} as any}),
    createMockBook({id: 3, metadata: {publishedDate: '2021-08-10', title: 'Book 2021'} as any})
  ];

  let bookServiceMock: {
    bookState$: BehaviorSubject<{loaded: boolean; books: typeof mockBooks; error: null}>;
    getCurrentBookState: ReturnType<typeof vi.fn>;
  };

  let libraryFilterServiceMock: {
    selectedLibrary$: BehaviorSubject<number | null>;
    getCurrentSelectedLibrary: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    bookServiceMock = {
      bookState$: new BehaviorSubject({loaded: true, books: mockBooks, error: null}),
      getCurrentBookState: vi.fn(() => ({loaded: true, books: mockBooks, error: null}))
    };

    libraryFilterServiceMock = {
      selectedLibrary$: new BehaviorSubject<number | null>(null),
      getCurrentSelectedLibrary: vi.fn(() => null)
    };

    TestBed.configureTestingModule({
      imports: [PublicationTimelineChartComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        {provide: BookService, useValue: bookServiceMock},
        {provide: LibraryFilterService, useValue: libraryFilterServiceMock}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    TestBed.overrideComponent(PublicationTimelineChartComponent, {
      set: {
        imports: [CommonModule, TranslocoTestingModule]
      }
    });

    fixture = TestBed.createComponent(PublicationTimelineChartComponent);
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

  it('should compute timeline stats and emit chart data', async () => {
    const data = await firstValueFrom(component.chartData$);

    expect(data.labels).toBeDefined();
    expect(data.datasets).toHaveLength(1);
    expect(component.totalBooks).toBe(3);
    expect(component.insights).not.toBeNull();
  });

  it('should produce an empty chart when there are no dated books', async () => {
    bookServiceMock.getCurrentBookState.mockReturnValue({loaded: true, books: [createMockBook()], error: null});
    libraryFilterServiceMock.selectedLibrary$.next(null);

    const data = await firstValueFrom(component.chartData$);
    expect(data.labels).toEqual([]);
    expect(data.datasets).toEqual([]);
    expect(component.totalBooks).toBe(0);
    expect(component.insights).toBeNull();
  });
});
