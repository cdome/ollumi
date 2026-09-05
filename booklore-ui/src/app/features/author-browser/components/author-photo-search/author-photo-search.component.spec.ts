import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {AuthorPhotoSearchComponent} from './author-photo-search.component';
import {AuthorService} from '../../service/author.service';
import {commonComponentTestProviders} from '../../../../../testing';
import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {of} from 'rxjs';

describe('AuthorPhotoSearchComponent', () => {
  let fixture: ComponentFixture<AuthorPhotoSearchComponent>;
  let component: AuthorPhotoSearchComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders.filter(p => p.provide !== TranslocoService),
        {provide: DynamicDialogRef, useValue: {close: vi.fn()}},
        {provide: DynamicDialogConfig, useValue: {data: {authorId: 1, authorName: 'Test Author'}}},
        {
          provide: AuthorService,
          useValue: {
            searchAuthorPhotos: vi.fn(() => of([])),
            uploadAuthorPhotoFromUrl: vi.fn(() => of(void 0))
          }
        }
      ]
    });

    fixture = TestBed.createComponent(AuthorPhotoSearchComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should create and render without error', () => {
    expect(component).toBeTruthy();
    expect(component.searchForm).toBeTruthy();
    expect(fixture.nativeElement).toBeTruthy();
  });
});
