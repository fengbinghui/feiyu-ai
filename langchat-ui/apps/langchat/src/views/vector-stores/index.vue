<script lang="ts" setup>
import type { AigcVectorStore } from '#/api/aigc/vector-store';

import { computed, onMounted, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { LayoutGrid, Plus, RefreshCcw, Search, Tag } from '@vben/icons';
import { $t } from '@vben/locales';

import { NButton, NInput, NPagination, NTag } from 'naive-ui';

import { dialog, message } from '#/adapter/naive';
import { vectorStoreApi } from '#/api/aigc/vector-store';
import LcCard from '#/components/LcCard/index.vue';
import LcEmptyState from '#/components/LcEmptyState/index.vue';
import { VECTOR_PROVIDER_OPTIONS } from '#/views/shared/aigc/options';

import VectorStoreCard from './card.vue';
import VectorStoreEdit from './edit.vue';

const loading = ref(false);
const saving = ref(false);
const showEdit = ref(false);
const keyword = ref('');
const selectedProvider = ref('ALL');
const currentPage = ref(1);
const pageSize = ref(9);
const items = ref<AigcVectorStore[]>([]);
const currentItem = ref<null | Partial<AigcVectorStore>>(null);
const providerTagOptions = computed(() => [
  { label: $t('common.labels.all'), value: 'ALL' },
  ...VECTOR_PROVIDER_OPTIONS.map((item) => ({
    label: item.label,
    value: String(item.value),
  })),
]);

const filteredItems = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return items.value.filter((item) => {
    const matchKeyword =
      !query ||
      [item.name, item.provider, item.host, item.databaseName, item.tableName]
        .map((value) => String(value ?? '').toLowerCase())
        .some((value) => value.includes(query));
    const matchProvider =
      selectedProvider.value === 'ALL' ||
      item.provider === selectedProvider.value;
    return matchKeyword && matchProvider;
  });
});

const pagedItems = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredItems.value.slice(start, start + pageSize.value);
});

const hasActiveFilter = computed(
  () => keyword.value.trim() !== '' || selectedProvider.value !== 'ALL',
);

watch([keyword, selectedProvider], () => {
  currentPage.value = 1;
});

const summaryText = computed(() => {
  const total = items.value.length;
  const providers = new Set(
    items.value.map((item) => item.provider).filter(Boolean),
  ).size;
  const ready = items.value.filter((item) => Boolean(item.tableName)).length;
  return $t('vectorStores.list.summary', { providers, ready, total });
});

async function loadList() {
  loading.value = true;
  try {
    items.value = await vectorStoreApi.list();
  } finally {
    loading.value = false;
  }
}

function handleCreate() {
  currentItem.value = null;
  showEdit.value = true;
}

function handleEdit(item: AigcVectorStore) {
  currentItem.value = item;
  showEdit.value = true;
}

async function handleDelete(item: AigcVectorStore) {
  if (!item.id) {
    return;
  }
  dialog.warning({
    closable: false,
    content: $t('common.messages.deleteConfirmContent', {
      name: item.name || $t('vectorStores.card.unnamed'),
    }),
    negativeText: $t('common.actions.cancel'),
    positiveText: $t('common.actions.confirmDelete'),
    title: $t('vectorStores.messages.deleteTitle'),
    onPositiveClick: async () => {
      await vectorStoreApi.remove(item.id!);
      message.success($t('vectorStores.messages.deleted'));
      await loadList();
    },
  });
}

async function handleSave(payload: Partial<AigcVectorStore>) {
  saving.value = true;
  try {
    if (currentItem.value?.id) {
      await vectorStoreApi.update(currentItem.value.id, payload);
      message.success($t('vectorStores.messages.updated'));
    } else {
      await vectorStoreApi.create(payload);
      message.success($t('vectorStores.messages.created'));
    }
    showEdit.value = false;
    await loadList();
  } finally {
    saving.value = false;
  }
}

onMounted(loadList);
</script>

<template>
  <Page
    content-class="p-4"
    :description="summaryText"
    header-class="px-4 py-3.5"
    :title="$t('menu.dynamic.vector_stores')"
  >
    <template #extra>
      <div class="flex items-center gap-2">
        <NButton secondary @click="loadList">
          <template #icon>
            <RefreshCcw class="size-3.5" />
          </template>
          {{ $t('common.actions.refreshList') }}
        </NButton>
        <NButton type="primary" @click="handleCreate">
          <template #icon>
            <Plus class="size-3.5" />
          </template>
          {{ $t('vectorStores.actions.create') }}
        </NButton>
      </div>
    </template>

    <div class="flex flex-col gap-3">
      <!-- 供应商筛选 / 搜索工具条 -->
      <div
        class="flex flex-col gap-2.5 rounded-lg border border-border bg-card px-3 py-2.5 shadow-[var(--shadow-card)] lg:flex-row lg:items-center lg:justify-between"
      >
        <div class="flex min-w-0 flex-wrap items-center gap-1.5">
          <NTag
            v-for="(option, index) in providerTagOptions"
            :key="option.value"
            :bordered="selectedProvider !== option.value"
            :type="selectedProvider === option.value ? 'primary' : 'default'"
            class="c-filter-tag cursor-pointer"
            @click="selectedProvider = String(option.value)"
          >
            <span class="inline-flex items-center gap-1">
              <LayoutGrid v-if="index === 0" class="size-3" />
              <Tag v-else class="size-3" />
              <span>{{ option.label }}</span>
            </span>
          </NTag>
        </div>
        <div class="w-full shrink-0 lg:w-72">
          <NInput
            v-model:value="keyword"
            clearable
            :placeholder="$t('vectorStores.list.searchPlaceholder')"
          >
            <template #prefix>
              <Search class="size-4 text-muted-foreground" />
            </template>
          </NInput>
        </div>
      </div>

      <!-- 向量库卡片网格 -->
      <div
        v-if="loading"
        class="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4"
      >
        <LcCard v-for="index in 6" :key="index" loading :show-icon="false" />
      </div>
      <div
        v-else-if="pagedItems.length > 0"
        class="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4"
      >
        <VectorStoreCard
          v-for="item in pagedItems"
          :key="item.id"
          :item="item"
          @delete="handleDelete"
          @edit="handleEdit"
        />
      </div>
      <div
        v-else
        class="rounded-lg border border-border bg-card shadow-[var(--shadow-card)]"
      >
        <LcEmptyState
          :description="
            hasActiveFilter
              ? $t('vectorStores.list.searchPlaceholder')
              : $t('vectorStores.list.emptyDescription')
          "
          :icon="Plus"
          :title="$t('common.empty.noData')"
        >
          <NButton v-if="!hasActiveFilter" type="primary" @click="handleCreate">
            <template #icon>
              <Plus class="size-3.5" />
            </template>
            {{ $t('vectorStores.actions.create') }}
          </NButton>
          <NButton
            v-else
            quaternary
            @click="keyword = ''; selectedProvider = 'ALL'"
          >
            {{ $t('common.actions.clearFilter') }}
          </NButton>
        </LcEmptyState>
      </div>

      <!-- 分页 -->
      <div
        v-if="filteredItems.length > pageSize"
        class="flex items-center justify-end"
      >
        <NPagination
          v-model:page="currentPage"
          v-model:page-size="pageSize"
          :item-count="filteredItems.length"
          :page-sizes="[6, 9, 12, 18]"
          show-size-picker
        />
      </div>

      <VectorStoreEdit
        v-model:show="showEdit"
        :model-value="currentItem"
        :saving="saving"
        @save="handleSave"
      />
    </div>
  </Page>
</template>

<style scoped>
.c-filter-tag.n-tag {
  padding: 2px 10px;
}

.c-filter-tag.n-tag :deep(.n-tag__content) {
  font-size: 11px;
  font-weight: 500;
}
</style>
