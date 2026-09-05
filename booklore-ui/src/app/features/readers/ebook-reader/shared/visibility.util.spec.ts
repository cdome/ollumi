import {beforeEach, describe, expect, it, vi} from 'vitest';
import {ReaderHeaderFooterVisibilityManager} from './visibility.util';

describe('ReaderHeaderFooterVisibilityManager', () => {
  const WINDOW_HEIGHT = 800;

  describe('constructor and basic state', () => {
    it('should initialise with both header and footer hidden', () => {
      const manager = new ReaderHeaderFooterVisibilityManager(WINDOW_HEIGHT);
      expect(manager.getVisibilityState()).toEqual({headerVisible: false, footerVisible: false});
    });
  });

  describe('handleMouseMove', () => {
    it('should show the header when the cursor is in the header trigger zone', () => {
      const manager = new ReaderHeaderFooterVisibilityManager(WINDOW_HEIGHT);
      manager.handleMouseMove(10);
      expect(manager.getVisibilityState()).toEqual({headerVisible: true, footerVisible: false});
    });

    it('should show the footer when the cursor is in the footer trigger zone', () => {
      const manager = new ReaderHeaderFooterVisibilityManager(WINDOW_HEIGHT);
      manager.handleMouseMove(WINDOW_HEIGHT - 10);
      expect(manager.getVisibilityState()).toEqual({headerVisible: false, footerVisible: true});
    });

    it('should hide the header when the cursor leaves the header area', () => {
      const manager = new ReaderHeaderFooterVisibilityManager(WINDOW_HEIGHT);
      manager.handleMouseMove(10);
      manager.handleMouseMove(100);
      expect(manager.getVisibilityState()).toEqual({headerVisible: false, footerVisible: false});
    });

    it('should hide the footer when the cursor leaves the footer area', () => {
      const manager = new ReaderHeaderFooterVisibilityManager(WINDOW_HEIGHT);
      manager.handleMouseMove(WINDOW_HEIGHT - 10);
      manager.handleMouseMove(400);
      expect(manager.getVisibilityState()).toEqual({headerVisible: false, footerVisible: false});
    });

    it('should keep the header visible while the cursor stays within the header height after being shown', () => {
      const manager = new ReaderHeaderFooterVisibilityManager(WINDOW_HEIGHT);
      manager.handleMouseMove(10);
      manager.handleMouseMove(20);
      expect(manager.getVisibilityState().headerVisible).toBe(true);
    });

    it('should keep the footer visible while the cursor stays within the footer height after being shown', () => {
      const manager = new ReaderHeaderFooterVisibilityManager(WINDOW_HEIGHT);
      manager.handleMouseMove(WINDOW_HEIGHT - 10);
      manager.handleMouseMove(WINDOW_HEIGHT - 20);
      expect(manager.getVisibilityState().footerVisible).toBe(true);
    });

    it('should show both header and footer when pinned and the cursor is in the middle', () => {
      const manager = new ReaderHeaderFooterVisibilityManager(WINDOW_HEIGHT);
      manager.togglePinned();
      manager.handleMouseMove(400);
      expect(manager.getVisibilityState()).toEqual({headerVisible: true, footerVisible: true});
    });
  });

  describe('handleMouseLeave', () => {
    it('should hide both header and footer when not pinned', () => {
      const manager = new ReaderHeaderFooterVisibilityManager(WINDOW_HEIGHT);
      manager.handleMouseMove(10);
      manager.handleMouseLeave();
      expect(manager.getVisibilityState()).toEqual({headerVisible: false, footerVisible: false});
    });

    it('should keep visibility unchanged when pinned', () => {
      const manager = new ReaderHeaderFooterVisibilityManager(WINDOW_HEIGHT);
      manager.togglePinned();
      manager.handleMouseLeave();
      expect(manager.getVisibilityState()).toEqual({headerVisible: true, footerVisible: true});
    });
  });

  describe('handleHeaderZoneEnter and handleFooterZoneEnter', () => {
    it('should show the header when entering the header zone', () => {
      const manager = new ReaderHeaderFooterVisibilityManager(WINDOW_HEIGHT);
      manager.handleHeaderZoneEnter();
      expect(manager.getVisibilityState()).toEqual({headerVisible: true, footerVisible: false});
    });

    it('should show the footer when entering the footer zone', () => {
      const manager = new ReaderHeaderFooterVisibilityManager(WINDOW_HEIGHT);
      manager.handleFooterZoneEnter();
      expect(manager.getVisibilityState()).toEqual({headerVisible: false, footerVisible: true});
    });

    it('should not change state when pinned', () => {
      const manager = new ReaderHeaderFooterVisibilityManager(WINDOW_HEIGHT);
      manager.togglePinned();
      manager.handleMouseMove(400);
      manager.handleHeaderZoneEnter();
      manager.handleFooterZoneEnter();
      expect(manager.getVisibilityState()).toEqual({headerVisible: true, footerVisible: true});
    });
  });

  describe('togglePinned', () => {
    it('should toggle pinned state and update visibility', () => {
      const manager = new ReaderHeaderFooterVisibilityManager(WINDOW_HEIGHT);
      manager.togglePinned();
      expect(manager.getVisibilityState()).toEqual({headerVisible: true, footerVisible: true});
      manager.togglePinned();
      expect(manager.getVisibilityState()).toEqual({headerVisible: false, footerVisible: false});
    });

    it('should hide elements when unpinning even if cursor is in trigger zones', () => {
      const manager = new ReaderHeaderFooterVisibilityManager(WINDOW_HEIGHT);
      manager.handleMouseMove(10);
      manager.togglePinned();
      manager.togglePinned();
      expect(manager.getVisibilityState()).toEqual({headerVisible: true, footerVisible: false});
    });
  });

  describe('updateWindowHeight', () => {
    it('should update the window height used for footer calculations', () => {
      const manager = new ReaderHeaderFooterVisibilityManager(WINDOW_HEIGHT);
      manager.updateWindowHeight(400);
      manager.handleMouseMove(390);
      expect(manager.getVisibilityState().footerVisible).toBe(true);
    });
  });

  describe('onStateChange callback', () => {
    it('should invoke the callback when visibility changes', () => {
      const manager = new ReaderHeaderFooterVisibilityManager(WINDOW_HEIGHT);
      const callback = vi.fn();
      manager.onStateChange(callback);
      manager.handleMouseMove(10);
      expect(callback).toHaveBeenCalledWith({headerVisible: true, footerVisible: false});
    });

    it('should not invoke the callback before any change', () => {
      const manager = new ReaderHeaderFooterVisibilityManager(WINDOW_HEIGHT);
      const callback = vi.fn();
      manager.onStateChange(callback);
      expect(callback).not.toHaveBeenCalled();
    });

    it('should invoke the callback on mouse leave', () => {
      const manager = new ReaderHeaderFooterVisibilityManager(WINDOW_HEIGHT);
      const callback = vi.fn();
      manager.onStateChange(callback);
      manager.handleMouseMove(10);
      manager.handleMouseLeave();
      expect(callback).toHaveBeenLastCalledWith({headerVisible: false, footerVisible: false});
    });

    it('should invoke the callback when pinning', () => {
      const manager = new ReaderHeaderFooterVisibilityManager(WINDOW_HEIGHT);
      const callback = vi.fn();
      manager.onStateChange(callback);
      manager.togglePinned();
      expect(callback).toHaveBeenLastCalledWith({headerVisible: true, footerVisible: true});
    });
  });
});
