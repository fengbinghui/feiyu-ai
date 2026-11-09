<script lang="ts" setup>
import type { AigcDatasource } from '#/api/aigc/datasource';

import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { useDebounceFn } from '@vueuse/core';

import { Page } from '@vben/common-ui';
import { Database, Plus, RefreshCcw, Search } from '@vben/icons';
import { $t } from '@vben/locales';

import { NButton, NInput, NPagination } from 'naive-ui';

import { dialog, message } from '#/adapter/naive';
import { deleteDataSource, pageDataSource } from '#/api/aigc/datasource';
import LcCard from '#/components/LcCard/index.vue';
import LcEmptyState from '#/components/LcEmptyState/index.vue';

import DatasourceCard from './card.vue';
import DatasourceEdit from './edit.vue';

const router = useRouter();

const loading = ref(false);
const saving = ref(false);
const keyword = ref('');
const currentPage = ref(1);
const pageSize = ref(6);
const items = ref<AigcDatasource[]>([]);
const total = ref(0);
const currentItem = ref<null | Partial<AigcDatasource>>(null);
const showEdit = ref(false);

let loadSeq = 0;

/** 服务端分页加载：pageNo/pageSize/keyword 均由后端处理。 */
async function loadPage() {
  const seq = ++loadSeq;
  loading.value = true;
  try {
    const result = await pageDataSource({
      keyword: keyword.value.trim() || undefined,
      pageNo: currentPage.value,
      pageSize: pageSize.value,
    });
    if (seq !== loadSeq) {
      return;
    }
    total.value = Number(result?.total ?? 0);
    items.value = result?.records ?? [];
    // 删除末页最后一条后回退到最后一个有效页
    const maxPage = Math.max(1, Math.ceil(total.value / pageSize.value));
    if (items.value.length === 0 && currentPage.value > maxPage) {
      currentPage.value = maxPage;
      void loadPage();
    }
  } finally {
    if (seq === loadSeq) {
      loading.value = false;
    }
  }
}

const loadPageDebounced = useDebounceFn(() => {
  void loadPage();
}, 300);

function reload(resetPage = true) {
  if (resetPage) {
    currentPage.value = 1;
  }
  void loadPageDebounced();
}

watch(keyword, () => reload());
watch([currentPage, pageSize], () => {
  void loadPageDebounced();
});

const hasActiveFilter = computed(() => keyword.value.trim() !== '');

async function loadList() {
  await loadPage();
}

function openDetail(item: AigcDatasource) {
  if (!item.id) {
    return;
  }
  void router.push(`/datasources/${item.id}/detail`);
}

function openCreate() {
  currentItem.value = null;
  showEdit.value = true;
}

function handleEdit(item: AigcDatasource) {
  currentItem.value = item;
  showEdit.value = true;
}

async function handleRemove(item: AigcDatasource) {
  if (!item.id) {
    return;
  }
  dialog.warning({
    closable: false,
    content: $t('common.messages.deleteConfirmContent', {
      name: item.name || $t('datasource.card.unnamed'),
    }),
    negativeText: $t('common.actions.cancel'),
    positiveText: $t('common.actions.confirmDelete'),
    title: $t('datasource.messages.deleteTitle'),
    onPositiveClick: async () => {
      await deleteDataSource(item.id!);
      message.success($t('datasource.messages.deleted'));
      await loadList();
    },
  });
}

async function handleSave(_payload: Partial<AigcDatasource>) {
  saving.value = true;
  try {
    // 具体保存逻辑由 DatasourceEdit 通过 api 调用，这里只刷新列表
    await loadList();
    showEdit.value = false;
  } finally {
    saving.value = false;
  }
}

async function initializePage() {
  await loadList();
}

onMounted(initializePage);
</script>

<template>
  <Page
    content-class="p-4"
    :description="$t('datasource.list.actionsDescription')"
    header-class="px-4 py-3.5"
    :title="$t('menu.dynamic.datasources')"
  >
    <template #extra>
      <div class="flex items-center gap-2">
        <NButton secondary @click="loadList">
          <template #icon>
            <RefreshCcw class="size-3.5" />
          </template>
          {{ $t('common.actions.refreshList') }}
        </NButton>
        <NButton type="primary" @click="openCreate">
          <template #icon>
            <Plus class="size-3.5" />
          </template>
          {{ $t('datasource.list.create') }}
        </NButton>
      </div>
    </template>

    <div class="flex flex-col gap-3">
      <!-- 搜索工具条 -->
      <div
        class="flex flex-col gap-2.5 rounded-lg border border-border bg-card px-3 py-2.5 shadow-[var(--shadow-card)] lg:flex-row lg:items-center lg:justify-between"
      >
        <div class="flex min-w-0 items-center gap-2 text-xs text-muted-foreground">
          <Database class="size-3.5 shrink-0 text-primary" />
          <span class="truncate">
            {{ $t('common.labels.count', { count: total }) }}
          </span>
        </div>
        <div class="w-full shrink-0 lg:w-72">
          <NInput
            v-model:value="keyword"
            clearable
            :placeholder="$t('datasource.list.searchPlaceholder')"
          >
            <template #prefix>
              <Search class="size-4 text-muted-foreground" />
            </template>
          </NInput>
        </div>
      </div>

      <!-- 数据源卡片网格 -->
      <div
        v-if="loading"
        class="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4"
      >
        <LcCard v-for="index in 6" :key="index" loading :show-icon="false" />
      </div>
      <div
        v-else-if="items.length > 0"
        class="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4"
      >
        <DatasourceCard
          v-for="item in items"
          :key="item.id"
          :item="item"
          @delete="handleRemove"
          @detail="openDetail"
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
              ? $t('datasource.list.searchPlaceholder')
              : $t('datasource.list.emptyHint')
          "
          :icon="Database"
          :title="
            hasActiveFilter
              ? $t('common.empty.noData')
              : $t('datasource.list.emptyDescription')
          "
        >
          <NButton v-if="!hasActiveFilter" type="primary" @click="openCreate">
            <template #icon>
              <Plus class="size-3.5" />
            </template>
            {{ $t('datasource.list.create') }}
          </NButton>
          <NButton v-else quaternary @click="keyword = ''">
            {{ $t('common.actions.clearFilter') }}
          </NButton>
        </LcEmptyState>
      </div>

      <!-- 分页（服务端总数） -->
      <div v-if="total > pageSize" class="flex items-center justify-end">
        <NPagination
          v-model:page="currentPage"
          v-model:page-size="pageSize"
          :item-count="total"
          :page-sizes="[6, 9, 12, 18]"
          show-size-picker
        />
      </div>

      <DatasourceEdit
        v-model:show="showEdit"
        :model-value="currentItem"
        :saving="saving"
        @save="handleSave"
      />
    </div>
  </Page>
</template>
