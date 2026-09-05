import {describe, expect, it} from 'vitest';
import {ReadStatusHelper} from './read-status.helper';
import {ReadStatus} from '../model/book.model';

describe('ReadStatusHelper', () => {
  const helper = new ReadStatusHelper();

  describe('getReadStatusIcon', () => {
    it('should return pi-book for undefined status', () => {
      expect(helper.getReadStatusIcon(undefined)).toBe('pi pi-book');
    });

    it('should return the correct icon for each status', () => {
      expect(helper.getReadStatusIcon(ReadStatus.READ)).toBe('pi pi-check');
      expect(helper.getReadStatusIcon(ReadStatus.READING)).toBe('pi pi-play');
      expect(helper.getReadStatusIcon(ReadStatus.RE_READING)).toBe('pi pi-refresh');
      expect(helper.getReadStatusIcon(ReadStatus.PARTIALLY_READ)).toBe('pi pi-clock');
      expect(helper.getReadStatusIcon(ReadStatus.PAUSED)).toBe('pi pi-pause');
      expect(helper.getReadStatusIcon(ReadStatus.ABANDONED)).toBe('pi pi-times');
      expect(helper.getReadStatusIcon(ReadStatus.WONT_READ)).toBe('pi pi-ban');
      expect(helper.getReadStatusIcon(ReadStatus.UNREAD)).toBe('pi pi-book');
      expect(helper.getReadStatusIcon(ReadStatus.UNSET)).toBe('pi pi-book');
    });
  });

  describe('getReadStatusClass', () => {
    it('should return status-unset for undefined status', () => {
      expect(helper.getReadStatusClass(undefined)).toBe('status-unset');
    });

    it('should return the correct class for each status', () => {
      expect(helper.getReadStatusClass(ReadStatus.READ)).toBe('status-read');
      expect(helper.getReadStatusClass(ReadStatus.READING)).toBe('status-reading');
      expect(helper.getReadStatusClass(ReadStatus.RE_READING)).toBe('status-re-reading');
      expect(helper.getReadStatusClass(ReadStatus.PARTIALLY_READ)).toBe('status-partially-read');
      expect(helper.getReadStatusClass(ReadStatus.PAUSED)).toBe('status-paused');
      expect(helper.getReadStatusClass(ReadStatus.ABANDONED)).toBe('status-abandoned');
      expect(helper.getReadStatusClass(ReadStatus.WONT_READ)).toBe('status-wont-read');
      expect(helper.getReadStatusClass(ReadStatus.UNREAD)).toBe('status-unset');
      expect(helper.getReadStatusClass(ReadStatus.UNSET)).toBe('status-unset');
    });
  });

  describe('getReadStatusTooltip', () => {
    it('should return Unset for undefined status', () => {
      expect(helper.getReadStatusTooltip(undefined)).toBe('Unset');
    });

    it('should return the correct tooltip for each status', () => {
      expect(helper.getReadStatusTooltip(ReadStatus.READ)).toBe('Read');
      expect(helper.getReadStatusTooltip(ReadStatus.READING)).toBe('Currently Reading');
      expect(helper.getReadStatusTooltip(ReadStatus.RE_READING)).toBe('Re-reading');
      expect(helper.getReadStatusTooltip(ReadStatus.PARTIALLY_READ)).toBe('Partially Read');
      expect(helper.getReadStatusTooltip(ReadStatus.PAUSED)).toBe('Paused');
      expect(helper.getReadStatusTooltip(ReadStatus.ABANDONED)).toBe('Abandoned');
      expect(helper.getReadStatusTooltip(ReadStatus.WONT_READ)).toBe('Won\'t Read');
      expect(helper.getReadStatusTooltip(ReadStatus.UNREAD)).toBe('Unread');
      expect(helper.getReadStatusTooltip(ReadStatus.UNSET)).toBe('Unset');
    });
  });

  describe('shouldShowStatusIcon', () => {
    it('should always return true', () => {
      expect(helper.shouldShowStatusIcon(ReadStatus.READ)).toBe(true);
      expect(helper.shouldShowStatusIcon(undefined)).toBe(true);
    });
  });
});
