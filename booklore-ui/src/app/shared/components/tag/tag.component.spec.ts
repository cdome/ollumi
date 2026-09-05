import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {TagComponent} from './tag.component';

describe('TagComponent', () => {
  let fixture: ComponentFixture<TagComponent>;
  let component: TagComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TagComponent]
    });

    fixture = TestBed.createComponent(TagComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    TestBed.resetTestingModule();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render the default tag classes', () => {
    const span = fixture.nativeElement.querySelector('span');
    expect(span).toBeTruthy();
    expect(span.classList.contains('app-tag')).toBe(true);
    expect(span.classList.contains('app-tag-primary')).toBe(true);
  });
});
