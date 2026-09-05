import {describe, expect, it} from 'vitest';
import {FilterLabelHelper} from './filter-label.helper';

describe('FilterLabelHelper', () => {
  describe('getFilterTypeName', () => {
    it('should return mapped name for known filter types', () => {
      expect(FilterLabelHelper.getFilterTypeName('author')).toBe('Author');
      expect(FilterLabelHelper.getFilterTypeName('category')).toBe('Genre');
      expect(FilterLabelHelper.getFilterTypeName('series')).toBe('Series');
      expect(FilterLabelHelper.getFilterTypeName('publisher')).toBe('Publisher');
      expect(FilterLabelHelper.getFilterTypeName('readStatus')).toBe('Read Status');
      expect(FilterLabelHelper.getFilterTypeName('personalRating')).toBe('Personal Rating');
      expect(FilterLabelHelper.getFilterTypeName('publishedDate')).toBe('Year Published');
      expect(FilterLabelHelper.getFilterTypeName('matchScore')).toBe('Metadata Match Score');
      expect(FilterLabelHelper.getFilterTypeName('language')).toBe('Language');
      expect(FilterLabelHelper.getFilterTypeName('bookType')).toBe('Book Type');
      expect(FilterLabelHelper.getFilterTypeName('shelfStatus')).toBe('Shelf Status');
      expect(FilterLabelHelper.getFilterTypeName('fileSize')).toBe('File Size');
      expect(FilterLabelHelper.getFilterTypeName('pageCount')).toBe('Page Count');
      expect(FilterLabelHelper.getFilterTypeName('amazonRating')).toBe('Amazon Rating');
      expect(FilterLabelHelper.getFilterTypeName('goodreadsRating')).toBe('Goodreads Rating');
      expect(FilterLabelHelper.getFilterTypeName('hardcoverRating')).toBe('Hardcover Rating');
      expect(FilterLabelHelper.getFilterTypeName('ranobedbRating')).toBe('Ranobedb Rating');
      expect(FilterLabelHelper.getFilterTypeName('mood')).toBe('Mood');
      expect(FilterLabelHelper.getFilterTypeName('tag')).toBe('Tag');
    });

    it('should capitalize unknown filter types', () => {
      expect(FilterLabelHelper.getFilterTypeName('customFilter')).toBe('CustomFilter');
      expect(FilterLabelHelper.getFilterTypeName('')).toBe('');
    });
  });

  describe('getFilterDisplayValue', () => {
    it('should return file size range label', () => {
      expect(FilterLabelHelper.getFilterDisplayValue('fileSize', 0)).toBe('< 1 MB');
      expect(FilterLabelHelper.getFilterDisplayValue('fileSize', '2')).toBe('10–50 MB');
    });

    it('should return page count range label', () => {
      expect(FilterLabelHelper.getFilterDisplayValue('pageCount', 1)).toBe('50–100 pages');
      expect(FilterLabelHelper.getFilterDisplayValue('pageCount', '6')).toBe('1000+ pages');
    });

    it('should return match score range label', () => {
      expect(FilterLabelHelper.getFilterDisplayValue('matchScore', 0)).toBe('Outstanding (95–100%)');
      expect(FilterLabelHelper.getFilterDisplayValue('matchScore', '3')).toBe('Good (70–79%)');
    });

    it('should return personal rating label', () => {
      expect(FilterLabelHelper.getFilterDisplayValue('personalRating', 5)).toBe('5');
      expect(FilterLabelHelper.getFilterDisplayValue('personalRating', '10')).toBe('10');
    });

    it('should return rating range label for amazon/goodreads/hardcover ratings', () => {
      expect(FilterLabelHelper.getFilterDisplayValue('amazonRating', 0)).toBe('0 to 1');
      expect(FilterLabelHelper.getFilterDisplayValue('goodreadsRating', 5)).toBe('4.5+');
      expect(FilterLabelHelper.getFilterDisplayValue('hardcoverRating', '3')).toBe('3 to 4');
    });

    it('should return string value for unknown range ids', () => {
      expect(FilterLabelHelper.getFilterDisplayValue('fileSize', 999)).toBe('999');
      expect(FilterLabelHelper.getFilterDisplayValue('pageCount', -1)).toBe('-1');
      expect(FilterLabelHelper.getFilterDisplayValue('matchScore', 'unknown')).toBe('unknown');
    });

    it('should return stringified value for unhandled filter types', () => {
      expect(FilterLabelHelper.getFilterDisplayValue('author', 'Tolkien')).toBe('Tolkien');
      expect(FilterLabelHelper.getFilterDisplayValue('series', 42)).toBe('42');
      expect(FilterLabelHelper.getFilterDisplayValue('custom', null as any)).toBe('null');
    });
  });
});
