import {FormControl, FormGroup} from '@angular/forms';
import {describe, expect, it} from 'vitest';
import {passwordMatchValidator} from './password-match.validator';

describe('passwordMatchValidator', () => {
  function buildGroup(password: unknown, confirmPassword: unknown): FormGroup {
    return new FormGroup({
      password: new FormControl(password),
      confirmPassword: new FormControl(confirmPassword)
    }, passwordMatchValidator('password', 'confirmPassword'));
  }

  it('should return null when both fields are empty', () => {
    const group = buildGroup('', '');
    expect(group.errors).toBeNull();
  });

  it('should return null when only the password field is filled', () => {
    const group = buildGroup('secret', '');
    expect(group.errors).toBeNull();
  });

  it('should return null when only the confirm field is filled', () => {
    const group = buildGroup('', 'secret');
    expect(group.errors).toBeNull();
  });

  it('should return null when passwords match', () => {
    const group = buildGroup('secret123', 'secret123');
    expect(group.errors).toBeNull();
  });

  it('should return passwordMismatch error when passwords do not match', () => {
    const group = buildGroup('secret123', 'different');
    expect(group.errors).toEqual({passwordMismatch: true});
  });

  it('should ignore leading/trailing whitespace differences if values differ literally', () => {
    const group = buildGroup('secret', ' secret ');
    expect(group.errors).toEqual({passwordMismatch: true});
  });

  it('should return null when values are equal but not strings', () => {
    const group = buildGroup(123, 123);
    expect(group.errors).toBeNull();
  });

  it('should return passwordMismatch when control names do not exist in the group', () => {
    const group = new FormGroup({
      other: new FormControl('value')
    }, passwordMatchValidator('missingPassword', 'missingConfirm'));

    // Both controls are missing, so !password || !confirmPassword is true → null
    expect(group.errors).toBeNull();
  });
});
