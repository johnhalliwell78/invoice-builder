import { describe, expect, it } from 'vitest';
import { findInvalidEmail, parseEmailList } from './email';

describe('parseEmailList', () => {
  it('splits on commas and semicolons and trims whitespace', () => {
    expect(parseEmailList(' a@x.io , b@x.io ;c@x.io ')).toEqual(['a@x.io', 'b@x.io', 'c@x.io']);
  });

  it('drops blanks and dedupes case-insensitively keeping first casing', () => {
    expect(parseEmailList('A@x.io,, a@x.io ,')).toEqual(['A@x.io']);
  });

  it('returns empty array for empty input', () => {
    expect(parseEmailList('')).toEqual([]);
  });
});

describe('findInvalidEmail', () => {
  it('returns null when all addresses are valid', () => {
    expect(findInvalidEmail(['a@x.io', 'b@y.co'])).toBeNull();
  });

  it('returns the first invalid address', () => {
    expect(findInvalidEmail(['a@x.io', 'not-an-email', 'also bad'])).toBe('not-an-email');
  });

  it('returns null for empty list', () => {
    expect(findInvalidEmail([])).toBeNull();
  });
});
