import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {CommonModule} from '@angular/common';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {AuthorDetailComponent} from './author-detail.component';
import {commonComponentTestProviders} from '../../../../../testing';
import {ActivatedRoute} from '@angular/router';
import {of} from 'rxjs';

describe('AuthorDetailComponent', () => {
  let fixture: ComponentFixture<AuthorDetailComponent>;
  let component: AuthorDetailComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders,
        {
          provide: ActivatedRoute,
          useValue: {
            params: of({}),
            queryParams: of({}),
            snapshot: {
              paramMap: {get: vi.fn(() => '1')},
              queryParamMap: {get: vi.fn(() => null)}
            }
          }
        }
      ]
    }).overrideComponent(AuthorDetailComponent, {
      set: {
        imports: [CommonModule, FormsModule, ReactiveFormsModule, TranslocoPipe],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(AuthorDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load author id from route', () => {
    expect(component.author).not.toBeNull();
  });
});
