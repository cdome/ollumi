import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BehaviorSubject} from 'rxjs';
import {firstValueFrom} from 'rxjs';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {ReadStatusChartComponent} from './read-status-chart.component';
import {BookService} from '../../../../../book/service/book.service';
import {createMockBook} from '../../../../../../../testing/factories';
import {ReadStatus} from '../../../../../book/model/book.model';

describe('ReadStatusChartComponent', () => {
  let fixture: ComponentFixture<ReadStatusChartComponent>;
  let component: ReadStatusChartComponent;

  const mockBooks = [
    createMockBook({id: 1, readStatus: ReadStatus.READ}),
    createMockBook({id: 2, readStatus: ReadStatus.READING}),
    createMockBook({id: 3, readStatus: ReadStatus.READ}),
    createMockBook({id: 4, readStatus: ReadStatus.UNREAD})
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
      imports: [ReadStatusChartComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        {provide: BookService, useValue: bookServiceMock}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    TestBed.overrideComponent(ReadStatusChartComponent, {
      set: {
        imports: [CommonModule, TranslocoTestingModule]
      }
    });

    fixture = TestBed.createComponent(ReadStatusChartComponent);
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

  it('should compute status stats and emit chart data', async () => {
    const data = await firstValueFrom(component.chartData$);

    expect(data.labels).toBeDefined();
    expect(data.datasets).toHaveLength(1);
    expect((data.datasets[0].data as number[]).reduce((a, b) => a + b, 0)).toBe(4);
  });

  it('should produce an empty chart when the book state is invalid', async () => {
    bookServiceMock.getCurrentBookState.mockReturnValue({loaded: false, books: [], error: null});

    fixture = TestBed.createComponent(ReadStatusChartComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    const data = await firstValueFrom(component.chartData$);
    expect(data.labels).toEqual([]);
    expect(data.datasets?.[0]?.data).toEqual([]);
  });
});
