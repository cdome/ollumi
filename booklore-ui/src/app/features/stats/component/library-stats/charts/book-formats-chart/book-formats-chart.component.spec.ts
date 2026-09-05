import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BehaviorSubject} from 'rxjs';
import {firstValueFrom} from 'rxjs';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {BookFormatsChartComponent} from './book-formats-chart.component';
import {LibraryFilterService} from '../../service/library-filter.service';
import {BookService} from '../../../../../book/service/book.service';
import {createMockBook} from '../../../../../../../testing/factories';

describe('BookFormatsChartComponent', () => {
  let fixture: ComponentFixture<BookFormatsChartComponent>;
  let component: BookFormatsChartComponent;

  const mockBooks = [
    createMockBook({id: 1, primaryFile: {bookType: 'EPUB'} as any}),
    createMockBook({id: 2, primaryFile: {bookType: 'PDF'} as any}),
    createMockBook({id: 3, primaryFile: {bookType: 'EPUB'} as any})
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
      imports: [BookFormatsChartComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        {provide: BookService, useValue: bookServiceMock},
        {provide: LibraryFilterService, useValue: libraryFilterServiceMock}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    TestBed.overrideComponent(BookFormatsChartComponent, {
      set: {
        imports: [CommonModule, TranslocoTestingModule]
      }
    });

    fixture = TestBed.createComponent(BookFormatsChartComponent);
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

  it('should compute format stats and emit chart data', async () => {
    const data = await firstValueFrom(component.chartData$);

    expect(data.labels).toBeDefined();
    expect(data.datasets).toHaveLength(1);
    expect(component.totalBooks).toBe(3);
    expect(component.formatStats.length).toBeGreaterThan(0);
  });

  it('should produce an empty chart when the book state is invalid', async () => {
    bookServiceMock.getCurrentBookState.mockReturnValue({loaded: false, books: [], error: null});
    libraryFilterServiceMock.selectedLibrary$.next(null);

    const data = await firstValueFrom(component.chartData$);
    expect(data.labels).toEqual([]);
    expect(data.datasets).toEqual([]);
    expect(component.totalBooks).toBe(0);
  });
});
