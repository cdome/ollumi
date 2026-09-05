import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom, of, throwError} from 'rxjs';
import {BookNoteService, BookNote, CreateBookNoteRequest} from './book-note.service';
import {mockHttpClientProvider} from '../../../testing/providers';

describe('BookNoteService', () => {
  let service: BookNoteService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        BookNoteService,
        mockHttpClientProvider
      ]
    });

    service = TestBed.inject(BookNoteService);
    httpClient = TestBed.inject(HttpClient) as any;

    vi.resetAllMocks();
    httpClient.get.mockReturnValue(of([]));
    httpClient.post.mockReturnValue(of({}));
    httpClient.delete.mockReturnValue(of(undefined));
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch notes for a book', async () => {
    const notes: BookNote[] = [
      {id: 1, userId: 1, bookId: 42, title: 'Note A', content: 'Content A', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z'}
    ];
    httpClient.get.mockReturnValue(of(notes));

    const result = await firstValueFrom(service.getNotesForBook(42));

    expect(httpClient.get).toHaveBeenCalledWith('http://localhost:6060/api/v1/book-notes/book/42');
    expect(result).toEqual(notes);
  });

  it('should create or update a note', async () => {
    const request: CreateBookNoteRequest = {bookId: 42, title: 'Note B', content: 'Content B'};
    const note: BookNote = {
      id: 2,
      userId: 1,
      bookId: 42,
      title: 'Note B',
      content: 'Content B',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z'
    };
    httpClient.post.mockReturnValue(of(note));

    const result = await firstValueFrom(service.createOrUpdateNote(request));

    expect(httpClient.post).toHaveBeenCalledWith('http://localhost:6060/api/v1/book-notes', request);
    expect(result).toEqual(note);
  });

  it('should delete a note', async () => {
    httpClient.delete.mockReturnValue(of(undefined));

    const result = await firstValueFrom(service.deleteNote(1));

    expect(httpClient.delete).toHaveBeenCalledWith('http://localhost:6060/api/v1/book-notes/1');
    expect(result).toBeUndefined();
  });

  it('should propagate errors from the backend', () => {
    httpClient.post.mockReturnValue(throwError(() => ({message: 'save failed'})));

    let error: any;
    service.createOrUpdateNote({bookId: 42, title: '', content: ''}).subscribe({error: e => error = e});

    expect(error).toEqual({message: 'save failed'});
  });
});
