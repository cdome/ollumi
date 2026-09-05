import {describe, expect, it} from 'vitest';
import {applyModifier, replacePlaceholders} from './pattern-resolver';

describe('applyModifier', () => {
  describe('first modifier', () => {
    it('should return the first comma-separated value trimmed', () => {
      expect(applyModifier('Isaac Asimov, Arthur Clarke', 'first', 'authors')).toBe('Isaac Asimov');
    });

    it('should return the whole value when no comma is present', () => {
      expect(applyModifier('Single Author', 'first', 'authors')).toBe('Single Author');
    });

    it('should return empty value unchanged', () => {
      expect(applyModifier('', 'first', 'authors')).toBe('');
    });
  });

  describe('sort modifier', () => {
    it('should convert "First Last" to "Last, First"', () => {
      expect(applyModifier('Isaac Asimov', 'sort', 'authors')).toBe('Asimov, Isaac');
    });

    it('should keep a single word unchanged', () => {
      expect(applyModifier('Asimov', 'sort', 'authors')).toBe('Asimov');
    });

    it('should use the first author when multiple are provided', () => {
      expect(applyModifier('Isaac Asimov, Arthur Clarke', 'sort', 'authors')).toBe('Asimov, Isaac');
    });

    it('should return empty value unchanged', () => {
      expect(applyModifier('', 'sort', 'authors')).toBe('');
    });
  });

  describe('initial modifier', () => {
    it('should return the uppercased first character of a generic field', () => {
      expect(applyModifier('fantasy', 'initial', 'genre')).toBe('F');
    });

    it('should return the initial of the last name when field is authors', () => {
      expect(applyModifier('Isaac Asimov, Arthur Clarke', 'initial', 'authors')).toBe('A');
    });

    it('should return the initial of the only author name when there is no space', () => {
      expect(applyModifier('Asimov', 'initial', 'authors')).toBe('A');
    });

    it('should return empty string for empty value', () => {
      expect(applyModifier('', 'initial', 'authors')).toBe('');
    });
  });

  describe('upper modifier', () => {
    it('should uppercase the value', () => {
      expect(applyModifier('Hello World', 'upper', 'title')).toBe('HELLO WORLD');
    });

    it('should return empty value unchanged', () => {
      expect(applyModifier('', 'upper', 'title')).toBe('');
    });
  });

  describe('lower modifier', () => {
    it('should lowercase the value', () => {
      expect(applyModifier('Hello World', 'lower', 'title')).toBe('hello world');
    });

    it('should return empty value unchanged', () => {
      expect(applyModifier('', 'lower', 'title')).toBe('');
    });
  });

  describe('unknown modifier', () => {
    it('should return the original value for an unrecognised modifier', () => {
      expect(applyModifier('Hello', 'unknown', 'title')).toBe('Hello');
    });
  });
});

describe('replacePlaceholders', () => {
  it('should replace simple placeholders with values', () => {
    expect(replacePlaceholders('{title} by {author}', {title: 'Dune', author: 'Herbert'})).toBe('Dune by Herbert');
  });

  it('should leave placeholders without values empty', () => {
    expect(replacePlaceholders('{title} - {missing}', {title: 'Dune'})).toBe('Dune -');
  });

  it('should apply modifiers inside placeholders', () => {
    expect(replacePlaceholders('{title:upper}', {title: 'Dune'})).toBe('DUNE');
  });

  it('should use the primary block when all placeholders are present', () => {
    expect(replacePlaceholders('<{title} ({year})|{title}>', {title: 'Dune', year: '1965'})).toBe('Dune (1965)');
  });

  it('should fall back when a primary placeholder is missing', () => {
    expect(replacePlaceholders('<{title} ({year})|{title}>', {title: 'Dune'})).toBe('Dune');
  });

  it('should render an empty string when fallback is missing and primary placeholders are absent', () => {
    expect(replacePlaceholders('<({year})>', {})).toBe('');
  });

  it('should resolve modifiers in fallback content', () => {
    expect(replacePlaceholders('<{title:upper} ({year})|{title:lower}>', {title: 'Dune'})).toBe('dune');
  });

  it('should trim surrounding whitespace from the final result', () => {
    expect(replacePlaceholders('  {title}  ', {title: 'Dune'})).toBe('Dune');
  });

  it('should handle multiple optional blocks independently', () => {
    const pattern = '<{series} #{seriesNumber}|{title}> - {author}';
    expect(replacePlaceholders(pattern, {title: 'Dune', author: 'Herbert'})).toBe('Dune - Herbert');
    expect(replacePlaceholders(pattern, {series: 'Dune', seriesNumber: '2', author: 'Herbert'})).toBe('Dune #2 - Herbert');
  });

  it('should treat whitespace-only values as absent in optional blocks', () => {
    expect(replacePlaceholders('<{title}|fallback>', {title: '   '})).toBe('fallback');
  });

  it('should return an empty string for an empty pattern', () => {
    expect(replacePlaceholders('', {})).toBe('');
  });
});
