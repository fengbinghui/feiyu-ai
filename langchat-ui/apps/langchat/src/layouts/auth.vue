<!--
  - © 2026 肥鱼AI. 版权所有.
  -->
<script lang="ts" setup>
import { computed } from 'vue';

import {
  Bot,
  ChartNoAxesCombined,
  LibraryBig,
  Wrench,
} from '@vben/icons';
import { AuthPageLayout } from '@vben/layouts';
import { $t } from '@vben/locales';
import { preferences, usePreferences } from '@vben/preferences';

const { isDark } = usePreferences();

const appName = computed(() => preferences.app.name);
const logo = computed(() => preferences.logo.source);
const logoDark = computed(() => preferences.logo.sourceDark);
/**
 * 登录页品牌区使用当前主题对应的 Logo（浅色 / 深色两套）
 */
const logoSrc = computed(() =>
  isDark.value && logoDark.value ? logoDark.value : logo.value,
);

/**
 * 登录页左侧特色功能卡片
 */
const authFeatures = computed(() => [
  {
    icon: Bot,
    title: $t('auth.features.builder.title'),
    description: $t('auth.features.builder.description'),
  },
  {
    icon: LibraryBig,
    title: $t('auth.features.knowledge.title'),
    description: $t('auth.features.knowledge.description'),
  },
  {
    icon: Wrench,
    title: $t('auth.features.extensions.title'),
    description: $t('auth.features.extensions.description'),
  },
  {
    icon: ChartNoAxesCombined,
    title: $t('auth.features.dataAnalysis.title'),
    description: $t('auth.features.dataAnalysis.description'),
  },
]);
</script>

<template>
  <AuthPageLayout :app-name="appName" :logo="logo" :logo-dark="logoDark">
    <!-- 左侧品牌区：肥鱼AI Logo + 品牌文案 + 能力卡片 -->
    <template #slogan>
      <div class="flex w-full max-w-xl flex-col items-center">
        <!-- 品牌标识 -->
        <div class="relative">
          <div
            class="absolute inset-0 -z-10 scale-125 rounded-full bg-primary/15 blur-2xl"
          ></div>
          <img
            :alt="appName"
            :src="logoSrc"
            class="size-24 rounded-[28px] shadow-sm"
          />
        </div>

        <h1
          class="mt-6 text-3xl font-semibold tracking-tight text-foreground"
        >
          {{ appName }}
        </h1>
        <p class="mt-2 text-lg font-medium text-primary">
          {{ $t('auth.brand.slogan') }}
        </p>
        <p
          class="mt-2 max-w-md text-center text-sm leading-6 text-muted-foreground"
        >
          {{ $t('auth.brand.description') }}
        </p>

        <!-- 能力卡片 -->
        <div class="mt-10 grid w-full grid-cols-1 gap-3 sm:grid-cols-2">
          <div
            v-for="feature in authFeatures"
            :key="feature.title"
            class="group flex items-start gap-3 rounded-xl border border-border/70 bg-background/70 p-4 text-left shadow-xs backdrop-blur-sm transition-all duration-200 hover:-translate-y-0.5 hover:border-primary/30 hover:shadow-md"
          >
            <div
              class="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary transition-colors group-hover:bg-primary group-hover:text-primary-foreground"
            >
              <component :is="feature.icon" class="size-5" />
            </div>
            <div class="min-w-0">
              <div class="text-sm font-semibold text-foreground">
                {{ feature.title }}
              </div>
              <div class="mt-1 text-xs leading-5 text-muted-foreground">
                {{ feature.description }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 面板底部署名信息（锚定到左侧品牌面板底部） -->
      <div
        class="absolute inset-x-0 bottom-5 flex flex-col items-center gap-1.5 text-xs text-muted-foreground"
      >
        <div class="flex items-center gap-2">
          <img :alt="appName" :src="logoSrc" class="size-4 opacity-80" />
          <span class="text-sm font-semibold text-foreground/80">
            {{ appName }}
          </span>
          <span>{{ $t('auth.footer.team') }}</span>
        </div>
        <div>{{ $t('auth.footer.copyright') }}</div>
      </div>
    </template>
  </AuthPageLayout>
</template>
