import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {SetupComponent} from './setup.component';
import {SetupService} from './setup.service';
import {Router} from '@angular/router';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {of, throwError} from 'rxjs';

describe('SetupComponent', () => {
  let fixture: ComponentFixture<SetupComponent>;
  let component: SetupComponent;
  let setupServiceMock: { createAdmin: ReturnType<typeof vi.fn> };
  let routerMock: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    setupServiceMock = {createAdmin: vi.fn(() => of(undefined))};
    routerMock = {navigate: vi.fn(() => Promise.resolve(true))};

    TestBed.configureTestingModule({
      imports: [SetupComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        {provide: SetupService, useValue: setupServiceMock},
        {provide: Router, useValue: routerMock}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    fixture = TestBed.createComponent(SetupComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should not submit an invalid form', () => {
    component.setupForm.setValue({
      name: '',
      username: '',
      email: '',
      password: '',
      confirmPassword: ''
    });
    component.onSubmit();
    expect(setupServiceMock.createAdmin).not.toHaveBeenCalled();
  });

  it('should create an admin and navigate to login on success', () => {
    vi.useFakeTimers();
    component.setupForm.setValue({
      name: 'Admin User',
      username: 'admin',
      email: 'admin@example.com',
      password: 'password123',
      confirmPassword: 'password123'
    });

    component.onSubmit();

    expect(setupServiceMock.createAdmin).toHaveBeenCalledWith({
      name: 'Admin User',
      username: 'admin',
      email: 'admin@example.com',
      password: 'password123'
    });
    expect(component.success).toBe(true);

    vi.advanceTimersByTime(1500);
    expect(routerMock.navigate).toHaveBeenCalledWith(['/login']);
    vi.useRealTimers();
  });

  it('should show an error when account creation fails', () => {
    setupServiceMock.createAdmin.mockReturnValue(throwError(() => ({error: {message: 'create failed'}})));
    component.setupForm.setValue({
      name: 'Admin User',
      username: 'admin',
      email: 'admin@example.com',
      password: 'password123',
      confirmPassword: 'password123'
    });

    component.onSubmit();

    expect(component.loading).toBe(false);
    expect(component.error).toBe('create failed');
  });
});
