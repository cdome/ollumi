import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {BookTableComponent} from './book-table.component';
import {getTranslocoModule} from '../../../../../core/testing/transloco-testing';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';
import {BookService} from '../../../service/book.service';
import {BookMetadataManageService} from '../../../service/book-metadata-manage.service';
import {MessageService} from 'primeng/api';
import {UserService} from '../../../../settings/user-management/user.service';
import {ReadStatusHelper} from '../../../helpers/read-status.helper';
import {
  createMockBookService,
  createMockUserService,
  mockBook,
  mockBookMetadataManageService,
  mockReadStatusHelper,
  mockUrlHelperService,
  setupJsdomMocks,
} from '../../../../../../testing/book-component-mocks';
import {mockMessageServiceProvider} from '../../../../../../testing/providers';

describe('BookTableComponent', () => {
  let fixture: ComponentFixture<BookTableComponent>;
  let component: BookTableComponent;

  afterEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
  });

  beforeEach(() => {
    setupJsdomMocks();
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      providers: [
        {provide: UrlHelperService, useValue: mockUrlHelperService},
        {provide: BookService, useValue: createMockBookService()},
        {provide: BookMetadataManageService, useValue: mockBookMetadataManageService},
        mockMessageServiceProvider,
        {provide: UserService, useValue: createMockUserService()},
        {provide: ReadStatusHelper, useValue: mockReadStatusHelper},
      ]
    });

    fixture = TestBed.createComponent(BookTableComponent);
    component = fixture.componentInstance;
    component.books = [mockBook];
    component.visibleColumns = [{field: 'title', header: 'Title'}];
    fixture.detectChanges();
  });

  it('should create and render rows without error', () => {
    expect(component).toBeTruthy();
    expect(component.books.length).toBe(1);
  });

  it('should select all books and emit the id set', () => {
    const emitSpy = vi.spyOn(component.selectedBooksChange, 'emit');
    component.selectAllBooks();
    expect(component.selectedBookIds.has(mockBook.id)).toBe(true);
    expect(emitSpy).toHaveBeenCalledWith(component.selectedBookIds);
  });

  it('should clear selections and emit empty set', () => {
    component.selectAllBooks();
    const emitSpy = vi.spyOn(component.selectedBooksChange, 'emit');
    component.clearSelectedBooks();
    expect(component.selectedBookIds.size).toBe(0);
    expect(emitSpy).toHaveBeenCalledWith(component.selectedBookIds);
  });

  it('should call metadata lock service when toggling lock', () => {
    const metadata = {bookId: mockBook.id, titleLocked: false} as any;
    component.toggleMetadataLock(metadata);
    expect(mockBookMetadataManageService.toggleAllLock).toHaveBeenCalledWith(new Set([mockBook.id]), 'LOCK');
  });
});
