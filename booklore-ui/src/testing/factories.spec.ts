import {describe, expect, it} from 'vitest';
import {
  createMockBook,
  createMockLibrary,
  createMockPublicAppSettings,
  createMockShelf,
  createMockUser
} from './factories';
import {ReadStatus} from '../app/features/book/model/book.model';

describe('Test factories', () => {
  it('createMockBook should provide sensible defaults', () => {
    const book = createMockBook();
    expect(book.id).toBe(1);
    expect(book['bookType']).toBe('EPUB');
    expect(book['readStatus']).toBe(ReadStatus.UNREAD);
    expect(book.metadata?.title).toBe('Test Book');
  });

  it('createMockBook should apply overrides', () => {
    const book = createMockBook({id: 42, fileName: 'override.epub'});
    expect(book.id).toBe(42);
    expect(book.fileName).toBe('override.epub');
  });

  it('createMockShelf should provide defaults', () => {
    const shelf = createMockShelf();
    expect(shelf.name).toBe('Test Shelf');
  });

  it('createMockLibrary should provide defaults', () => {
    const library = createMockLibrary();
    expect(library.name).toBe('Test Library');
    expect(library.paths.length).toBe(1);
  });

  it('createMockUser should provide defaults', () => {
    const user = createMockUser();
    expect(user.username).toBe('testuser');
    expect(user.permissions.admin).toBe(false);
  });

  it('createMockPublicAppSettings should provide defaults', () => {
    const settings = createMockPublicAppSettings();
    expect(settings.oidcEnabled).toBe(false);
    expect(settings.remoteAuthEnabled).toBe(false);
  });
});
