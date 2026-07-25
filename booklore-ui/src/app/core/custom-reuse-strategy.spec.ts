import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {ActivatedRouteSnapshot, DetachedRouteHandle} from '@angular/router';

import {CustomReuseStrategy} from './custom-reuse-strategy';
import {BookBrowserScrollService} from '../features/book/components/book-browser/book-browser-scroll.service';
import {BookSelectionService} from '../features/book/components/book-browser/book-selection.service';

// A DetachedRouteHandle is opaque; the reuse strategy reaches through to the
// componentRef, so a fake handle just needs a spied destroy().
function fakeHandle() {
  const destroy = vi.fn();
  return {handle: {componentRef: {destroy}} as unknown as DetachedRouteHandle, destroy};
}

function routeFor(path: string, params: Record<string, string> = {}): ActivatedRouteSnapshot {
  return {routeConfig: {path}, params} as unknown as ActivatedRouteSnapshot;
}

describe('CustomReuseStrategy', () => {
  let strategy: CustomReuseStrategy;
  let scroll: BookBrowserScrollService;
  let deselectAll: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    deselectAll = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        CustomReuseStrategy,
        BookBrowserScrollService,
        {provide: BookSelectionService, useValue: {deselectAll}},
      ],
    });
    strategy = TestBed.inject(CustomReuseStrategy);
    scroll = TestBed.inject(BookBrowserScrollService);
  });

  describe('detach eligibility', () => {
    it('detaches book-browser routes but not others', () => {
      expect(strategy.shouldDetach(routeFor('all-books'))).toBe(true);
      expect(strategy.shouldDetach(routeFor('library/:libraryId/books', {libraryId: '3'}))).toBe(true);
      expect(strategy.shouldDetach(routeFor('book/:bookId', {bookId: '9'}))).toBe(false);
      expect(strategy.shouldDetach(routeFor('settings'))).toBe(false);
    });
  });

  describe('store / attach / retrieve round-trip', () => {
    it('stores a browser route and reattaches the same handle', () => {
      const route = routeFor('all-books');
      const {handle} = fakeHandle();

      strategy.store(route, handle);

      expect(strategy.shouldAttach(route)).toBe(true);
      expect(strategy.retrieve(route)).toBe(handle);
      expect(deselectAll).toHaveBeenCalled();
    });

    it('does not store non-browser routes', () => {
      const route = routeFor('settings');
      strategy.store(route, fakeHandle().handle);
      expect(strategy.shouldAttach(route)).toBe(false);
    });

    it('keys distinct entities separately', () => {
      const lib3 = routeFor('library/:libraryId/books', {libraryId: '3'});
      const lib4 = routeFor('library/:libraryId/books', {libraryId: '4'});
      strategy.store(lib3, fakeHandle().handle);

      expect(strategy.shouldAttach(lib3)).toBe(true);
      expect(strategy.shouldAttach(lib4)).toBe(false);
    });
  });

  describe('overwriting a key', () => {
    it('destroys the previous component when a key is re-stored with a new handle', () => {
      const route = routeFor('all-books');
      const first = fakeHandle();
      const second = fakeHandle();

      strategy.store(route, first.handle);
      strategy.store(route, second.handle);

      expect(first.destroy).toHaveBeenCalledOnce();
      expect(strategy.retrieve(route)).toBe(second.handle);
    });
  });

  describe('bounded LRU eviction', () => {
    const routes = Array.from({length: 6}, (_, i) =>
      routeFor('library/:libraryId/books', {libraryId: String(i)}),
    );

    it('evicts and destroys the least-recently-used handle past the cap of 5', () => {
      const handles = routes.map(() => fakeHandle());
      routes.forEach((r, i) => strategy.store(r, handles[i].handle));

      // The first (oldest) route is evicted and its component destroyed.
      expect(handles[0].destroy).toHaveBeenCalledOnce();
      expect(strategy.shouldAttach(routes[0])).toBe(false);
      // The rest are retained.
      for (let i = 1; i < 6; i++) {
        expect(strategy.shouldAttach(routes[i])).toBe(true);
        expect(handles[i].destroy).not.toHaveBeenCalled();
      }
    });

    it('clears the scroll position of an evicted route', () => {
      const clearSpy = vi.spyOn(scroll, 'clearPosition');
      const handles = routes.map(() => fakeHandle());
      routes.forEach((r, i) => strategy.store(r, handles[i].handle));

      expect(clearSpy).toHaveBeenCalledWith('library/:libraryId/books:0');
    });

    it('treats retrieve as a use, sparing a route that would otherwise be evicted', () => {
      const handles = routes.slice(0, 5).map(() => fakeHandle());
      routes.slice(0, 5).forEach((r, i) => strategy.store(r, handles[i].handle));

      // Touch the oldest route so it is no longer the LRU victim.
      strategy.retrieve(routes[0]);
      // Store a 6th route → eviction should now drop route[1], not route[0].
      strategy.store(routes[5], fakeHandle().handle);

      expect(strategy.shouldAttach(routes[0])).toBe(true);
      expect(handles[0].destroy).not.toHaveBeenCalled();
      expect(strategy.shouldAttach(routes[1])).toBe(false);
      expect(handles[1].destroy).toHaveBeenCalledOnce();
    });
  });
});
