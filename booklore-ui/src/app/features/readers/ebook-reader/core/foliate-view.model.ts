// Minimal structural typings for the foliate-js <foliate-view> custom element.
// Only the surface booklore actually touches is modeled here — foliate-js ships
// no types of its own.

export interface FoliateContents {
  index: number;
  doc: Document;
}

export interface FoliateTocItem {
  label: string;
  href: string;
  subitems?: FoliateTocItem[];
}

export interface FoliateBookMetadata {
  title?: string;
  authors?: string[];
  language?: string;
  publisher?: string;
  description?: string;
  identifier?: string;

  [key: string]: unknown;
}

export interface FoliateBook {
  toc?: FoliateTocItem[];
  metadata?: FoliateBookMetadata;

  getCover?(): Promise<Blob | null> | null;
}

export interface FoliateRenderer extends HTMLElement {
  heads?: HTMLElement[];
  feet?: HTMLElement[];

  getContents(): FoliateContents[] | null;

  setStyles?(css: string): void;
}

export interface FoliateSearchOptions {
  query: string;
  matchCase?: boolean;
  matchWholeWords?: boolean;
}

export interface FoliateSearchSubitem {
  cfi: string;
  excerpt: { pre: string; match: string; post: string };
}

export type FoliateSearchYield =
  | 'done'
  | { progress: number }
  | { label?: string; subitems?: FoliateSearchSubitem[] };

export interface FoliateLoadDetail {
  doc: Document;
  index: number;
}

export interface FoliateRelocateDetail {
  fraction?: number;
  cfi?: string;
  tocItem?: FoliateTocItem;
  pageItem?: { label?: string; href?: string };
  section?: { current: number; total: number };
  time?: { section: number; total: number };
  location?: { current: number; next: number; total: number };
}

export interface FoliateAnnotationRef {
  value: string;
}

export interface FoliateDrawAnnotationDetail {
  draw: (drawFn: unknown, options: { color: string }) => void;
  annotation: FoliateAnnotationRef;
  doc: Document;
  range: Range;
}

export interface FoliateViewEventMap {
  'load': CustomEvent<FoliateLoadDetail>;
  'relocate': CustomEvent<FoliateRelocateDetail>;
  'error': CustomEvent<unknown>;
  'draw-annotation': CustomEvent<FoliateDrawAnnotationDetail>;
  'show-annotation': CustomEvent<{ value: string; index?: number }>;
}

export interface FoliateView extends HTMLElement {
  book?: FoliateBook;
  renderer?: FoliateRenderer;

  open(book: unknown): Promise<void>;

  goTo(target: string | number): Promise<void>;

  goToFraction(fraction: number): Promise<void>;

  prev(): void;

  next(): void;

  getCFI(index: number, range: Range): string | null;

  deselect(): void;

  addAnnotation(annotation: FoliateAnnotationRef): Promise<{ index: number; label: string } | undefined>;

  deleteAnnotation(annotation: FoliateAnnotationRef): Promise<void>;

  showAnnotation(annotation: FoliateAnnotationRef): Promise<void>;

  getSectionFractions?(): number[];

  search?(opts: FoliateSearchOptions): AsyncGenerator<FoliateSearchYield>;

  clearSearch?(): void;

  addEventListener<K extends keyof FoliateViewEventMap>(type: K, listener: (ev: FoliateViewEventMap[K]) => void): void;

  addEventListener(type: string, listener: EventListenerOrEventListenerObject, options?: boolean | AddEventListenerOptions): void;
}
