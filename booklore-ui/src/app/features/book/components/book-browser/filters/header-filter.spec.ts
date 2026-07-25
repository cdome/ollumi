import {describe, expect, it} from 'vitest';

import {filterBooksBySearchTerm, normalizeSearchTerm} from './HeaderFilter';
import {makeBook} from '../../../../../testing/book.fixture';

describe('normalizeSearchTerm', () => {
  it('returns empty string for falsy input', () => {
    expect(normalizeSearchTerm('')).toBe('');
  });

  it('strips diacritics via Unicode decomposition', () => {
    expect(normalizeSearchTerm('Rémy')).toBe('remy');
  });

  it('transliterates special latin letters', () => {
    expect(normalizeSearchTerm('Bjørn')).toBe('bjorn');
    expect(normalizeSearchTerm('Straße')).toBe('strasse');
    expect(normalizeSearchTerm('Æther œuvre')).toBe('aether oeuvre');
  });

  it('removes punctuation and collapses whitespace', () => {
    expect(normalizeSearchTerm("Harry  Potter & the *Goblet*!")).toBe('harry potter the goblet');
  });
});

describe('filterBooksBySearchTerm', () => {
  const dune = makeBook({id: 1, metadata: {title: 'Dune', authors: ['Frank Herbert'], seriesName: 'Dune Chronicles'}});
  const foundation = makeBook({id: 2, metadata: {title: 'Foundation', authors: ['Isaac Asimov'], isbn13: '9780553293357'}});
  const books = [dune, foundation];

  it('returns all books for a term shorter than 2 characters', () => {
    expect(filterBooksBySearchTerm(books, 'd')).toBe(books);
    expect(filterBooksBySearchTerm(books, '')).toBe(books);
  });

  it('matches on title', () => {
    expect(filterBooksBySearchTerm(books, 'found').map(b => b.id)).toEqual([2]);
  });

  it('matches on author', () => {
    expect(filterBooksBySearchTerm(books, 'herbert').map(b => b.id)).toEqual([1]);
  });

  it('matches on series name', () => {
    expect(filterBooksBySearchTerm(books, 'chronicles').map(b => b.id)).toEqual([1]);
  });

  it('matches on isbn13', () => {
    expect(filterBooksBySearchTerm(books, '9780553293357').map(b => b.id)).toEqual([2]);
  });

  it('is diacritic-insensitive on both term and content', () => {
    const remy = makeBook({id: 3, metadata: {title: 'Rémy'}});
    expect(filterBooksBySearchTerm([remy], 'remy').map(b => b.id)).toEqual([3]);
  });

  it('returns an empty list when nothing matches', () => {
    expect(filterBooksBySearchTerm(books, 'zzzznope')).toEqual([]);
  });
});
