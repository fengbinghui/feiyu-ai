import type { UserInfo } from '@vben/types';

import { $t } from '@vben/locales';
import { preferences } from '@vben/preferences';

import { requestClient } from '#/api/request';

interface BackendAuthCurrentUserResponse {
  avatar?: string;
  displayName?: string;
  email?: string;
  permissions?: string[];
  phone?: string;
  roles?: string[];
  tenantId?: string;
  userId: string;
  username: string;
}

/**
 * 获取用户信息
 */
export async function getUserInfoApi() {
  const response =
    await requestClient.get<BackendAuthCurrentUserResponse>('/v1/auth/me');
  const roles = response.roles ?? [];
  // 普通用户无平台概览（管理员视图）权限，登录后默认进入应用市场
  const isAdmin = roles.some((role) =>
    ['ADMIN', 'SUPER_ADMIN'].includes(String(role).toUpperCase()),
  );
  return {
    avatar: response.avatar || preferences.app.defaultAvatar,
    desc: response.tenantId
      ? $t('layout.user.currentTenant', { tenantId: response.tenantId })
      : $t('layout.user.defaultDesc'),
    homePath: isAdmin ? '/explore' : '/market',
    realName: response.displayName || response.username,
    roles,
    token: '',
    userId: response.userId,
    username: response.username,
  } satisfies UserInfo;
}
