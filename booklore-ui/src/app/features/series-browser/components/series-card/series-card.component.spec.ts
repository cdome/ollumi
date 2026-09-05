import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslocoPipe} from '@jsverse/transloco';
import {SeriesCardComponent} from './series-card.component';
import {BookService} from '../../../book/service/book.service';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {createMockBook} from '../../../../../testing/factories';
import {mockTranslocoServiceProvider} from '../../../../../testing/providers';
import {SeriesSummary} from '../../model/series.model';

describe('SeriesCardComponent', () => {
  let fixture: ComponentFixture<SeriesCardComponent>;
  let component: SeriesCardComponent;

  const bookServiceMock = {
    readBook: vi.fn()
  };

  const urlHelperServiceMock = {
    getThumbnailUrl: vi.fn(() => 'thumbnail-url'),
    getAudiobookThumbnailUrl: vi.fn(() => 'audiobook-thumbnail-url')
  };

  const mockSeries: SeriesSummary = {
    seriesName: 'The Test Series',
    books: [],
    authors: ['Test Author'],
    categories: ['Fiction'],
    bookCount: 1,
    readCount: 0,
    progress: 0,
    seriesStatus: 'UNREAD' as any,
    nextUnread: null,
    lastReadTime: null,
    coverBooks: [createMockBook()],
    addedOn: null
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SeriesCardComponent],
      providers: [
        {provide: BookService, useValue: bookServiceMock},
        {provide: UrlHelperService, useValue: urlHelperServiceMock},
        mockTranslocoServiceProvider
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    TestBed.overrideComponent(SeriesCardComponent, {
      set: {
        imports: [CommonModule, TranslocoPipe],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(SeriesCardComponent);
    component = fixture.componentInstance;
    component.series = mockSeries;
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

  it('should render the series name', () => {
    expect(fixture.nativeElement.textContent).toContain('The Test Series');
  });
});
