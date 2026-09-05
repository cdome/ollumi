import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {ExternalDocLinkComponent} from './external-doc-link.component';

describe('ExternalDocLinkComponent', () => {
  let fixture: ComponentFixture<ExternalDocLinkComponent>;
  let component: ExternalDocLinkComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ExternalDocLinkComponent, TranslocoTestingModule.forRoot({langs: {}})]
    });

    fixture = TestBed.createComponent(ExternalDocLinkComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('docType', 'authentication');
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.resetTestingModule();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
