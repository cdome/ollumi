import {describe, expect, it} from 'vitest';

import {applyModifier, replacePlaceholders} from './pattern-resolver';

describe('applyModifier', () => {
  it('returns empty/falsy values unchanged', () => {
    expect(applyModifier('', 'upper', 'title')).toBe('');
  });

  it('first: takes the first comma-separated entry', () => {
    expect(applyModifier('Orwell, Huxley, Bradbury', 'first', 'authors')).toBe('Orwell');
  });

  it('sort: converts "First Last" into "Last, First"', () => {
    expect(applyModifier('George Orwell', 'sort', 'authors')).toBe('Orwell, George');
  });

  it('sort: leaves a single-word value untouched', () => {
    expect(applyModifier('Plato', 'sort', 'authors')).toBe('Plato');
  });

  it('initial: uppercases the first character of the surname for authors', () => {
    expect(applyModifier('George Orwell', 'initial', 'authors')).toBe('O');
  });

  it('initial: uses the first character for non-author fields', () => {
    expect(applyModifier('dune', 'initial', 'title')).toBe('D');
  });

  it('upper / lower change case', () => {
    expect(applyModifier('Dune', 'upper', 'title')).toBe('DUNE');
    expect(applyModifier('Dune', 'lower', 'title')).toBe('dune');
  });

  it('an unknown modifier returns the value unchanged', () => {
    expect(applyModifier('Dune', 'sideways', 'title')).toBe('Dune');
  });
});

describe('replacePlaceholders', () => {
  it('substitutes plain placeholders', () => {
    expect(replacePlaceholders('{title} by {authors}', {title: 'Dune', authors: 'Herbert'}))
      .toBe('Dune by Herbert');
  });

  it('applies a modifier inside a placeholder', () => {
    expect(replacePlaceholders('{authors:first}', {authors: 'Orwell, Huxley'})).toBe('Orwell');
  });

  it('renders an optional block only when all its placeholders are present', () => {
    const pattern = '{title}<, Book {seriesNumber}>';
    expect(replacePlaceholders(pattern, {title: 'Dune', seriesNumber: '1'})).toBe('Dune, Book 1');
    expect(replacePlaceholders(pattern, {title: 'Dune', seriesNumber: ''})).toBe('Dune');
  });

  it('uses the fallback branch of an optional block when the primary is incomplete', () => {
    const pattern = '<{seriesName} #{seriesNumber}|{title}>';
    expect(replacePlaceholders(pattern, {seriesName: 'Dune', seriesNumber: '2', title: 'X'}))
      .toBe('Dune #2');
    expect(replacePlaceholders(pattern, {seriesName: '', seriesNumber: '', title: 'Standalone'}))
      .toBe('Standalone');
  });

  it('treats whitespace-only values as absent for block resolution', () => {
    expect(replacePlaceholders('<{author}|unknown>', {author: '   '})).toBe('unknown');
  });

  it('trims the final result', () => {
    expect(replacePlaceholders('  {title}  ', {title: 'Dune'})).toBe('Dune');
  });
});
