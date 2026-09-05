import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {BookSearcherComponent} from './book-searcher.component';
import {BookService} from '../../service/book.service';
import {commonComponentTestProviders} from '../../../../../testing';
import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {of} from 'rxjs';

describe('BookSearcherComponent', () => {
  let fixture: ComponentFixture<BookSearcherComponent>;
  let component: BookSearcherComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders.filter(p => p.provide !== TranslocoService),
        {
          provide: BookService,
          useValue: {
            bookState$: of({loaded: true, books: [], error: null}),
            getCurrentBookState: vi.fn(() => ({loaded: true, books: [], error: null}))
          }
        }
      ]
    });

    fixture = TestBed.createComponent(BookSearcherComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should create and render without error', () => {
    expect(component).toBeTruthy();
    expect(fixture.nativeElement).toBeTruthy();
  });
});
