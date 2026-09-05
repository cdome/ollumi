import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {BookSenderComponent} from './book-sender.component';
import {EmailV2ProviderService} from '../../../settings/email-v2/email-v2-provider/email-v2-provider.service';
import {EmailV2RecipientService} from '../../../settings/email-v2/email-v2-recipient/email-v2-recipient.service';
import {EmailService} from '../../../settings/email-v2/email.service';
import {commonComponentTestProviders} from '../../../../../testing';
import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {createMockBook} from '../../../../../testing/factories';
import {of} from 'rxjs';

describe('BookSenderComponent', () => {
  let fixture: ComponentFixture<BookSenderComponent>;
  let component: BookSenderComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders.filter(p => p.provide !== TranslocoService),
        {provide: DynamicDialogRef, useValue: {close: vi.fn()}},
        {provide: DynamicDialogConfig, useValue: {data: {book: createMockBook()}}},
        {provide: EmailV2ProviderService, useValue: {getEmailProviders: vi.fn(() => of([]))}},
        {provide: EmailV2RecipientService, useValue: {getRecipients: vi.fn(() => of([]))}},
        {provide: EmailService, useValue: {emailBook: vi.fn(() => of(void 0))}}
      ]
    });

    fixture = TestBed.createComponent(BookSenderComponent);
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
