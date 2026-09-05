import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {of} from 'rxjs';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {MetadataTabsComponent} from './metadata-tabs.component';
import {AudiobookService} from '../../../../../readers/audiobook-player/audiobook.service';
import {createMockBook, commonComponentTestProviders} from '../../../../../../../testing';

describe('MetadataTabsComponent', () => {
  let fixture: ComponentFixture<MetadataTabsComponent>;
  let component: MetadataTabsComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MetadataTabsComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        ...commonComponentTestProviders,
        {provide: AudiobookService, useValue: {
          getAudiobookInfo: vi.fn(() => of({}))
        }}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    fixture = TestBed.createComponent(MetadataTabsComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('book', createMockBook());
    fixture.componentRef.setInput('bookInSeries', []);
    fixture.componentRef.setInput('recommendedBooks', []);
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
