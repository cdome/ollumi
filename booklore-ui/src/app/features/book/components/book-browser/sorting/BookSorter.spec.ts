import {beforeEach, describe, expect, it, vi} from 'vitest';
import {BookSorter, SORT_OPTION_LABEL_KEYS} from './BookSorter';
import {SortDirection, SortOption} from '../../../model/sort.model';
import {CdkDragDrop} from '@angular/cdk/drag-drop';

function createMockTransloco(): { translate: ReturnType<typeof vi.fn> } {
  return {
    translate: vi.fn((key: string) => `translated:${key}`)
  };
}

describe('BookSorter', () => {
  let onSortChange: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    onSortChange = vi.fn();
  });

  describe('constructor', () => {
    it('should build sort options with default labels when no transloco is provided', () => {
      const sorter = new BookSorter(onSortChange);
      expect(sorter.sortOptions.length).toBeGreaterThan(0);
      expect(sorter.sortOptions[0]).toEqual({label: 'Title', field: 'title', direction: SortDirection.ASCENDING});
    });

    it('should use translated labels when transloco is provided', () => {
      const transloco = createMockTransloco();
      const sorter = new BookSorter(onSortChange, transloco as any);
      expect(sorter.sortOptions[0].label).toBe(`translated:${SORT_OPTION_LABEL_KEYS.title}`);
    });

    it('should default all options to ascending direction', () => {
      const sorter = new BookSorter(onSortChange);
      expect(sorter.sortOptions.every(o => o.direction === SortDirection.ASCENDING)).toBe(true);
    });
  });

  describe('selectedSort getter/setter', () => {
    it('should get the first selected criterion', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.selectedSortCriteria = [{field: 'title', direction: SortDirection.ASCENDING, label: 'Title'}];
      expect(sorter.selectedSort?.field).toBe('title');
    });

    it('should return undefined when no criteria selected', () => {
      const sorter = new BookSorter(onSortChange);
      expect(sorter.selectedSort).toBeUndefined();
    });

    it('should set single criterion via setter', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.selectedSort = {field: 'author', direction: SortDirection.DESCENDING, label: 'Author'};
      expect(sorter.selectedSortCriteria).toEqual([{field: 'author', direction: SortDirection.DESCENDING, label: 'Author'}]);
    });

    it('should clear criteria when setter receives undefined', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.selectedSort = {field: 'title', direction: SortDirection.ASCENDING, label: 'Title'};
      sorter.selectedSort = undefined;
      expect(sorter.selectedSortCriteria).toEqual([]);
    });
  });

  describe('setSortCriteria', () => {
    it('should replace selected criteria and update icons', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.setSortCriteria([{field: 'author', direction: SortDirection.DESCENDING, label: 'Author'}]);
      expect(sorter.selectedSortCriteria).toEqual([{field: 'author', direction: SortDirection.DESCENDING, label: 'Author'}]);
      expect(sorter.sortOptions.find(o => o.field === 'author')?.icon).toBe('pi pi-arrow-down');
    });

    it('should not call onSortChange when setting criteria', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.setSortCriteria([{field: 'title', direction: SortDirection.ASCENDING, label: 'Title'}]);
      expect(onSortChange).not.toHaveBeenCalled();
    });
  });

  describe('sortBooks', () => {
    it('should set field as primary ascending sort when not currently primary', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.sortBooks('author');
      expect(sorter.selectedSortCriteria).toEqual([{label: 'Author', field: 'author', direction: SortDirection.ASCENDING}]);
      expect(onSortChange).toHaveBeenCalledWith(sorter.selectedSortCriteria);
    });

    it('should toggle direction when field is already primary sort', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.sortBooks('title');
      sorter.sortBooks('title');
      expect(sorter.selectedSortCriteria[0].direction).toBe(SortDirection.DESCENDING);
      sorter.sortBooks('title');
      expect(sorter.selectedSortCriteria[0].direction).toBe(SortDirection.ASCENDING);
    });

    it('should update sort option icons after sort', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.sortBooks('title');
      expect(sorter.sortOptions.find(o => o.field === 'title')?.icon).toBe('pi pi-arrow-up');
    });

    it('should ignore unknown fields', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.sortBooks('unknownField');
      expect(sorter.selectedSortCriteria).toEqual([]);
      expect(onSortChange).not.toHaveBeenCalled();
    });

    it('should reset to single sort when a different field is clicked', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.sortBooks('title');
      sorter.sortBooks('author');
      expect(sorter.selectedSortCriteria).toEqual([{label: 'Author', field: 'author', direction: SortDirection.ASCENDING}]);
    });
  });

  describe('addSortCriterion', () => {
    it('should add a new criterion without changing existing ones', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.addSortCriterion('title');
      sorter.addSortCriterion('author');
      expect(sorter.selectedSortCriteria.map(c => c.field)).toEqual(['title', 'author']);
    });

    it('should not duplicate an existing criterion', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.addSortCriterion('title');
      sorter.addSortCriterion('title');
      expect(sorter.selectedSortCriteria.length).toBe(1);
    });

    it('should ignore unknown fields', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.addSortCriterion('unknownField');
      expect(sorter.selectedSortCriteria).toEqual([]);
    });

    it('should call onSortChange when adding', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.addSortCriterion('title');
      expect(onSortChange).toHaveBeenCalledTimes(1);
    });
  });

  describe('removeSortCriterion', () => {
    it('should remove criterion at index', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.addSortCriterion('title');
      sorter.addSortCriterion('author');
      sorter.removeSortCriterion(0);
      expect(sorter.selectedSortCriteria.map(c => c.field)).toEqual(['author']);
    });

    it('should ignore out-of-bounds indices', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.addSortCriterion('title');
      sorter.removeSortCriterion(-1);
      sorter.removeSortCriterion(5);
      expect(sorter.selectedSortCriteria.length).toBe(1);
    });

    it('should call onSortChange when removing', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.addSortCriterion('title');
      onSortChange.mockClear();
      sorter.removeSortCriterion(0);
      expect(onSortChange).toHaveBeenCalledWith([]);
    });
  });

  describe('toggleCriterionDirection', () => {
    it('should toggle direction at index', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.addSortCriterion('title');
      sorter.toggleCriterionDirection(0);
      expect(sorter.selectedSortCriteria[0].direction).toBe(SortDirection.DESCENDING);
      sorter.toggleCriterionDirection(0);
      expect(sorter.selectedSortCriteria[0].direction).toBe(SortDirection.ASCENDING);
    });

    it('should ignore out-of-bounds indices', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.addSortCriterion('title');
      sorter.toggleCriterionDirection(-1);
      sorter.toggleCriterionDirection(5);
      expect(sorter.selectedSortCriteria[0].direction).toBe(SortDirection.ASCENDING);
    });

    it('should call onSortChange when toggling', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.addSortCriterion('title');
      onSortChange.mockClear();
      sorter.toggleCriterionDirection(0);
      expect(onSortChange).toHaveBeenCalledTimes(1);
    });
  });

  describe('reorderCriteria', () => {
    it('should reorder criteria based on drag event', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.addSortCriterion('title');
      sorter.addSortCriterion('author');
      sorter.addSortCriterion('seriesName');
      const event = {previousIndex: 0, currentIndex: 2} as CdkDragDrop<SortOption[]>;
      sorter.reorderCriteria(event);
      expect(sorter.selectedSortCriteria.map(c => c.field)).toEqual(['author', 'seriesName', 'title']);
    });

    it('should call onSortChange when reordering', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.addSortCriterion('title');
      sorter.addSortCriterion('author');
      onSortChange.mockClear();
      const event = {previousIndex: 0, currentIndex: 1} as CdkDragDrop<SortOption[]>;
      sorter.reorderCriteria(event);
      expect(onSortChange).toHaveBeenCalledTimes(1);
    });
  });

  describe('getAvailableSortOptions', () => {
    it('should return options not currently selected', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.addSortCriterion('title');
      sorter.addSortCriterion('author');
      const available = sorter.getAvailableSortOptions();
      expect(available.some(o => o.field === 'title')).toBe(false);
      expect(available.some(o => o.field === 'author')).toBe(false);
      expect(available.some(o => o.field === 'seriesName')).toBe(true);
    });

    it('should return all options when none selected', () => {
      const sorter = new BookSorter(onSortChange);
      expect(sorter.getAvailableSortOptions().length).toBe(sorter.sortOptions.length);
    });
  });

  describe('updateSortOptions', () => {
    it('should set icon for primary field based on direction', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.sortBooks('title');
      expect(sorter.sortOptions.find(o => o.field === 'title')?.icon).toBe('pi pi-arrow-up');
      sorter.sortBooks('title');
      expect(sorter.sortOptions.find(o => o.field === 'title')?.icon).toBe('pi pi-arrow-down');
    });

    it('should clear icons when no criteria selected', () => {
      const sorter = new BookSorter(onSortChange);
      sorter.sortBooks('title');
      sorter.selectedSort = undefined;
      sorter.updateSortOptions();
      expect(sorter.sortOptions.every(o => !o.icon)).toBe(true);
    });
  });
});
