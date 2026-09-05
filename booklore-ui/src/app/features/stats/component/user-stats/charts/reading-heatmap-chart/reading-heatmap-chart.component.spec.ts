import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BehaviorSubject} from 'rxjs';
import {firstValueFrom} from 'rxjs';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {ReadingHeatmapChartComponent} from './reading-heatmap-chart.component';
import {BookService} from '../../../../../book/service/book.service';
import {createMockBook} from '../../../../../../../testing/factories';

describe('ReadingHeatmapChartComponent', () => {
  let fixture: ComponentFixture<ReadingHeatmapChartComponent>;
  let component: ReadingHeatmapChartComponent;

  const currentYear = new Date().getFullYear();
  const mockBooks = [
    createMockBook({id: 1, dateFinished: `${currentYear}-06-15T10:00:00Z`}),
    createMockBook({id: 2, dateFinished: `${currentYear}-03-20T10:00:00Z`}),
    createMockBook({id: 3, dateFinished: `${currentYear}-06-20T10:00:00Z`})
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
      imports: [ReadingHeatmapChartComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        {provide: BookService, useValue: bookServiceMock}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    TestBed.overrideComponent(ReadingHeatmapChartComponent, {
      set: {
        imports: [CommonModule, TranslocoTestingModule]
      }
    });

    fixture = TestBed.createComponent(ReadingHeatmapChartComponent);
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

  it('should compute heatmap data and emit chart data', async () => {
    const data = await firstValueFrom(component.chartData$);

    expect(data.datasets).toHaveLength(1);
    expect(data.datasets[0].data.length).toBeGreaterThan(0);
  });

  it('should render a zeroed grid when there is no finished book data', async () => {
    bookServiceMock.getCurrentBookState.mockReturnValue({loaded: false, books: [], error: null});

    fixture = TestBed.createComponent(ReadingHeatmapChartComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    const data = await firstValueFrom(component.chartData$);
    expect(data.datasets?.[0]?.data.length).toBe(120);
    expect(data.datasets?.[0]?.data.every((point: any) => point.v === 0)).toBe(true);
  });
});
