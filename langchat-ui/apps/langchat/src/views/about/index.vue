<script lang="ts" setup>
import { computed } from 'vue';

import { Page } from '@vben/common-ui';
import {
  Bot,
  ChartNoAxesCombined,
  LibraryBig,
  Wrench,
} from '@vben/icons';
import { $t } from '@vben/locales';
import { preferences, usePreferences } from '@vben/preferences';

import LcCard from '#/components/LcCard/index.vue';
import {
  FEIYU_PRODUCT_NAME,
  FEIYU_PRODUCT_TEAM,
} from '#/constants/product';

import FeiyuArchitecture from './components/feiyu-architecture.vue';

const { isDark } = usePreferences();

const appName = computed(() => preferences.app.name);
const logoSrc = computed(() =>
  isDark.value && preferences.logo.sourceDark
    ? preferences.logo.sourceDark
    : preferences.logo.source,
);
const appVersion = import.meta.env.VITE_APP_VERSION;

/** 产品版本与部署信息 */
const metaItems = computed(() => [
  {
    label: $t('about.meta.version'),
    value: `v${appVersion}`,
  },
  {
    label: $t('about.meta.edition'),
    value: $t('about.meta.editionValue'),
  },
  {
    label: $t('about.meta.deployment'),
    value: $t('about.meta.deploymentValue'),
  },
  {
    label: $t('about.meta.updated'),
    value: $t('about.meta.updatedValue'),
  },
]);

/** 核心能力概览 */
const featureCards = computed(() => [
  {
    description: $t('about.features.models.description'),
    icon: Bot,
    label: $t('about.features.models.label'),
  },
  {
    description: $t('about.features.knowledge.description'),
    icon: LibraryBig,
    label: $t('about.features.knowledge.label'),
  },
  {
    description: $t('about.features.orchestration.description'),
    icon: Wrench,
    label: $t('about.features.orchestration.label'),
  },
  {
    description: $t('about.features.streaming.description'),
    icon: ChartNoAxesCombined,
    label: $t('about.features.streaming.label'),
  },
]);

/** 技术栈 */
const techStacks = computed(() => [
  {
    label: $t('about.stack.frontend.label'),
    value: $t('about.stack.frontend.value'),
  },
  {
    label: $t('about.stack.backend.label'),
    value: $t('about.stack.backend.value'),
  },
  {
    label: $t('about.stack.runtime.label'),
    value: $t('about.stack.runtime.value'),
  },
  {
    label: $t('about.stack.storage.label'),
    value: $t('about.stack.storage.value'),
  },
]);
</script>

<template>
  <Page>
    <div class="flex flex-col gap-4">
      <!-- 品牌与产品介绍 -->
      <section
        class="relative overflow-hidden rounded-xl border border-border bg-card px-5 py-6 sm:px-7 sm:py-8"
      >
        <div
          class="pointer-events-none absolute -right-24 -top-32 size-72 rounded-full bg-primary/10 blur-3xl"
        ></div>
        <div
          class="relative grid gap-8 xl:grid-cols-[minmax(0,1fr)_320px] xl:items-center"
        >
          <div class="min-w-0">
            <div class="flex items-start gap-3.5">
              <img
                :alt="appName"
                :src="logoSrc"
                class="size-12 shrink-0 rounded-xl shadow-sm"
              />
              <div class="min-w-0">
                <div class="text-xs font-medium text-muted-foreground">
                  {{ FEIYU_PRODUCT_TEAM }} ·
                  {{ $t('about.hero.badge') }}
                </div>
                <h1
                  class="mt-1 text-balance text-2xl font-semibold tracking-tight text-foreground"
                >
                  {{ $t('about.hero.title', { name: FEIYU_PRODUCT_NAME }) }}
                </h1>
              </div>
            </div>

            <p
              class="mt-4 max-w-4xl text-pretty text-sm leading-6 text-muted-foreground"
            >
              {{ $t('about.hero.introFirst') }}
            </p>
            <p
              class="mt-2 max-w-4xl text-pretty text-sm leading-6 text-muted-foreground"
            >
              {{ $t('about.hero.introSecond') }}
            </p>

            <div
              class="mt-5 flex flex-wrap items-center gap-2 text-[11px] text-muted-foreground"
            >
              <span
                class="inline-flex items-center gap-1.5 rounded-full border border-border bg-background/60 px-2.5 py-1"
              >
                <span class="size-1.5 rounded-full bg-emerald-500"></span>
                {{ $t('about.hero.badge') }}
              </span>
              <span
                class="rounded-full border border-border bg-background/60 px-2.5 py-1"
              >
                v{{ appVersion }}
              </span>
            </div>
          </div>

          <!-- 版本与部署信息 -->
          <div class="grid grid-cols-2 gap-2.5">
            <div
              v-for="item in metaItems"
              :key="item.label"
              class="rounded-lg border border-border/70 bg-background/60 px-3 py-2.5"
            >
              <div
                class="text-[10px] font-medium uppercase tracking-[0.08em] text-muted-foreground"
              >
                {{ item.label }}
              </div>
              <div class="mt-1 text-sm font-semibold text-foreground">
                {{ item.value }}
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 能力概览 -->
      <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <div
          v-for="item in featureCards"
          :key="item.label"
          class="rounded-xl border border-border bg-card p-4 shadow-xs transition-shadow hover:shadow-sm"
        >
          <div
            class="flex size-9 items-center justify-center rounded-lg bg-primary/10 text-primary"
          >
            <component :is="item.icon" class="size-4.5" />
          </div>
          <div class="mt-3 text-sm font-semibold text-foreground">
            {{ item.label }}
          </div>
          <div class="mt-1 text-xs leading-5 text-muted-foreground">
            {{ item.description }}
          </div>
        </div>
      </div>

      <!-- 系统组件架构 -->
      <LcCard :hoverable="false" :show-icon="false">
        <template #header>
          <div>
            <div class="text-sm font-semibold text-foreground">
              {{ $t('about.architecture.title') }}
            </div>
            <div class="mt-1 text-xs font-normal text-muted-foreground">
              {{ $t('about.architecture.description') }}
            </div>
          </div>
        </template>
        <FeiyuArchitecture />
      </LcCard>

      <!-- 技术架构 -->
      <LcCard :hoverable="false" :show-icon="false">
        <template #header>
          <div class="text-sm font-semibold text-foreground">
            {{ $t('about.stack.title') }}
          </div>
        </template>
        <div class="grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
          <div
            v-for="item in techStacks"
            :key="item.label"
            class="rounded-lg border border-border/70 bg-muted/20 px-3 py-2.5"
          >
            <div class="text-xs font-medium text-foreground">
              {{ item.label }}
            </div>
            <div class="mt-1 text-xs leading-5 text-muted-foreground">
              {{ item.value }}
            </div>
          </div>
        </div>
      </LcCard>

      <!-- 许可与合规（弱化展示，不提供任何外链） -->
      <section
        class="rounded-xl border border-dashed border-border bg-muted/20 px-4 py-3"
      >
        <div class="text-xs font-medium text-foreground">
          {{ $t('about.license.title') }}
        </div>
        <p class="mt-1 text-[11px] leading-5 text-muted-foreground">
          {{ $t('about.license.description') }}
        </p>
      </section>
    </div>
  </Page>
</template>
