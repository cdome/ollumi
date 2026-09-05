import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {of} from 'rxjs';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {BookReadingSessionsComponent} from './book-reading-sessions.component';
import {ReadingSessionApiService} from '../../../../../shared/service/reading-session-api.service';
import {commonComponentTestProviders} from '../../../../../../testing/providers';

const mockSessions = [
  {
    id: 1,
    bookId: 42,
    bookTitle: 'Test Book',
    startTime: '2024-01-01T10:00:00Z',
    endTime: '2024-01-01T10:30:00Z',
    durationSeconds: 1800,
    bookType: 'EPUB',
    pagesRead: 10,
    startLocation: '1',
    endLocation: '10'
  }
];

describe('BookReadingSessionsComponent', () => {
  let fixture: ComponentFixture<BookReadingSessionsComponent>;
  let component: BookReadingSessionsComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BookReadingSessionsComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        ...commonComponentTestProviders,
        {provide: ReadingSessionApiService, useValue: {
          getSessionsByBookId: vi.fn(() => of({content: mockSessions, page: {totalElements: 1}} as any))
        }}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    fixture = TestBed.createComponent(BookReadingSessionsComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('bookId', 42);
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

  it('should load reading sessions', () => {
    expect(component.sessions).toEqual(mockSessions);
  });
});
