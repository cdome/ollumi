import {describe, expect, it} from 'vitest';
import {PageDecorator, PageInfo, ThemeInfo} from './header-footer.util';

const createRenderer = (headCount: number, footCount: number) => {
  const heads: HTMLElement[] = [];
  const feet: HTMLElement[] = [];
  for (let i = 0; i < headCount; i++) {
    heads.push(document.createElement('div'));
  }
  for (let i = 0; i < footCount; i++) {
    feet.push(document.createElement('div'));
  }
  return {heads, feet};
};

const pageInfo = (overrides: Partial<PageInfo> = {}): PageInfo => ({
  percentCompleted: 42,
  sectionTimeText: '3m 12s',
  ...overrides
});

const theme = (overrides: Partial<ThemeInfo> = {}): ThemeInfo => ({
  fg: '#ffffff',
  bg: '#000000',
  ...overrides
});

describe('PageDecorator', () => {
  describe('updateHeadersAndFooters', () => {
    it('should do nothing when renderer is null', () => {
      expect(() => PageDecorator.updateHeadersAndFooters(null, 'Chapter 1', pageInfo())).not.toThrow();
    });

    it('should do nothing when renderer is undefined', () => {
      expect(() => PageDecorator.updateHeadersAndFooters(undefined, 'Chapter 1', pageInfo())).not.toThrow();
    });

    it('should update headers and footers for a single-column renderer', () => {
      const renderer = createRenderer(1, 1);
      PageDecorator.updateHeadersAndFooters(renderer, 'Chapter 1', pageInfo(), theme());

      expect(renderer.heads[0].style.visibility).toBe('visible');
      expect(renderer.heads[0].children.length).toBe(1);
      expect(renderer.heads[0].children[0].children.length).toBe(2);

      expect(renderer.feet[0].children.length).toBe(1);
      const footerContent = renderer.feet[0].children[0];
      expect(footerContent.children.length).toBe(2);
      expect(footerContent.children[0].textContent).toBe('Time remaining in section: 3m 12s');
      expect(footerContent.children[1].textContent).toBe('42%');
    });

    it('should update headers and footers for a two-column renderer', () => {
      const renderer = createRenderer(2, 2);
      PageDecorator.updateHeadersAndFooters(renderer, 'Chapter 1', pageInfo(), theme());

      expect(renderer.heads[0].children[0].children.length).toBe(1);
      expect(renderer.heads[1].children[0].children.length).toBe(0);

      expect(renderer.feet[0].children[0].children.length).toBe(2);
      expect(renderer.feet[1].children[0].children.length).toBe(2);
      expect(renderer.feet[1].children[0].children[1].textContent).toBe('42%');
    });

    it('should not throw when a head element is null', () => {
      const renderer = createRenderer(2, 0);
      renderer.heads[1] = null as unknown as HTMLElement;
      expect(() => PageDecorator.updateHeadersAndFooters(renderer, 'Chapter 1')).not.toThrow();
      expect(renderer.heads[0].children.length).toBe(1);
    });

    it('should not throw when a foot element is null', () => {
      const renderer = createRenderer(0, 2);
      renderer.feet[1] = null as unknown as HTMLElement;
      expect(() => PageDecorator.updateHeadersAndFooters(renderer, 'Chapter 1', pageInfo())).not.toThrow();
      expect(renderer.feet[0].children.length).toBe(1);
    });

    it('should apply theme color when provided', () => {
      const renderer = createRenderer(1, 1);
      PageDecorator.updateHeadersAndFooters(renderer, 'Chapter 1', pageInfo(), theme({fg: '#ff0000'}));
      const headerStyle = renderer.heads[0].children[0].getAttribute('style');
      expect(headerStyle).toContain('color: rgb(255, 0, 0)');
    });

    it('should use the provided time remaining label', () => {
      const renderer = createRenderer(0, 1);
      PageDecorator.updateHeadersAndFooters(renderer, 'Chapter 1', pageInfo(), undefined, 'Time left: 1m');
      expect(renderer.feet[0].children[0].children[0].textContent).toBe('Time left: 1m');
    });

    it('should fall back to default time text when sectionTimeText is undefined', () => {
      const renderer = createRenderer(0, 1);
      PageDecorator.updateHeadersAndFooters(renderer, 'Chapter 1', pageInfo({sectionTimeText: undefined as unknown as string}), undefined);
      expect(renderer.feet[0].children[0].children[0].textContent).toBe('Time remaining in section: 0s');
    });

    it('should not render footers when pageInfo is undefined', () => {
      const renderer = createRenderer(1, 1);
      PageDecorator.updateHeadersAndFooters(renderer, 'Chapter 1', undefined);
      expect(renderer.heads[0].children.length).toBe(1);
      expect(renderer.feet[0].children.length).toBe(0);
    });

    it('should treat an empty chapter name as empty string', () => {
      const renderer = createRenderer(1, 0);
      PageDecorator.updateHeadersAndFooters(renderer, '');
      expect(renderer.heads[0].children[0].children[0].textContent).toBe('');
    });

    it('should not render headers when heads array is missing', () => {
      const renderer = {feet: createRenderer(0, 1).feet};
      expect(() => PageDecorator.updateHeadersAndFooters(renderer, 'Chapter 1', pageInfo())).not.toThrow();
    });

    it('should not render footers when feet array is missing', () => {
      const renderer = {heads: createRenderer(1, 0).heads};
      expect(() => PageDecorator.updateHeadersAndFooters(renderer, 'Chapter 1', pageInfo())).not.toThrow();
    });

    it('should handle more than two columns', () => {
      const renderer = createRenderer(3, 3);
      PageDecorator.updateHeadersAndFooters(renderer, 'Chapter 1', pageInfo());
      expect(renderer.heads[0].children[0].children.length).toBe(1);
      expect(renderer.heads[1].children[0].children.length).toBe(0);
      expect(renderer.heads[2].children[0].children.length).toBe(0);

      expect(renderer.feet[0].children[0].children.length).toBe(2);
      expect(renderer.feet[1].children[0].children.length).toBe(0);
      expect(renderer.feet[2].children[0].children.length).toBe(2);
    });
  });
});
