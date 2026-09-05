import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {MetadataEditorComponent} from './metadata-editor.component';
import {
  commonMetadataProviders,
  mockActivatedRouteProvider,
  mockBrowserGlobals,
  provideBookSubject
} from '../../../../../../testing/metadata-component-helpers';

describe('MetadataEditorComponent', () => {
  let fixture: ComponentFixture<MetadataEditorComponent>;
  let component: MetadataEditorComponent;

  beforeEach(() => {
    mockBrowserGlobals();

    TestBed.configureTestingModule({
      imports: [MetadataEditorComponent, TranslocoTestingModule.forRoot({langs: {en: {}}}), NoopAnimationsModule],
      providers: [
        mockActivatedRouteProvider(),
        ...commonMetadataProviders
      ]
    });

    fixture = TestBed.createComponent(MetadataEditorComponent);
    component = fixture.componentInstance;
    component.book$ = provideBookSubject().asObservable();
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
