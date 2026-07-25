import {ReadStatus} from './book.model';

/**
 * Lightweight per-book row returned by the paged books endpoint
 * (`GET /api/v1/app/books`). Mirrors the backend AppBookSummary — far smaller
 * than the full {@link Book} the in-memory store loads today.
 */
export interface PagedBookSummary {
  id: number;
  title?: string;
  authors?: string[];
  thumbnailUrl?: string;
  readStatus?: ReadStatus;
  personalRating?: number;
  seriesName?: string;
  seriesNumber?: number;
  libraryId?: number;
  addedOn?: string;
  lastReadTime?: string;
  readProgress?: number;
  primaryFileType?: string;
  coverUpdatedOn?: string;
  audiobookCoverUpdatedOn?: string;
  isPhysical?: boolean;
}

/** Backend AppPageResponse envelope. */
export interface PagedBooksResponse {
  content: PagedBookSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

/**
 * Query parameters the paged endpoint understands today. Filter coverage is
 * intentionally the current server-supported subset; it grows as the backend
 * query layer gains conditions (tracked with the jOOQ migration).
 */
export interface PagedBooksQuery {
  page?: number;
  size?: number;
  sort?: string;
  dir?: 'asc' | 'desc';
  libraryId?: number;
  shelfId?: number;
  status?: ReadStatus;
  search?: string;
  fileType?: string;
  minRating?: number;
  maxRating?: number;
  authors?: string;
  language?: string;
}

/** The base query for an infinite scroll — everything except the page cursor. */
export type PagedBooksBaseQuery = Omit<PagedBooksQuery, 'page'>;
