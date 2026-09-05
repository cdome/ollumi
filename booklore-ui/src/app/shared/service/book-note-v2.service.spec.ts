import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom, of, throwError} from 'rxjs';
import {
  BookNoteV2Service,
  BookNoteV2,
  CreateBookNoteV2Request,
  UpdateBookNoteV2Request
} from './book-note-v2.service';
import {mockHttpClientProvider} from '../../../testing/providers';

describe('BookNoteV2Service', () => {
  let service: BookNoteV2Service;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        BookNoteV2Service,
        mockHttpClientProvider
      ]
    });

    service = TestBed.inject(BookNoteV2Service);
    httpClient = TestBed.inject(HttpClient) as any;

    vi.resetAllMocks();
    httpClient.get.mockReturnValue(of([]));
    httpClient.post.mockReturnValue(of({}));
    httpClient.put.mockReturnValue(of({}));
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
    const notes: BookNoteV2[] = [
      {id: 1, bookId: 42, cfi: 'cfi-1', noteContent: 'A note', createdAt: '2026-01-01T00:00:00Z'}
    ];
    httpClient.get.mockReturnValue(of(notes));

    const result = await firstValueFrom(service.getNotesForBook(42));

    expect(httpClient.get).toHaveBeenCalledWith('http://localhost:6060/api/v2/book-notes/book/42');
    expect(result).toEqual(notes);
  });

  it('should create a note', async () => {
    const request: CreateBookNoteV2Request = {bookId: 42, cfi: 'cfi-2', noteContent: 'New note'};
    const note: BookNoteV2 = {
      id: 2,
      bookId: 42,
      cfi: 'cfi-2',
      noteContent: 'New note',
      createdAt: '2026-01-01T00:00:00Z'
    };
    httpClient.post.mockReturnValue(of(note));

    const result = await firstValueFrom(service.createNote(request));

    expect(httpClient.post).toHaveBeenCalledWith('http://localhost:6060/api/v2/book-notes', request);
    expect(result).toEqual(note);
  });

  it('should update a note', async () => {
    const request: UpdateBookNoteV2Request = {noteContent: 'Updated note', color: 'blue'};
    const note: BookNoteV2 = {
      id: 1,
      bookId: 42,
      cfi: 'cfi-1',
      noteContent: 'Updated note',
      color: 'blue',
      createdAt: '2026-01-01T00:00:00Z'
    };
    httpClient.put.mockReturnValue(of(note));

    const result = await firstValueFrom(service.updateNote(1, request));

    expect(httpClient.put).toHaveBeenCalledWith('http://localhost:6060/api/v2/book-notes/1', request);
    expect(result).toEqual(note);
  });

  it('should delete a note', async () => {
    httpClient.delete.mockReturnValue(of(undefined));

    const result = await firstValueFrom(service.deleteNote(1));

    expect(httpClient.delete).toHaveBeenCalledWith('http://localhost:6060/api/v2/book-notes/1');
    expect(result).toBeUndefined();
  });

  it('should propagate errors from the backend', () => {
    httpClient.delete.mockReturnValue(throwError(() => ({message: 'delete failed'})));

    let error: any;
    service.deleteNote(1).subscribe({error: e => error = e});

    expect(error).toEqual({message: 'delete failed'});
  });
});
