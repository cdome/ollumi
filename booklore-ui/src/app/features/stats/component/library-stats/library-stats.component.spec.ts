import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {CommonModule} from '@angular/common';
import {DragDropModule} from '@angular/cdk/drag-drop';
import {BehaviorSubject, of} from 'rxjs';
import {firstValueFrom} from 'rxjs';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {LibraryStatsComponent} from './library-stats.component';
import {LibrariesSummaryService} from './service/libraries-summary.service';
import {LibraryFilterService, LibraryOption} from './service/library-filter.service';

describe('LibraryStatsComponent', () => {
  let fixture: ComponentFixture<LibraryStatsComponent>;
  let component: LibraryStatsComponent;

  let libraryFilterServiceMock: {
    selectedLibrary$: BehaviorSubject<number | null>;
    getCurrentSelectedLibrary: ReturnType<typeof vi.fn>;
    setSelectedLibrary: ReturnType<typeof vi.fn>;
    getLibraryOptions: ReturnType<typeof vi.fn>;
  };

  let librariesSummaryServiceMock: {
    getBooksSummary: ReturnType<typeof vi.fn>;
    getFormattedSize: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    libraryFilterServiceMock = {
      selectedLibrary$: new BehaviorSubject<number | null>(null),
      getCurrentSelectedLibrary: vi.fn(() => null),
      setSelectedLibrary: vi.fn(),
      getLibraryOptions: vi.fn(() => of([{id: 1, name: 'Test Library'} as LibraryOption]))
    };

    librariesSummaryServiceMock = {
      getBooksSummary: vi.fn(() => of({
        totalBooks: 12,
        totalAuthors: 5,
        totalSeries: 3,
        totalPublishers: 2,
        totalSizeKb: 2048
      })),
      getFormattedSize: vi.fn(() => of('2 MB'))
    };

    TestBed.configureTestingModule({
      imports: [LibraryStatsComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        {provide: LibraryFilterService, useValue: libraryFilterServiceMock},
        {provide: LibrariesSummaryService, useValue: librariesSummaryServiceMock}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    TestBed.overrideComponent(LibraryStatsComponent, {
      set: {
        imports: [CommonModule, DragDropModule, TranslocoTestingModule]
      }
    });

    fixture = TestBed.createComponent(LibraryStatsComponent);
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

  it('should load library options and select the first library on init', () => {
    expect(libraryFilterServiceMock.getLibraryOptions).toHaveBeenCalled();
    expect(component.libraryOptions.length).toBe(1);
    expect(component.selectedLibrary).toEqual({id: 1, name: 'Test Library'});
    expect(component.hasData).toBe(true);
    expect(component.isLoading).toBe(false);
  });

  it('should set the selected library when onLibraryChange is called', () => {
    component.selectedLibrary = {id: 2, name: 'Another Library'};
    component.onLibraryChange();
    expect(libraryFilterServiceMock.setSelectedLibrary).toHaveBeenCalledWith(2);
  });

  it('should toggle the config panel', () => {
    expect(component.showConfigPanel).toBe(false);
    component.toggleConfigPanel();
    expect(component.showConfigPanel).toBe(true);
    component.closeConfigPanel();
    expect(component.showConfigPanel).toBe(false);
  });

  it('should toggle a chart configuration', () => {
    expect(component.isChartEnabled('bookFormats')).toBe(true);
    component.toggleChart('bookFormats');
    expect(component.isChartEnabled('bookFormats')).toBe(false);
    component.toggleChart('bookFormats');
    expect(component.isChartEnabled('bookFormats')).toBe(true);
  });

  it('should enable and disable all charts', () => {
    component.disableAllCharts();
    expect(component.getEnabledChartsSorted().length).toBe(0);
    component.enableAllCharts();
    expect(component.getEnabledChartsSorted().length).toBe(component.chartsConfig.length);
  });

  it('should expose summary observables', async () => {
    const totalBooks = await firstValueFrom(component.totalBooks$);
    expect(totalBooks).toBe(12);

    const totalAuthors = await firstValueFrom(component.totalAuthors$);
    expect(totalAuthors).toBe(5);
  });
});
