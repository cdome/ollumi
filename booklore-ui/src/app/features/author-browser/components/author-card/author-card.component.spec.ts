import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {BehaviorSubject} from 'rxjs';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {AuthorCardComponent} from './author-card.component';
import {AuthorService} from '../../service/author.service';
import {MessageService} from 'primeng/api';
import {commonComponentTestProviders} from '../../../../../testing';

const mockAuthor = {
  id: 1,
  name: 'Test Author',
  hasPhoto: true,
  asin: 'B123',
  bookCount: 10,
  cachedThumbnailUrl: ''
} as any;

describe('AuthorCardComponent', () => {
  let fixture: ComponentFixture<AuthorCardComponent>;
  let component: AuthorCardComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AuthorCardComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        ...commonComponentTestProviders,
        {provide: AuthorService, useValue: {
          getAuthorThumbnailUrl: vi.fn(() => ''),
          quickMatchAuthor: vi.fn(() => new BehaviorSubject(mockAuthor).asObservable())
        }},
        {provide: MessageService, useValue: {add: vi.fn(), clear: vi.fn()}}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    fixture = TestBed.createComponent(AuthorCardComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('author', mockAuthor);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should emit cardClick when card is clicked', () => {
    const spy = vi.fn();
    component.cardClick.subscribe(spy);
    const target = fixture.nativeElement.querySelector('.author-card') || fixture.nativeElement;
    const event = new MouseEvent('click', {bubbles: true});
    target.dispatchEvent(event);
    expect(spy).toHaveBeenCalled();
  });
});
