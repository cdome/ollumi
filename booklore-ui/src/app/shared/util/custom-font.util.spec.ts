import {describe, expect, it} from 'vitest';
import {addCustomFontsToDropdown, FontDropdownItem, FontPreferenceItem} from './custom-font.util';
import {CustomFont, FontFormat} from '../model/custom-font.model';

const createFont = (id: number, fontName: string): CustomFont => ({
  id,
  fontName,
  originalFileName: `${fontName}.ttf`,
  format: FontFormat.TTF,
  fileSize: 1024,
  uploadedAt: '2024-01-01T00:00:00Z'
});

describe('addCustomFontsToDropdown', () => {
  describe('select format', () => {
    it('should add custom fonts to a select dropdown', () => {
      const fonts = [createFont(1, 'Comic Sans'), createFont(2, 'Papyrus')];
      const target: FontDropdownItem[] = [
        {label: 'Default', value: null},
        {label: '──────────', value: 'separator', disabled: true}
      ];

      addCustomFontsToDropdown(fonts, target, 'select');

      expect(target).toEqual([
        {label: 'Default', value: null},
        {label: 'Comic Sans', value: 'custom:1'},
        {label: 'Papyrus', value: 'custom:2'}
      ]);
    });

    it('should not add anything when fonts array is empty', () => {
      const target: FontDropdownItem[] = [{label: 'Default', value: null}];
      addCustomFontsToDropdown([], target, 'select');
      expect(target).toEqual([{label: 'Default', value: null}]);
    });

    it('should remove only the existing separator before adding fonts', () => {
      const fonts = [createFont(3, 'Arial')];
      const target: FontDropdownItem[] = [
        {label: 'Default', value: null},
        {label: 'Separator', value: 'separator'},
        {label: 'Other', value: 'other'}
      ];

      addCustomFontsToDropdown(fonts, target, 'select');

      expect(target).toEqual([
        {label: 'Default', value: null},
        {label: 'Other', value: 'other'},
        {label: 'Arial', value: 'custom:3'}
      ]);
    });

    it('should not remove items when no separator exists', () => {
      const fonts = [createFont(4, 'Verdana')];
      const target: FontDropdownItem[] = [{label: 'Default', value: null}];

      addCustomFontsToDropdown(fonts, target, 'select');

      expect(target).toEqual([
        {label: 'Default', value: null},
        {label: 'Verdana', value: 'custom:4'}
      ]);
    });
  });

  describe('preference format', () => {
    it('should add custom fonts to a preference list', () => {
      const fonts = [createFont(1, 'Comic Sans'), createFont(2, 'VeryLongFontNameIndeed')];
      const target: FontPreferenceItem[] = [
        {name: 'default', displayName: 'Default', key: null},
        {name: 'separator', displayName: '──────────', key: 'separator'}
      ];

      addCustomFontsToDropdown(fonts, target, 'preference');

      expect(target).toEqual([
        {name: 'default', displayName: 'Default', key: null},
        {name: 'Comic Sans', displayName: 'Comic Sans', key: 'custom:1'},
        {name: 'VeryLongFontNameIndeed', displayName: 'VeryLongFont', key: 'custom:2'}
      ]);
    });

    it('should truncate display names longer than 12 characters', () => {
      const fonts = [createFont(5, 'ABCDEFGHIJKLMNOP')];
      const target: FontPreferenceItem[] = [];

      addCustomFontsToDropdown(fonts, target, 'preference');

      expect(target[0].displayName).toBe('ABCDEFGHIJKL');
      expect(target[0].name).toBe('ABCDEFGHIJKLMNOP');
    });

    it('should not add anything when fonts array is empty', () => {
      const target: FontPreferenceItem[] = [{name: 'default', displayName: 'Default', key: null}];
      addCustomFontsToDropdown([], target, 'preference');
      expect(target).toEqual([{name: 'default', displayName: 'Default', key: null}]);
    });

    it('should remove only the existing separator before adding fonts', () => {
      const fonts = [createFont(6, 'Georgia')];
      const target: FontPreferenceItem[] = [
        {name: 'default', displayName: 'Default', key: null},
        {name: 'separator', displayName: '──────────', key: 'separator'},
        {name: 'other', displayName: 'Other', key: 'other'}
      ];

      addCustomFontsToDropdown(fonts, target, 'preference');

      expect(target).toEqual([
        {name: 'default', displayName: 'Default', key: null},
        {name: 'other', displayName: 'Other', key: 'other'},
        {name: 'Georgia', displayName: 'Georgia', key: 'custom:6'}
      ]);
    });
  });
});
