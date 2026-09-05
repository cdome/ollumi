import {runPermissionGuardTests} from './permission-guard.spec-helper';
import {UserStatsGuard} from './user-stats.guard';

runPermissionGuardTests('UserStatsGuard', UserStatsGuard, 'canAccessUserStats');
