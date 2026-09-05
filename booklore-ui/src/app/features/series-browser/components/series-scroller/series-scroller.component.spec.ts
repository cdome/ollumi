import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoDirective} from '@jsverse/transloco';
import {SeriesScrollerComponent} from './series-scroller.component';
import {createMockBook} from '../../../../../testing/factories';
import {mockTranslocoServiceProvider} from '../../../../../testing/providers';
import {SeriesSummary} from '../../model/series.model';

describe('SeriesScrollerComponent', () => {
  let fixture: ComponentFixture<SeriesScrollerComponent>;
  let component: SeriesScrollerComponent;

  const mockSeries: SeriesSummary[] = [
    {
      seriesName: 'Series One',
      books: [],
      authors: ['Author One'],
      categories: [],
      bookCount: 1,
      readCount: 0,
      progress: 0,
      seriesStatus: 'UNREAD' as any,
      nextUnread: null,
      lastReadTime: null,
      coverBooks: [createMockBook()],
      addedOn: null
    }
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SeriesScrollerComponent],
      providers: [mockTranslocoServiceProvider],
      schemas: [NO_ERRORS_SCHEMA]
    });

    TestBed.overrideComponent(SeriesScrollerComponent, {
      set: {
        imports: [TranslocoDirective],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(SeriesScrollerComponent);
    component = fixture.componentInstance;
    component.title = 'Featured Series';
    component.series = mockSeries;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.resetTestingModule();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render the section title', () => {
    expect(fixture.nativeElement.textContent).toContain('Featured Series');
  });
});
