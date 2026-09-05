import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {BehaviorSubject, of} from 'rxjs';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {MetadataProviderFieldSelectorComponent} from './metadata-provider-field-selector.component';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {commonComponentTestProviders} from '../../../../../testing';

describe('MetadataProviderFieldSelectorComponent', () => {
  let fixture: ComponentFixture<MetadataProviderFieldSelectorComponent>;
  let component: MetadataProviderFieldSelectorComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MetadataProviderFieldSelectorComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        ...commonComponentTestProviders,
        {provide: AppSettingsService, useValue: {
          appSettings$: new BehaviorSubject({metadataProviderSpecificFields: {}} as any).asObservable(),
          publicAppSettings$: new BehaviorSubject(null).asObservable(),
          currentPublicSettings: null,
          saveSettings: vi.fn(() => of(undefined)),
          toggleOidcEnabled: vi.fn(() => of(undefined))
        }}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    fixture = TestBed.createComponent(MetadataProviderFieldSelectorComponent);
    component = fixture.componentInstance;
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
