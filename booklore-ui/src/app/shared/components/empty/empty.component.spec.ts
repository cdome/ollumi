import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {EmptyComponent} from './empty.component';

describe('EmptyComponent', () => {
  let fixture: ComponentFixture<EmptyComponent>;
  let component: EmptyComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [EmptyComponent]
    });
    fixture = TestBed.createComponent(EmptyComponent);
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

  it('should render the placeholder text', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('empty works!');
  });
});
