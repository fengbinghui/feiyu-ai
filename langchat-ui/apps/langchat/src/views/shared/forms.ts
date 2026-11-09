import type { VbenFormSchema } from '#/adapter/form';

/**
 * 表单字段分组辅助（风格 A）
 *
 * VbenForm 本身没有「分组标题」能力，这里通过注册的 Divider 组件渲染分组标题，
 * 分组项使用 `__group_` 前缀的占位字段名，提交前可用 stripFormGroupFields 清理。
 */
export const FORM_GROUP_FIELD_PREFIX = '__group_';

/**
 * 生成表单分组标题（Divider）
 */
export function formGroupDivider(
  name: string,
  title: string,
): VbenFormSchema {
  return {
    component: 'Divider',
    componentProps: {
      titlePlacement: 'left',
    },
    fieldName: `${FORM_GROUP_FIELD_PREFIX}${name}`,
    formItemClass: 'lc-form-divider col-span-full',
    hideLabel: true,
    renderComponentContent: () => ({
      default: () => title,
    }),
  } as VbenFormSchema;
}

/**
 * 移除表单分组占位字段，避免把 UI 辅助字段提交给接口
 */
export function stripFormGroupFields<T extends Record<string, any>>(
  values: T,
): T {
  const result: Record<string, any> = { ...values };
  for (const key of Object.keys(result)) {
    if (key.startsWith(FORM_GROUP_FIELD_PREFIX)) {
      delete result[key];
    }
  }
  return result as T;
}
