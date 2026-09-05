import {beforeEach, describe, expect, it} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {firstValueFrom, take} from 'rxjs';
import {BookNavigationService} from './book-navigation.service';

describe('BookNavigationService', () => {
  let service: BookNavigationService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [BookNavigationService]
    });

    service = TestBed.inject(BookNavigationService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('available book ids', () => {
    it('should set and return available book ids', () => {
      const ids = [10, 20, 30];
      service.setAvailableBookIds(ids);

      expect(service.getAvailableBookIds()).toBe(ids);
    });
  });

  describe('navigation context', () => {
    it('should emit navigation state when current book is in the list', async () => {
      service.setNavigationContext([1, 2, 3], 2);

      const state = await firstValueFrom(service.getNavigationState().pipe(take(1)));

      expect(state).toEqual({bookIds: [1, 2, 3], currentIndex: 1});
    });

    it('should emit null when current book is not in the list', async () => {
      service.setNavigationContext([1, 2, 3], 99);

      const state = await firstValueFrom(service.getNavigationState().pipe(take(1)));

      expect(state).toBeNull();
    });

    it('should update current book index', async () => {
      service.setNavigationContext([1, 2, 3], 1);
      service.updateCurrentBook(3);

      const state = await firstValueFrom(service.getNavigationState().pipe(take(1)));

      expect(state).toEqual({bookIds: [1, 2, 3], currentIndex: 2});
    });

    it('should not emit when updating with an unknown book id', async () => {
      service.setNavigationContext([1, 2, 3], 1);
      service.updateCurrentBook(99);

      const state = await firstValueFrom(service.getNavigationState().pipe(take(1)));

      expect(state).toEqual({bookIds: [1, 2, 3], currentIndex: 0});
    });
  });

  describe('navigation helpers', () => {
    beforeEach(() => {
      service.setNavigationContext([10, 20, 30], 20);
    });

    it('should indicate previous navigation is available', () => {
      expect(service.canNavigatePrevious()).toBe(true);
    });

    it('should indicate next navigation is available', () => {
      expect(service.canNavigateNext()).toBe(true);
    });

    it('should return the previous book id', () => {
      expect(service.getPreviousBookId()).toBe(10);
    });

    it('should return the next book id', () => {
      expect(service.getNextBookId()).toBe(30);
    });

    it('should return current position', () => {
      expect(service.getCurrentPosition()).toEqual({current: 2, total: 3});
    });

    it('should not allow previous navigation on the first book', () => {
      service.setNavigationContext([10, 20, 30], 10);
      expect(service.canNavigatePrevious()).toBe(false);
      expect(service.getPreviousBookId()).toBeNull();
    });

    it('should not allow next navigation on the last book', () => {
      service.setNavigationContext([10, 20, 30], 30);
      expect(service.canNavigateNext()).toBe(false);
      expect(service.getNextBookId()).toBeNull();
    });

    it('should return null for position when no state exists', () => {
      const freshService = new BookNavigationService();
      expect(freshService.getCurrentPosition()).toBeNull();
    });
  });
});
