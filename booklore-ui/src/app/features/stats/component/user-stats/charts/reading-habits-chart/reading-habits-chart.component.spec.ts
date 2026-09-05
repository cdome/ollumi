import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BehaviorSubject} from 'rxjs';
import {firstValueFrom} from 'rxjs';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {ReadingHabitsChartComponent} from './reading-habits-chart.component';
import {BookService} from '../../../../../book/service/book.service';
import {createMockBook} from '../../../../../../../testing/factories';
import {ReadStatus} from '../../../../../book/model/book.model';

describe('ReadingHabitsChartComponent', () => {
  let fixture: ComponentFixture<ReadingHabitsChartComponent>;
  let component: ReadingHabitsChartComponent;

  const mockBooks = [
    createMockBook({
      id: 1,
      readStatus: ReadStatus.READ,
      dateFinished: '2024-06-15T10:00:00Z',
      metadata: {pageCount: 300, authors: ['Author A'], categories: ['Fiction'], publishedDate: '2020-01-01', title: 'Book 1'} as any
    }),
    createMockBook({
      id: 2,
      readStatus: ReadStatus.READ,
      dateFinished: '2024-07-20T10:00:00Z',
      metadata: {pageCount: 250, authors: ['Author B'], categories: ['Sci-Fi'], publishedDate: '2019-01-01', title: 'Book 2'} as any
    })
  ];

  let bookServiceMock: {
    bookState$: BehaviorSubject<{loaded: boolean; books: typeof mockBooks; error: null}>;
    getCurrentBookState: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    bookServiceMock = {
      bookState$: new BehaviorSubject({loaded: true, books: mockBooks, error: null}),
      getCurrentBookState: vi.fn(() => ({loaded: true, books: mockBooks, error: null}))
    };

    TestBed.configureTestingModule({
      imports: [ReadingHabitsChartComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        {provide: BookService, useValue: bookServiceMock}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    TestBed.overrideComponent(ReadingHabitsChartComponent, {
      set: {
        imports: [CommonModule, TranslocoTestingModule]
      }
    });

    fixture = TestBed.createComponent(ReadingHabitsChartComponent);
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

  it('should compute habit profile and emit chart data', async () => {
    const data = await firstValueFrom(component.chartData$);

    expect(data.labels).toBeDefined();
    expect(data.labels?.length).toBe(8);
    expect(data.datasets).toHaveLength(1);
    expect(component.habitInsights.length).toBe(8);
  });

  it('should produce an empty chart when the book state is invalid', async () => {
    bookServiceMock.getCurrentBookState.mockReturnValue({loaded: false, books: [], error: null});

    fixture = TestBed.createComponent(ReadingHabitsChartComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    const data = await firstValueFrom(component.chartData$);
    expect(data.labels).toEqual([]);
    expect(data.datasets).toEqual([]);
    expect(component.habitInsights).toEqual([]);
  });
});
