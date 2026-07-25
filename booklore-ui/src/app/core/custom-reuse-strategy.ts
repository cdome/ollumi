import {ComponentRef, inject, Injectable} from '@angular/core';
import {ActivatedRouteSnapshot, DetachedRouteHandle, RouteReuseStrategy} from '@angular/router';
import {BookBrowserScrollService} from '../features/book/components/book-browser/book-browser-scroll.service';
import {BookSelectionService} from '../features/book/components/book-browser/book-selection.service';

@Injectable({
  providedIn: 'root',
})
export class CustomReuseStrategy implements RouteReuseStrategy {
  // Cap on cached book-browser instances. Each stored handle retains a full
  // component tree (books, subscriptions, DOM), so the map must be bounded and
  // evicted handles must be destroyed — otherwise every distinct library/shelf/
  // magic-shelf/author/series route ever visited leaks for the app's lifetime.
  private static readonly MAX_STORED_ROUTES = 5;

  // Insertion order doubles as LRU order: the least-recently-used key is first.
  private storedRoutes = new Map<string, DetachedRouteHandle>();
  private scrollService = inject(BookBrowserScrollService);
  private bookSelectionService = inject(BookSelectionService);

  private readonly BOOK_BROWSER_PATHS = [
    'all-books',
    'unshelved-books',
    'library/:libraryId/books',
    'shelf/:shelfId/books',
    'magic-shelf/:magicShelfId/books',
    'authors',
    'series'
  ];

  private readonly BOOK_DETAILS_PATH = 'book/:bookId';

  private getRouteKey(route: ActivatedRouteSnapshot): string {
    const path = route.routeConfig?.path || '';
    return this.scrollService.createKey(path, route.params);
  }

  private isBookBrowserRoute(route: ActivatedRouteSnapshot): boolean {
    const path = route.routeConfig?.path;
    return this.BOOK_BROWSER_PATHS.includes(path || '');
  }

  shouldDetach(route: ActivatedRouteSnapshot): boolean {
    return this.isBookBrowserRoute(route);
  }

  store(route: ActivatedRouteSnapshot, handle: DetachedRouteHandle | null): void {
    if (handle && this.isBookBrowserRoute(route)) {
      const key = this.getRouteKey(route);

      // Re-storing a key with a replacement handle: destroy the old component.
      const existing = this.storedRoutes.get(key);
      if (existing && existing !== handle) {
        this.destroyHandle(existing);
      }

      // Delete-then-set moves the key to the end → marks it most-recently-used.
      this.storedRoutes.delete(key);
      this.storedRoutes.set(key, handle);
      this.bookSelectionService.deselectAll();

      this.evictOverflow();
    }
  }

  shouldAttach(route: ActivatedRouteSnapshot): boolean {
    if (!this.isBookBrowserRoute(route)) {
      return false;
    }
    const key = this.getRouteKey(route);
    return this.storedRoutes.has(key);
  }

  retrieve(route: ActivatedRouteSnapshot): DetachedRouteHandle | null {
    const key = this.getRouteKey(route);
    const handle = this.storedRoutes.get(key) || null;

    if (handle) {
      // Reattaching this route makes it the most-recently-used entry.
      this.storedRoutes.delete(key);
      this.storedRoutes.set(key, handle);

      const savedPosition = this.scrollService.getPosition(key);
      if (savedPosition !== undefined) {
        setTimeout(() => {
          const scrollElement = document.querySelector('.virtual-scroller');
          if (scrollElement) {
            (scrollElement as HTMLElement).scrollTop = savedPosition;
          }
        }, 0);
      }
    }

    return handle;
  }

  shouldReuseRoute(future: ActivatedRouteSnapshot, curr: ActivatedRouteSnapshot): boolean {
    return future.routeConfig === curr.routeConfig &&
      JSON.stringify(future.params) === JSON.stringify(curr.params);
  }

  private evictOverflow(): void {
    while (this.storedRoutes.size > CustomReuseStrategy.MAX_STORED_ROUTES) {
      const oldestKey = this.storedRoutes.keys().next().value;
      if (oldestKey === undefined) {
        return;
      }
      const handle = this.storedRoutes.get(oldestKey);
      this.storedRoutes.delete(oldestKey);
      this.scrollService.clearPosition(oldestKey);
      if (handle) {
        this.destroyHandle(handle);
      }
    }
  }

  // A DetachedRouteHandle from Angular's default reuse machinery carries the
  // component's ComponentRef; destroying it runs ngOnDestroy so the cached
  // component tree and its subscriptions are actually released.
  private destroyHandle(handle: DetachedRouteHandle): void {
    const componentRef = (handle as {componentRef?: ComponentRef<unknown>}).componentRef;
    componentRef?.destroy();
  }
}
