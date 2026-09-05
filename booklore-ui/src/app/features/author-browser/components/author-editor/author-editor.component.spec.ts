import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {AuthorEditorComponent} from './author-editor.component';
import {AuthorService} from '../../service/author.service';
import {AuthorDetails} from '../../model/author.model';
import {commonComponentTestProviders} from '../../../../../testing';
import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {of} from 'rxjs';

describe('AuthorEditorComponent', () => {
  let fixture: ComponentFixture<AuthorEditorComponent>;
  let component: AuthorEditorComponent;

  const mockAuthor: AuthorDetails = {
    id: 1,
    name: 'Test Author',
    description: '',
    asin: '',
    nameLocked: false,
    descriptionLocked: false,
    asinLocked: false,
    photoLocked: false
  } as AuthorDetails;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders.filter(p => p.provide !== TranslocoService),
        {
          provide: AuthorService,
          useValue: {
            getAuthorDetails: vi.fn(() => of(mockAuthor)),
            getAuthorByName: vi.fn(() => of(mockAuthor)),
            updateAuthor: vi.fn(() => of(mockAuthor)),
            getAuthorPhotoUrl: vi.fn(() => '/api/photo'),
            getUploadAuthorPhotoUrl: vi.fn(() => '/api/upload')
          }
        }
      ]
    });

    fixture = TestBed.createComponent(AuthorEditorComponent);
    component = fixture.componentInstance;
    component.authorId = 1;
    component.author = mockAuthor;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should create and render without error', () => {
    expect(component).toBeTruthy();
    expect(component.form).toBeTruthy();
    expect(fixture.nativeElement).toBeTruthy();
  });
});
