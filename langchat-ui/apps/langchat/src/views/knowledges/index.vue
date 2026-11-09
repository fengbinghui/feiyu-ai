<script lang="ts" setup>
import type {AigcKnowledge} from '#/api/aigc/knowledge';

import {computed, onMounted, ref, watch} from 'vue';
import {useRouter} from 'vue-router';

import {Page} from '@vben/common-ui';
import {Database, Plus, RefreshCcw} from '@vben/icons';
import {$t} from '@vben/locales';

import {NButton, NPagination} from 'naive-ui';

import {dialog, message} from '#/adapter/naive';
import {indexKnowledgeApi} from '#/api/aigc/docs';
import {knowledgeApi} from '#/api/aigc/knowledge';
import LcListCard from '#/components/LcListCard/index.vue';
import {buildKnowledgeDocsRouteLocation} from '#/views/docs/shared';
import {useAigcLookups} from '#/views/shared/aigc/lookups';
import {aigcCommonTagOptions} from '#/views/shared/aigc/options';
import {parseTagList} from '#/views/shared/aigc/tags';

import KnowledgeCard from './card.vue';
import KnowledgeEdit from './edit.vue';

const router = useRouter();
const { loadLookups, lookups } = useAigcLookups({
  models: true,
  vectorStores: true,
});

const loading = ref(false);
const saving = ref(false);
const showEdit = ref(false);
const keyword = ref('');
const selectedTag = ref('ALL');
const currentPage = ref(1);
const pageSize = ref(6);
const items = ref<AigcKnowledge[]>([]);
const currentItem = ref<null | Partial<AigcKnowledge>>(null);
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
      [item.name, item.description, item.tags]
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

watch([keyword, selectedTag], () => {
  currentPage.value = 1;
});

function jumpToKnowledge(knowledgeId?: string) {
  void router.push(buildKnowledgeDocsRouteLocation(knowledgeId));
}

async function loadList() {
  loading.value = true;
  try {
    items.value = await knowledgeApi.list();
  } finally {
    loading.value = false;
  }
}

async function initializePage() {
  await Promise.all([loadLookups(), loadList()]);
}

function handleCreate() {
  currentItem.value = null;
  showEdit.value = true;
}

function handleEdit(item: AigcKnowledge) {
  currentItem.value = item;
  showEdit.value = true;
}

async function handleDelete(item: AigcKnowledge) {
  if (!item.id) {
    return;
  }
  dialog.warning({
    closable: false,
    content: $t('common.messages.deleteConfirmContent', {
      name: item.name || $t('knowledge.card.unnamed'),
    }),
    negativeText: $t('common.actions.cancel'),
    positiveText: $t('common.actions.confirmDelete'),
    title: $t('knowledge.messages.deleteTitle'),
    onPositiveClick: async () => {
      await knowledgeApi.remove(item.id!);
      message.success($t('knowledge.messages.deleted'));
      await loadList();
    },
  });
}

async function handleSave(payload: Partial<AigcKnowledge>) {
  saving.value = true;
  try {
    if (currentItem.value?.id) {
      await knowledgeApi.update(currentItem.value.id, payload);
      message.success($t('knowledge.messages.updated'));
    } else {
      await knowledgeApi.create(payload);
      message.success($t('knowledge.messages.created'));
    }
    showEdit.value = false;
    await loadList();
  } finally {
    saving.value = false;
  }
}

async function handleIndex(item?: AigcKnowledge | string) {
  const knowledgeId = typeof item === 'string' ? item : item?.id;
  if (!knowledgeId) {
    message.error($t('knowledge.messages.missingId'));
    return;
  }
  await indexKnowledgeApi(knowledgeId, {});
  message.success($t('knowledge.messages.vectorizeSubmitted'));
}

onMounted(initializePage);
</script>

<template>
  <Page>
    <div class="flex flex-col gap-3">
      <!-- 页头：标题 / 一行说明 / 右侧主操作 -->
      <section class="rounded-xl border border-border bg-card px-4 py-3.5 sm:px-5">
        <div
          class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between"
        >
          <div class="flex min-w-0 items-start gap-3">
            <span
              class="flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/15 bg-primary/8 text-primary"
            >
              <Database class="size-4.5" />
            </span>
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-2">
                <h1
                  class="text-xl font-semibold tracking-tight text-foreground"
                >
                  {{ $t('menu.dynamic.knowledges') }}
                </h1>
                <span
                  class="rounded-md bg-muted px-2 py-0.5 text-[10px] font-medium tabular-nums text-muted-foreground"
                >
                  {{ $t('common.labels.count', { count: filteredItems.length }) }}
                </span>
              </div>
              <p class="mt-1 text-sm leading-6 text-muted-foreground">
                {{ $t('knowledge.settings.description') }}
              </p>
            </div>
          </div>

          <div class="flex shrink-0 flex-wrap items-center gap-2">
            <NButton
              v-tippy="$t('common.actions.refreshList')"
              secondary
              @click="initializePage"
            >
              <template #icon>
                <RefreshCcw class="size-4" />
              </template>
              {{ $t('common.actions.refresh') }}
            </NButton>
            <NButton type="primary" @click="handleCreate">
              <template #icon>
                <Plus class="size-4" />
              </template>
              {{ $t('knowledge.actions.create') }}
            </NButton>
          </div>
        </div>
      </section>

      <LcListCard
        :active-tag="selectedTag"
        :items="pagedItems"
        :loading="loading"
        :search-value="keyword"
        :tags="tagFilterOptions"
        :search-placeholder="$t('knowledge.list.searchPlaceholder')"
        @update:active-tag="selectedTag = $event"
        @update:search-value="keyword = $event"
      >
        <template #item="{ item }">
          <KnowledgeCard
            :item="item"
            :model-options="lookups.models"
            :vector-store-options="lookups.vectorStores"
            @delete="handleDelete"
            @docs="jumpToKnowledge(item.id)"
            @edit="handleEdit"
            @index="handleIndex"
          />
        </template>
        <template #empty-extra>
          <NButton type="primary" @click="handleCreate">{{ $t('knowledge.actions.create') }}</NButton>
        </template>
      </LcListCard>

      <div
        v-if="filteredItems.length > pageSize"
        class="flex justify-end rounded-xl border border-border bg-card px-4 py-2.5"
      >
        <NPagination
          v-model:page="currentPage"
          v-model:page-size="pageSize"
          :item-count="filteredItems.length"
          :page-sizes="[6, 9, 12, 18]"
          show-size-picker
        />
      </div>

      <KnowledgeEdit
        v-model:show="showEdit"
        :model-options="lookups.models"
        :model-value="currentItem"
        :saving="saving"
        :vector-store-options="lookups.vectorStores"
        @save="handleSave"
      />
    </div>
  </Page>
</template>

