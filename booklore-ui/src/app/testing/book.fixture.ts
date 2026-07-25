import {Book, BookMetadata} from '../features/book/model/book.model';

/**
 * Minimal Book factory for unit tests. Only the fields a test cares about need
 * to be passed; everything else gets a benign default. Metadata is merged
 * shallowly so `metadata: {title: 'x'}` does not wipe the other defaults.
 */
export type BookOverrides = Partial<Omit<Book, 'metadata'>> & {metadata?: Partial<BookMetadata>};

export function makeBook(overrides: BookOverrides = {}): Book {
  // Distinguish "metadata not passed" (→ default) from an explicit
  // `metadata: undefined` (→ genuinely no metadata), which some tests need.
  const hasMetadataKey = 'metadata' in overrides;
  const {metadata, ...rest} = overrides;
  const resolvedMetadata = hasMetadataKey
    ? (metadata === undefined ? undefined : makeMetadata(metadata))
    : makeMetadata();
  return {
    id: 1,
    libraryId: 1,
    metadata: resolvedMetadata,
    ...rest,
  } as Book;
}

export function makeMetadata(overrides: Partial<BookMetadata> = {}): BookMetadata {
  return {
    bookId: 1,
    title: 'Untitled',
    ...overrides,
  } as BookMetadata;
}
