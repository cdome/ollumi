import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {ChangePasswordComponent} from './change-password.component';
import {UserService} from '../../../features/settings/user-management/user.service';
import {AuthService} from '../../service/auth.service';
import {MessageService} from 'primeng/api';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {of, throwError} from 'rxjs';

describe('ChangePasswordComponent', () => {
  let fixture: ComponentFixture<ChangePasswordComponent>;
  let component: ChangePasswordComponent;
  let userServiceMock: { changePassword: ReturnType<typeof vi.fn> };
  let authServiceMock: { logout: ReturnType<typeof vi.fn> };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    userServiceMock = {changePassword: vi.fn(() => of(undefined))};
    authServiceMock = {logout: vi.fn()};
    messageServiceMock = {add: vi.fn()};

    TestBed.configureTestingModule({
      imports: [ChangePasswordComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        {provide: UserService, useValue: userServiceMock},
        {provide: AuthService, useValue: authServiceMock},
        {provide: MessageService, useValue: messageServiceMock}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    fixture = TestBed.createComponent(ChangePasswordComponent);
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

  it('should require all fields', () => {
    component.currentPassword = '';
    component.newPassword = 'newpass';
    component.confirmNewPassword = 'newpass';
    component.changePassword();
    expect(component.errorMessage).toContain('shared.changePassword.validation.allFieldsRequired');
    expect(userServiceMock.changePassword).not.toHaveBeenCalled();
  });

  it('should reject mismatched passwords', () => {
    component.currentPassword = 'oldpass';
    component.newPassword = 'newpass';
    component.confirmNewPassword = 'different';
    component.changePassword();
    expect(component.errorMessage).toContain('shared.changePassword.validation.passwordsDoNotMatch');
    expect(userServiceMock.changePassword).not.toHaveBeenCalled();
  });

  it('should reject a new password identical to the current password', () => {
    component.currentPassword = 'samepass';
    component.newPassword = 'samepass';
    component.confirmNewPassword = 'samepass';
    component.changePassword();
    expect(component.errorMessage).toContain('shared.changePassword.validation.sameAsCurrentPassword');
    expect(userServiceMock.changePassword).not.toHaveBeenCalled();
  });

  it('should change password and log out on success', () => {
    component.currentPassword = 'oldpass';
    component.newPassword = 'newpass';
    component.confirmNewPassword = 'newpass';

    component.changePassword();

    expect(userServiceMock.changePassword).toHaveBeenCalledWith('oldpass', 'newpass');
    expect(component.successMessage).toContain('shared.changePassword.toast.success');
    expect(authServiceMock.logout).toHaveBeenCalled();
  });

  it('should show an error toast when change password fails', () => {
    userServiceMock.changePassword.mockReturnValue(throwError(() => new Error('bad password')));
    component.currentPassword = 'oldpass';
    component.newPassword = 'newpass';
    component.confirmNewPassword = 'newpass';

    component.changePassword();

    expect(component.errorMessage).toBe('bad password');
    expect(messageServiceMock.add).toHaveBeenCalled();
  });
});
