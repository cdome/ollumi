import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {BookStateService} from './book-state.service';
import {BookState} from '../model/state/book-state.model';
import {createMockBook} from '../../../../testing/factories';

describe('BookStateService', () => {
  let service: BookStateService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [BookStateService]
    });

    service = TestBed.inject(BookStateService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should expose the initial state via getCurrentBookState', () => {
    const state = service.getCurrentBookState();

    expect(state.books).toBeNull();
    expect(state.loaded).toBe(false);
    expect(state.error).toBeNull();
  });

  it('should emit the initial state on bookState$', () => {
    const emissions: BookState[] = [];
    service.bookState$.subscribe(state => emissions.push(state));

    expect(emissions.length).toBe(1);
    expect(emissions[0]).toEqual(service.getCurrentBookState());
  });

  it('should update book state and emit the new state', () => {
    const book = createMockBook({id: 42});
    const newState: BookState = {books: [book], loaded: true, error: null};
    const emissions: BookState[] = [];

    service.bookState$.subscribe(state => emissions.push(state));
    service.updateBookState(newState);

    expect(service.getCurrentBookState()).toEqual(newState);
    expect(emissions.length).toBe(2);
    expect(emissions[emissions.length - 1]).toEqual(newState);
  });

  it('should reset book state to loaded with no books and no error', () => {
    const emissions: BookState[] = [];
    service.bookState$.subscribe(state => emissions.push(state));
    service.updateBookState({books: [createMockBook()], loaded: true, error: 'error'});

    service.resetBookState();

    const state = service.getCurrentBookState();
    expect(state.books).toBeNull();
    expect(state.loaded).toBe(true);
    expect(state.error).toBeNull();
    expect(emissions.length).toBe(3);
    expect(emissions[emissions.length - 1]).toEqual(state);
  });
});
