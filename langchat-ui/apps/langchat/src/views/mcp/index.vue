<script lang="ts" setup>
import type { AigcMcp } from '#/api/aigc/mcp';

import { computed, onMounted, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { LayoutGrid, Plus, RefreshCcw, Search, Tag } from '@vben/icons';
import { $t } from '@vben/locales';

import { NButton, NInput, NPagination, NTag } from 'naive-ui';

import { dialog, message } from '#/adapter/naive';
import { mcpApi } from '#/api/aigc/mcp';
import LcCard from '#/components/LcCard/index.vue';
import LcEmptyState from '#/components/LcEmptyState/index.vue';
import { aigcCommonTagOptions } from '#/views/shared/aigc/options';
import { parseTagList } from '#/views/shared/aigc/tags';

import McpCard from './card.vue';
import McpEdit from './edit.vue';

const loading = ref(false);
const saving = ref(false);
const showEdit = ref(false);
const keyword = ref('');
const selectedTag = ref('ALL');
const currentPage = ref(1);
const pageSize = ref(9);
const items = ref<AigcMcp[]>([]);
const currentItem = ref<null | Partial<AigcMcp>>(null);
const tagFilterOptions = computed(() => [
  { label: $t('common.labels.all'), value: 'ALL' },
  ...aigcCommonTagOptions().map((item) => ({
    label: item.label,
    value: String(item.value),
  })),
]);

const filteredItems = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return items.value.filter((item) => {
    const matchKeyword =
      !query ||
      [item.name, item.uuid, item.transport, item.siteUrl, item.tags]
        .map((value) => String(value ?? '').toLowerCase())
        .some((value) => value.includes(query));
    const tags = parseTagList(item.tags);
    const matchTag =
      selectedTag.value === 'ALL' || tags.includes(selectedTag.value);
    return matchKeyword && matchTag;
  });
});

const pagedItems = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredItems.value.slice(start, start + pageSize.value);
});

const hasActiveFilter = computed(
  () => keyword.value.trim() !== '' || selectedTag.value !== 'ALL',
);

watch([keyword, selectedTag], () => {
  currentPage.value = 1;
});

async function loadList() {
  loading.value = true;
  try {
    items.value = await mcpApi.list();
  } finally {
    loading.value = false;
  }
}

function handleCreate() {
  currentItem.value = null;
  showEdit.value = true;
}

function handleEdit(item: AigcMcp) {
  currentItem.value = item;
  showEdit.value = true;
}

async function handleDelete(item: AigcMcp) {
  if (!item.id) {
    return;
  }
  dialog.warning({
    closable: false,
    content: $t('common.messages.deleteConfirmContent', {
      name: item.name || $t('mcp.card.unnamed'),
    }),
    negativeText: $t('common.actions.cancel'),
    positiveText: $t('common.actions.confirmDelete'),
    title: $t('mcp.messages.deleteTitle'),
    onPositiveClick: async () => {
      await mcpApi.remove(item.id!);
      message.success($t('mcp.messages.deleted'));
      await loadList();
    },
  });
}

async function handleSave(payload: Partial<AigcMcp>) {
  saving.value = true;
  try {
    if (currentItem.value?.id) {
      await mcpApi.update(currentItem.value.id, payload);
      message.success($t('mcp.messages.updated'));
    } else {
      await mcpApi.create(payload);
      message.success($t('mcp.messages.created'));
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
    header-class="px-4 py-3.5"
    :title="$t('menu.dynamic.mcp')"
  >
    <template #extra>
      <div class="flex items-center gap-2">
        <NButton secondary @click="loadList">
          <template #icon>
            <RefreshCcw class="size-3.5" />
          </template>
          {{ $t('mcp.actions.refreshList') }}
        </NButton>
        <NButton type="primary" @click="handleCreate">
          <template #icon>
            <Plus class="size-3.5" />
          </template>
          {{ $t('mcp.actions.create') }}
        </NButton>
      </div>
    </template>

    <div class="flex flex-col gap-3">
      <!-- 筛选 / 搜索工具条 -->
      <div
        class="flex flex-col gap-2.5 rounded-lg border border-border bg-card px-3 py-2.5 shadow-[var(--shadow-card)] lg:flex-row lg:items-center lg:justify-between"
      >
        <div class="flex min-w-0 flex-wrap items-center gap-1.5">
          <NTag
            v-for="(option, index) in tagFilterOptions"
            :key="option.value"
            :bordered="selectedTag !== option.value"
            :type="selectedTag === option.value ? 'primary' : 'default'"
            class="c-filter-tag cursor-pointer"
            @click="selectedTag = option.value"
          >
            <span class="inline-flex items-center gap-1">
              <LayoutGrid v-if="index === 0" class="size-3" />
              <Tag v-else class="size-3" />
              <span>{{ option.label }}</span>
            </span>
          </NTag>
        </div>
        <div class="w-full shrink-0 lg:w-80">
          <NInput
            v-model:value="keyword"
            clearable
            :placeholder="$t('mcp.list.searchPlaceholder')"
          >
            <template #prefix>
              <Search class="size-4 text-muted-foreground" />
            </template>
          </NInput>
        </div>
      </div>

      <!-- MCP 服务卡片网格 -->
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
        <McpCard
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
              ? $t('mcp.list.searchPlaceholder')
              : $t('mcp.list.actionsTitle')
          "
          :icon="Plus"
          :title="$t('common.empty.noData')"
        >
          <NButton v-if="!hasActiveFilter" type="primary" @click="handleCreate">
            <template #icon>
              <Plus class="size-3.5" />
            </template>
            {{ $t('mcp.actions.create') }}
          </NButton>
          <NButton v-else quaternary @click="keyword = ''; selectedTag = 'ALL'">
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

      <McpEdit
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
