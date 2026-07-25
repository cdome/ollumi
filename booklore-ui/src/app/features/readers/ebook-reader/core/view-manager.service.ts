import {inject, Injectable} from '@angular/core';
import {defer, from, Observable, of, throwError, timer} from 'rxjs';
import {catchError, map, switchMap} from 'rxjs/operators';
import {ReaderAnnotationService, Annotation} from '../features/annotations/annotation-renderer.service';
import {ReaderEventService, ViewEvent, TextSelection} from './event.service';
import {PageInfo, ThemeInfo, PageDecorator} from '../shared/header-footer.util';
import {EpubStreamingService, EpubBookInfo} from './epub-streaming.service';
import {FoliateRenderer, FoliateSearchOptions, FoliateSearchYield, FoliateTocItem, FoliateView} from './foliate-view.model';

export type {ViewEvent, TextSelection} from './event.service';
export type {PageInfo, ThemeInfo} from '../shared/header-footer.util';

interface TocItem {
  label: string;
  href: string;
  subitems?: TocItem[];
}

type StreamingBookFactory = (bookId: number, baseUrl: string, bookInfo: EpubBookInfo, authToken: string | null, bookType?: string) => Promise<unknown>;

export interface BookMetadata {
  title?: string;
  authors?: string[];
  language?: string;
  publisher?: string;
  description?: string;
  identifier?: string;
  coverUrl?: string;

  [key: string]: unknown;
}

@Injectable({
  providedIn: 'root'
})
export class ReaderViewManagerService {
  private annotationService = inject(ReaderAnnotationService);
  private eventService = inject(ReaderEventService);
  private epubStreamingService = inject(EpubStreamingService);
  private view: FoliateView | null = null;

  public get events$(): Observable<ViewEvent> {
    return this.eventService.events$;
  }

  createView(container: HTMLElement): void {
    this.view = document.createElement('foliate-view') as FoliateView;
    this.view.style.width = '100%';
    this.view.style.height = '100%';
    this.view.style.display = 'block';
    container.appendChild(this.view);

    this.eventService.initialize(this.view, {
      prev: () => this.prev(),
      next: () => this.next(),
      getCFI: (index: number, range: Range) => this.view?.getCFI(index, range) ?? null,
      getContents: () => this.view?.renderer?.getContents() ?? null
    });
  }

  loadEpub(epubPath: string): Observable<void> {
    const view = this.view;
    if (!view) {
      return throwError(() => new Error('View not created'));
    }

    return timer(100).pipe(
      switchMap(() => from(fetch(epubPath))),
      switchMap(response => {
        if (!response.ok) {
          throw new Error(`EPUB not found: ${response.status}`);
        }
        return from(response.blob());
      }),
      switchMap(blob => {
        const file = new File([blob], epubPath.split('/').pop() || 'book.epub', {
          type: 'application/epub+zip'
        });
        return from(view.open(file));
      }),
      map(() => undefined),
      catchError(err => throwError(() => err))
    );
  }

  loadEpubStreaming(bookId: number, bookType?: string): Observable<void> {
    if (!this.view) {
      return throwError(() => new Error('View not created'));
    }

    return this.epubStreamingService.getBookInfo(bookId, bookType).pipe(
      switchMap(bookInfo => from(this.openStreamingBook(bookId, bookInfo, bookType))),
      map(() => undefined),
      catchError(err => throwError(() => err))
    );
  }

  private async openStreamingBook(bookId: number, bookInfo: EpubBookInfo, bookType?: string): Promise<void> {
    const {makeStreamingBook} = window as Window & {makeStreamingBook?: StreamingBookFactory};
    if (!makeStreamingBook) {
      throw new Error('makeStreamingBook not available - Foliate script may not be loaded');
    }
    const baseUrl = this.epubStreamingService.getBaseUrl();
    const authToken = this.epubStreamingService.getAuthToken();
    const book = await makeStreamingBook(bookId, baseUrl, bookInfo, authToken, bookType);
    await this.view?.open(book);
  }

  destroy(): void {
    this.eventService.destroy();
    this.view?.remove();
    this.view = null;
  }

  goTo(target?: string | number | null): Observable<void> {
    const resolvedTarget = target ?? 0;
    const view = this.view;
    if (!view) {
      return of(undefined);
    }
    return defer(() =>
      from(view.goTo(resolvedTarget))
    ).pipe(
      map(() => undefined)
    );
  }

  goToSection(index: number): Observable<void> {
    return this.goTo(index);
  }

  goToFraction(fraction: number): Observable<void> {
    const view = this.view;
    if (!view) {
      return of(undefined);
    }
    return defer(() => from(view.goToFraction(fraction))).pipe(
      map(() => undefined)
    );
  }

  prev(): void {
    this.view?.prev();
  }

  next(): void {
    this.view?.next();
  }

  getRenderer(): FoliateRenderer | undefined {
    return this.view?.renderer;
  }

  getSelection(): TextSelection | null {
    const renderer = this.getRenderer();
    if (!renderer) return null;

    const contents = renderer.getContents();
    if (!contents || contents.length === 0) return null;

    const {index, doc} = contents[0];
    if (!doc) return null;

    const selection = doc.defaultView?.getSelection();
    if (!selection || selection.rangeCount === 0 || selection.isCollapsed) return null;

    const range = selection.getRangeAt(0);
    const text = range.toString().trim();
    if (!text) return null;

    const cfi = this.view?.getCFI(index, range);
    if (!cfi) return null;

    return {text, cfi, range, index};
  }

  clearSelection(): void {
    this.view?.deselect();
  }

  addAnnotation(annotation: Annotation): Observable<{index: number; label: string} | undefined> {
    return this.annotationService.addAnnotation(this.view, annotation);
  }

  deleteAnnotation(cfi: string): Observable<void> {
    return this.annotationService.deleteAnnotation(this.view, cfi);
  }

  addAnnotations(annotations: Annotation[]): void {
    this.annotationService.addAnnotations(this.view, annotations);
  }

  updateHeadersAndFooters(chapterName: string, pageInfo?: PageInfo, theme?: ThemeInfo, timeRemainingLabel?: string): void {
    const renderer = this.getRenderer();
    PageDecorator.updateHeadersAndFooters(renderer, chapterName, pageInfo, theme, timeRemainingLabel);
  }

  getChapters(): TocItem[] {
    const toc = this.view?.book?.toc;
    if (!toc) return [];

    const mapToc = (items: FoliateTocItem[]): TocItem[] =>
      items.map(item => ({
        label: item.label,
        href: item.href,
        subitems: item.subitems?.length ? mapToc(item.subitems) : undefined
      }));

    return mapToc(toc);
  }

  getSectionFractions(): number[] {
    return this.view?.getSectionFractions?.() ?? [];
  }

  getMetadata(): Observable<BookMetadata> {
    const metadata = this.view?.book?.metadata;
    if (!metadata) {
      return of({});
    }

    return this.getCoverUrl().pipe(
      map(coverUrl => ({
        title: metadata.title,
        authors: metadata.authors,
        language: metadata.language,
        publisher: metadata.publisher,
        description: metadata.description,
        identifier: metadata.identifier,
        coverUrl: coverUrl ?? undefined,
        ...metadata
      }))
    );
  }

  getCover(): Observable<Blob | null> {
    const getCover = this.view?.book?.getCover?.bind(this.view.book);
    if (!getCover) {
      return of(null);
    }
    return defer(() => {
      const coverPromise = getCover();
      return coverPromise ? from(coverPromise) : of(null);
    });
  }

  getCoverUrl(): Observable<string | null> {
    return this.getCover().pipe(
      map(blob => blob ? URL.createObjectURL(blob) : null)
    );
  }

  async* search(opts: FoliateSearchOptions): AsyncGenerator<FoliateSearchYield> {
    if (!this.view?.search) return;
    yield* this.view.search(opts);
  }

  clearSearch(): void {
    this.view?.clearSearch?.();
  }
}
