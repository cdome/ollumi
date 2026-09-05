import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {AuthorMatchComponent} from './author-match.component';
import {AuthorService} from '../../service/author.service';
import {AuthorDetails} from '../../model/author.model';
import {commonComponentTestProviders} from '../../../../../testing';
import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {of} from 'rxjs';

describe('AuthorMatchComponent', () => {
  let fixture: ComponentFixture<AuthorMatchComponent>;
  let component: AuthorMatchComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders.filter(p => p.provide !== TranslocoService),
        {
          provide: AuthorService,
          useValue: {
            searchAuthorMetadata: vi.fn(() => of([])),
            matchAuthor: vi.fn(() => of({id: 1, name: 'Test Author'} as AuthorDetails))
          }
        }
      ]
    });

    fixture = TestBed.createComponent(AuthorMatchComponent);
    component = fixture.componentInstance;
    component.authorId = 1;
    component.authorName = 'Test Author';
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should create and render without error', () => {
    expect(component).toBeTruthy();
    expect(component.searchQuery).toBe('Test Author');
    expect(fixture.nativeElement).toBeTruthy();
  });
});
