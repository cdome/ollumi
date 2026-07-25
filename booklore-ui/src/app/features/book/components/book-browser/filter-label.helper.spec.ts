import {describe, expect, it} from 'vitest';

import {FilterLabelHelper} from './filter-label.helper';

describe('FilterLabelHelper.getFilterTypeName', () => {
  it('maps known filter types to display names', () => {
    expect(FilterLabelHelper.getFilterTypeName('author')).toBe('Author');
    expect(FilterLabelHelper.getFilterTypeName('category')).toBe('Genre');
    expect(FilterLabelHelper.getFilterTypeName('readStatus')).toBe('Read Status');
  });

  it('capitalizes an unknown filter type as a fallback', () => {
    expect(FilterLabelHelper.getFilterTypeName('narrator')).toBe('Narrator');
    expect(FilterLabelHelper.getFilterTypeName('somethingNew')).toBe('SomethingNew');
  });
});

describe('FilterLabelHelper.getFilterDisplayValue', () => {
  it('resolves file-size range ids to their labels', () => {
    expect(FilterLabelHelper.getFilterDisplayValue('fileSize', 0)).toBe('< 1 MB');
    expect(FilterLabelHelper.getFilterDisplayValue('fileSize', '1')).toBe('1–10 MB');
  });

  it('resolves page-count range ids to their labels', () => {
    expect(FilterLabelHelper.getFilterDisplayValue('pageCount', 2)).toBe('100–200 pages');
  });

  it('resolves match-score range ids to their labels', () => {
    expect(FilterLabelHelper.getFilterDisplayValue('matchScore', 0)).toBe('Outstanding (95–100%)');
  });

  it('resolves personal-rating ids to their labels', () => {
    expect(FilterLabelHelper.getFilterDisplayValue('personalRating', 7)).toBe('7');
  });

  it('resolves 5-star rating ids for provider ratings', () => {
    expect(FilterLabelHelper.getFilterDisplayValue('amazonRating', 4)).toBe('4 to 4.5');
    expect(FilterLabelHelper.getFilterDisplayValue('goodreadsRating', 5)).toBe('4.5+');
  });

  it('is case-insensitive on the filter type', () => {
    expect(FilterLabelHelper.getFilterDisplayValue('FILESIZE', 0)).toBe('< 1 MB');
  });

  it('returns the raw value as a string for unknown range ids and types', () => {
    expect(FilterLabelHelper.getFilterDisplayValue('fileSize', 999)).toBe('999');
    expect(FilterLabelHelper.getFilterDisplayValue('author', 'Orwell')).toBe('Orwell');
  });
});
