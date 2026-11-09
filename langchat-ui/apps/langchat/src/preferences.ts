import {defineOverridesPreferences} from '@vben/preferences';

/**
 * @description 项目配置文件（肥鱼AI）
 * 只需要覆盖项目中的一部分配置，不需要的配置不用覆盖，会自动使用默认配置
 * !!! 更改配置后请清空缓存，否则可能不生效
 */
export const overridesPreferences = defineOverridesPreferences({
  // overrides
  app: {
    name: '肥鱼AI',
    accessMode: 'mixed',
    dynamicTitle: true,
    // 关闭外联的「检查更新」，避免任何不必要的远端请求
    enableCheckUpdates: false,
    defaultHomePath: '/explore',
    // 风格 A：默认不展示水印，用户可在偏好设置中手动开启
    watermark: false,
  },
  logo: {
    enable: true,
    source: '/logo.svg',
    sourceDark: '/logo-dark.svg',
  },
  footer: {
    enable: false,
  },
  theme: {
    mode: 'light',
    builtinType: 'default',
    // 品牌主色保持不变
    colorPrimary: 'hsl(212 100% 45%)',
    // 风格 A：统一使用实线细边框
    dashedBorder: false,
    // 8px 圆角体系
    radius: '0.5',
  },
  transition: {
    name: 'fade',
  },
  widget: {
    globalSearch: false,
    lockScreen: false,
    notification: false,
    refresh: false,
    timezone: false,
  },
  copyright: {
    companyName: '肥鱼AI',
    // 不展示任何外部站点链接
    companySiteLink: '',
    date: '2026',
    enable: true,
  },
  navigation: {
    flatGroupMode: true,
  },
  sidebar: {
    floatingMode: true,
  },
});
