import {describe, expect, it} from 'vitest';
import {IconCategoriesHelper} from './icon-categories.helper';

describe('IconCategoriesHelper', () => {
  describe('CATEGORIES', () => {
    it('should contain a known set of icon names', () => {
      expect(IconCategoriesHelper.CATEGORIES).toContain('book');
      expect(IconCategoriesHelper.CATEGORIES).toContain('star');
      expect(IconCategoriesHelper.CATEGORIES).toContain('home');
    });

    it('should not be empty', () => {
      expect(IconCategoriesHelper.CATEGORIES.length).toBeGreaterThan(0);
    });

    it('should not contain duplicate entries', () => {
      const unique = new Set(IconCategoriesHelper.CATEGORIES);
      expect(unique.size).toBe(IconCategoriesHelper.CATEGORIES.length);
    });

    it('should store icon names without the pi pi- prefix', () => {
      expect(IconCategoriesHelper.CATEGORIES.every(name => !name.startsWith('pi '))).toBe(true);
    });
  });

  describe('createIconList', () => {
    it('should prefix each category with pi pi-', () => {
      const list = IconCategoriesHelper.createIconList();
      expect(list).toContain('pi pi-book');
      expect(list).toContain('pi pi-star');
      expect(list.every(icon => icon.startsWith('pi pi-'))).toBe(true);
    });

    it('should produce a list with the same length as CATEGORIES', () => {
      expect(IconCategoriesHelper.createIconList().length).toBe(IconCategoriesHelper.CATEGORIES.length);
    });

    it('should return a new array instance on each call', () => {
      const first = IconCategoriesHelper.createIconList();
      const second = IconCategoriesHelper.createIconList();
      expect(first).not.toBe(second);
      expect(first).toEqual(second);
    });
  });
});
