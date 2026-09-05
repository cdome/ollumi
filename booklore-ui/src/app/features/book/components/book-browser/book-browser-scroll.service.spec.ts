import {beforeEach, describe, expect, it} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {BookBrowserScrollService} from './book-browser-scroll.service';

describe('BookBrowserScrollService', () => {
  let service: BookBrowserScrollService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [BookBrowserScrollService]
    });
    service = TestBed.inject(BookBrowserScrollService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('savePosition', () => {
    it('should save and return a scroll position', () => {
      service.savePosition('key-1', 120);

      expect(service.getPosition('key-1')).toBe(120);
    });

    it('should return undefined for an unknown key', () => {
      expect(service.getPosition('unknown')).toBeUndefined();
    });

    it('should overwrite an existing position', () => {
      service.savePosition('key-1', 100);
      service.savePosition('key-1', 250);

      expect(service.getPosition('key-1')).toBe(250);
    });
  });

  describe('clearPosition', () => {
    it('should remove a saved position', () => {
      service.savePosition('key-1', 100);
      service.clearPosition('key-1');

      expect(service.getPosition('key-1')).toBeUndefined();
    });

    it('should do nothing for an unknown key', () => {
      expect(() => service.clearPosition('unknown')).not.toThrow();
    });
  });

  describe('createKey', () => {
    it('should return the path when there are no params', () => {
      expect(service.createKey('all-books', {})).toBe('all-books');
    });

    it('should append param values joined by a colon', () => {
      expect(service.createKey('library', {libraryId: '5'})).toBe('library:5');
    });

    it('should join multiple param values in insertion order', () => {
      expect(service.createKey('shelf', {shelfId: '3', view: 'grid'})).toBe('shelf:3-grid');
    });
  });
});
