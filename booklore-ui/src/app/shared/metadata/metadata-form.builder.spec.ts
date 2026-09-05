import {describe, expect, it} from 'vitest';
import {ALL_COMIC_METADATA_FIELDS, ALL_METADATA_FIELDS, AUDIOBOOK_METADATA_FIELDS, MetadataFieldConfig} from './metadata-field.config';
import {MetadataFormBuilder} from './metadata-form.builder';

describe('MetadataFormBuilder', () => {
  const builder = new MetadataFormBuilder();

  describe('buildForm', () => {
    it('should create a FormGroup with all standard field controls', () => {
      const form = builder.buildForm();

      for (const field of ALL_METADATA_FIELDS) {
        expect(form.contains(field.controlName)).toBe(true);
      }
    });

    it('should create locked controls for every field by default', () => {
      const form = builder.buildForm();

      for (const field of ALL_METADATA_FIELDS) {
        expect(form.contains(field.lockedKey)).toBe(true);
      }
      for (const field of AUDIOBOOK_METADATA_FIELDS) {
        expect(form.contains(field.lockedKey)).toBe(true);
      }
      for (const field of ALL_COMIC_METADATA_FIELDS) {
        expect(form.contains(field.lockedKey)).toBe(true);
      }
      expect(form.contains('coverLocked')).toBe(true);
      expect(form.contains('audiobookCoverLocked')).toBe(true);
    });

    it('should not create locked controls when includeLockedControls is false', () => {
      const form = builder.buildForm(false);

      for (const field of ALL_METADATA_FIELDS) {
        expect(form.contains(field.lockedKey)).toBe(false);
      }
      for (const field of AUDIOBOOK_METADATA_FIELDS) {
        expect(form.contains(field.lockedKey)).toBe(false);
      }
      for (const field of ALL_COMIC_METADATA_FIELDS) {
        expect(form.contains(field.lockedKey)).toBe(false);
      }
      expect(form.contains('coverLocked')).toBe(false);
      expect(form.contains('audiobookCoverLocked')).toBe(false);
    });

    it('should create audiobook-specific controls', () => {
      const form = builder.buildForm();

      for (const field of AUDIOBOOK_METADATA_FIELDS) {
        expect(form.contains(field.controlName)).toBe(true);
      }
    });

    it('should create comic-specific controls', () => {
      const form = builder.buildForm();

      for (const field of ALL_COMIC_METADATA_FIELDS) {
        expect(form.contains(field.controlName)).toBe(true);
      }
    });

    it('should create thumbnail URL controls', () => {
      const form = builder.buildForm();
      expect(form.contains('thumbnailUrl')).toBe(true);
      expect(form.contains('audiobookThumbnailUrl')).toBe(true);
    });

    it('should use default values based on field type', () => {
      const form = builder.buildForm(false);

      expect(form.get('authors')?.value).toEqual([]);
      expect(form.get('categories')?.value).toEqual([]);
      expect(form.get('title')?.value).toBe('');
      expect(form.get('description')?.value).toBe('');
      expect(form.get('seriesNumber')?.value).toBeNull();
      expect(form.get('abridged')?.value).toBeNull();
      expect(form.get('thumbnailUrl')?.value).toBe('');
    });

    it('should support a custom field set', () => {
      const customFields: MetadataFieldConfig[] = [
        {label: 'Custom Title', controlName: 'customTitle', lockedKey: 'customTitleLocked', fetchedKey: 'customTitle', type: 'string'},
        {label: 'Custom Tags', controlName: 'customTags', lockedKey: 'customTagsLocked', fetchedKey: 'customTags', type: 'array'}
      ];

      const form = builder.buildForm(true, customFields);

      expect(form.contains('customTitle')).toBe(true);
      expect(form.contains('customTags')).toBe(true);
      expect(form.contains('customTitleLocked')).toBe(true);
      expect(form.contains('customTagsLocked')).toBe(true);

      expect(form.contains('title')).toBe(false);
    });

    it('should still add audiobook, comic, and thumbnail controls with custom fields', () => {
      const customFields: MetadataFieldConfig[] = [
        {label: 'Custom', controlName: 'custom', lockedKey: 'customLocked', fetchedKey: 'custom', type: 'string'}
      ];

      const form = builder.buildForm(true, customFields);

      expect(form.contains('narrator')).toBe(true);
      expect(form.contains('comicIssueNumber')).toBe(true);
      expect(form.contains('thumbnailUrl')).toBe(true);
    });
  });

  describe('applyLockStates', () => {
    it('should disable locked controls', () => {
      const form = builder.buildForm();
      const lockedFields = {titleLocked: true, authorsLocked: true};

      builder.applyLockStates(form, lockedFields);

      expect(form.get('title')?.disabled).toBe(true);
      expect(form.get('authors')?.disabled).toBe(true);
      expect(form.get('publisher')?.disabled).toBe(false);
    });

    it('should enable previously disabled controls when lock is removed', () => {
      const form = builder.buildForm();
      builder.applyLockStates(form, {titleLocked: true});
      builder.applyLockStates(form, {});

      expect(form.get('title')?.disabled).toBe(false);
    });

    it('should not affect controls when no fields are locked', () => {
      const form = builder.buildForm();
      builder.applyLockStates(form, {});

      for (const key of Object.keys(form.controls)) {
        expect(form.get(key)?.disabled).toBe(false);
      }
    });

    it('should respect custom field sets', () => {
      const customFields: MetadataFieldConfig[] = [
        {label: 'Custom', controlName: 'custom', lockedKey: 'customLocked', fetchedKey: 'custom', type: 'string'}
      ];
      const form = builder.buildForm(true, customFields);

      builder.applyLockStates(form, {customLocked: true}, customFields);

      expect(form.get('custom')?.disabled).toBe(true);
    });
  });

  describe('setAllFieldsLocked', () => {
    it('should lock every field when locked is true', () => {
      const form = builder.buildForm();
      builder.setAllFieldsLocked(form, true);

      const lockedKeyToField: Record<string, string> = {
        coverLocked: 'thumbnailUrl',
        audiobookCoverLocked: 'audiobookThumbnailUrl'
      };

      for (const key of Object.keys(form.controls)) {
        if (key.endsWith('Locked')) {
          expect(form.get(key)?.value).toBe(true);
          const fieldName = lockedKeyToField[key] ?? key.replace('Locked', '');
          expect(form.get(fieldName)?.disabled).toBe(true);
        }
      }
    });

    it('should unlock every field when locked is false', () => {
      const form = builder.buildForm();
      builder.setAllFieldsLocked(form, true);
      builder.setAllFieldsLocked(form, false);

      const lockedKeyToField: Record<string, string> = {
        coverLocked: 'thumbnailUrl',
        audiobookCoverLocked: 'audiobookThumbnailUrl'
      };

      for (const key of Object.keys(form.controls)) {
        if (key.endsWith('Locked')) {
          expect(form.get(key)?.value).toBe(false);
          const fieldName = lockedKeyToField[key] ?? key.replace('Locked', '');
          expect(form.get(fieldName)?.disabled).toBe(false);
        }
      }
    });
  });
});
