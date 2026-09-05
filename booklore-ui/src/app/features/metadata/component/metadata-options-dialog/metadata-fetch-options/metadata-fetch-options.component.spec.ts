import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {BehaviorSubject, of} from 'rxjs';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {MetadataFetchOptionsComponent} from './metadata-fetch-options.component';
import {TaskHelperService} from '../../../../settings/task-management/task-helper.service';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {commonComponentTestProviders} from '../../../../../../testing';

describe('MetadataFetchOptionsComponent', () => {
  let fixture: ComponentFixture<MetadataFetchOptionsComponent>;
  let component: MetadataFetchOptionsComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MetadataFetchOptionsComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        ...commonComponentTestProviders,
        {provide: DynamicDialogConfig, useValue: {data: {libraryId: 1, bookIds: [1, 2], metadataRefreshType: 'FULL'}}},
        {provide: DynamicDialogRef, useValue: {close: vi.fn()}},
        {provide: TaskHelperService, useValue: {
          refreshMetadataTask: vi.fn(() => of(undefined))
        }},
        {provide: AppSettingsService, useValue: {
          appSettings$: new BehaviorSubject({defaultMetadataRefreshOptions: {}} as any).asObservable(),
          publicAppSettings$: new BehaviorSubject(null).asObservable(),
          currentPublicSettings: null,
          saveSettings: vi.fn(() => of(undefined)),
          toggleOidcEnabled: vi.fn(() => of(undefined))
        }}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    fixture = TestBed.createComponent(MetadataFetchOptionsComponent);
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
