import { baseRequestClient, requestClient } from '#/api/request';

export namespace AuthApi {
  /** 登录接口参数 */
  export interface LoginParams {
    password?: string;
    username?: string;
  }

  /** 登录接口返回值 */
  export interface LoginResult {
    accessToken: string;
    tokenName: string;
  }

  export interface BackendAuthCurrentUserResponse {
    displayName?: string;
    permissions?: string[];
    roles?: string[];
    tenantId?: string;
    userId: string;
    username: string;
  }

  export interface BackendAuthLoginResponse extends BackendAuthCurrentUserResponse {
    tokenName?: string;
    tokenValue: string;
  }

  export interface RefreshTokenResult {
    data: string;
    status: number;
  }
}

/**
 * 登录
 */
export async function loginApi(data: AuthApi.LoginParams) {
  const response = await requestClient.post<AuthApi.BackendAuthLoginResponse>(
    '/v1/auth/login',
    data,
  );
  return {
    accessToken: response.tokenValue,
    tokenName: response.tokenName || 'Authorization',
  };
}

/**
 * 刷新accessToken
 */
export async function refreshTokenApi() {
  return baseRequestClient.post<AuthApi.RefreshTokenResult>(
    '/v1/auth/refresh',
    {
      withCredentials: true,
    },
  );
}

/**
 * 退出登录
 */
export async function logoutApi() {
  return requestClient.post<boolean>('/v1/auth/logout');
}

/**
 * 获取用户权限码
 */
export async function getAccessCodesApi() {
  const response =
    await requestClient.get<AuthApi.BackendAuthCurrentUserResponse>(
      '/v1/auth/me',
    );
  return response.permissions ?? [];
}

/** 注册参数 */
export interface RegisterParams {
  email?: string;
  password: string;
  phone?: string;
  realName?: string;
  username: string;
}

/** 个人资料可编辑字段 */
export interface ProfileUpdateParams {
  avatar?: string;
  email?: string;
  phone?: string;
  realName?: string;
}

/** 当前登录用户（含资料扩展字段） */
export interface BackendCurrentUserProfile
  extends AuthApi.BackendAuthCurrentUserResponse {
  avatar?: string;
  email?: string;
  phone?: string;
}

/**
 * 查询是否开放注册（公开接口）
 * 探测失败时默认放开表单，最终是否允许注册由后端在提交时校验。
 */
export async function registerEnabledApi(): Promise<boolean> {
  try {
    const response = await baseRequestClient.get<{ data: boolean }>(
      '/v1/auth/register-enabled',
    );
    const payload = (response as any)?.data;
    return Boolean(payload?.data ?? payload);
  } catch {
    return true;
  }
}

/** 注册账号 */
export async function registerApi(data: RegisterParams) {
  return requestClient.post<{ userId?: string; username?: string }>(
    '/v1/auth/register',
    data,
  );
}

/** 修改密码（需登录） */
export async function changePasswordApi(data: {
  newPassword: string;
  oldPassword: string;
}) {
  return requestClient.post<boolean>('/v1/auth/change-password', data);
}

/** 更新个人资料（需登录） */
export async function updateProfileApi(data: ProfileUpdateParams) {
  return requestClient.put<BackendCurrentUserProfile>('/v1/auth/profile', data);
}

/** 获取当前用户完整资料（个人中心回填） */
export async function getCurrentUserProfileApi() {
  return requestClient.get<BackendCurrentUserProfile>('/v1/auth/me');
}
