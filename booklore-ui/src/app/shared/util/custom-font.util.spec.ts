import {describe, expect, it} from 'vitest';

import {addCustomFontsToDropdown, FontDropdownItem, FontPreferenceItem} from './custom-font.util';
import {CustomFont} from '../model/custom-font.model';

function font(id: number, name: string): CustomFont {
  return {id, fontName: name} as CustomFont;
}

describe('addCustomFontsToDropdown', () => {
  it('does nothing when there are no fonts', () => {
    const target: FontDropdownItem[] = [{label: 'Default', value: null}];
    addCustomFontsToDropdown([], target, 'select');
    expect(target).toEqual([{label: 'Default', value: null}]);
  });

  describe('select format', () => {
    it('appends fonts as custom:<id> values', () => {
      const target: FontDropdownItem[] = [{label: 'Default', value: null}];
      addCustomFontsToDropdown([font(3, 'Inter')], target, 'select');
      expect(target).toContainEqual({label: 'Inter', value: 'custom:3'});
    });

    it('removes an existing separator before appending', () => {
      const target: FontDropdownItem[] = [
        {label: 'Default', value: null},
        {label: '──', value: 'separator'},
      ];
      addCustomFontsToDropdown([font(1, 'Inter')], target, 'select');
      expect(target.some(i => i.value === 'separator')).toBe(false);
      expect(target.at(-1)).toEqual({label: 'Inter', value: 'custom:1'});
    });
  });

  describe('preference format', () => {
    it('appends fonts with truncated display names and custom:<id> keys', () => {
      const target: FontPreferenceItem[] = [];
      addCustomFontsToDropdown([font(5, 'A Very Long Font Name Indeed')], target, 'preference');
      expect(target).toEqual([
        {name: 'A Very Long Font Name Indeed', displayName: 'A Very Long ', key: 'custom:5'},
      ]);
    });

    it('removes an existing separator (by key) before appending', () => {
      const target: FontPreferenceItem[] = [{name: 'sep', displayName: 'sep', key: 'separator'}];
      addCustomFontsToDropdown([font(2, 'Serif')], target, 'preference');
      expect(target.some(i => i.key === 'separator')).toBe(false);
      expect(target).toEqual([{name: 'Serif', displayName: 'Serif', key: 'custom:2'}]);
    });
  });
});
