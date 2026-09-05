import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {BehaviorSubject, of} from 'rxjs';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {MetadataAdvancedFetchOptionsComponent} from './metadata-advanced-fetch-options.component';
import {MessageService} from 'primeng/api';
import {commonComponentTestProviders} from '../../../../../../testing/providers';

describe('MetadataAdvancedFetchOptionsComponent', () => {
  let fixture: ComponentFixture<MetadataAdvancedFetchOptionsComponent>;
  let component: MetadataAdvancedFetchOptionsComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MetadataAdvancedFetchOptionsComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        ...commonComponentTestProviders,
        {provide: MessageService, useValue: {add: vi.fn(), clear: vi.fn()}}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    fixture = TestBed.createComponent(MetadataAdvancedFetchOptionsComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('currentMetadataOptions', {
      libraryId: null,
      refreshCovers: false,
      mergeCategories: false,
      reviewBeforeApply: false,
      replaceMode: 'REPLACE_MISSING',
      fieldOptions: {},
      enabledFields: {}
    });
    fixture.componentRef.setInput('submitButtonLabel', 'Submit');
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
});
