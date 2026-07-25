import {effect, inject, Injectable} from '@angular/core';
import {User, UserService, UserSettings} from '../user-management/user.service';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';

@Injectable({providedIn: 'root'})
export class ReaderPreferencesService {
  private readonly userService = inject(UserService);
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);
  private currentUser: User | null = null;

  constructor() {
    effect(() => {
      const user = this.userService.currentUser();
      if (user) {
        this.currentUser = user;
      }
    });
  }

  updatePreference(path: string[], value: unknown): void {
    if (!this.currentUser) return;

    let target: Record<string, unknown> = this.currentUser.userSettings as unknown as Record<string, unknown>;
    for (let i = 0; i < path.length - 1; i++) {
      target = (target[path[i]] ||= {}) as Record<string, unknown>;
    }
    target[path.at(-1)!] = value;

    const [rootKey] = path;
    const updatedValue = this.currentUser.userSettings[rootKey as keyof UserSettings];

    this.userService.updateUserSetting(this.currentUser.id, rootKey, updatedValue);
    this.messageService.add({
      severity: 'success',
      summary: this.t.translate('settingsReader.toast.preferencesUpdated'),
      detail: this.t.translate('settingsReader.toast.preferencesUpdatedDetail'),
      life: 2000
    });
  }
}
