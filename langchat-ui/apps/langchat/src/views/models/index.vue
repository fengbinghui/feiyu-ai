<script lang="ts" setup>
import type {AigcModel} from '#/api/aigc/model';

import {computed, onMounted, ref, watch} from 'vue';

import {Page} from '@vben/common-ui';
import {
  Bot,
  BotOff,
  Building2,
  Eraser,
  Factory,
  ListFilter,
  Plus,
  RefreshCcw,
  RotateCcw,
  Search,
  Shapes,
} from '@vben/icons';
import { $t } from '@vben/locales';

import {NButton, NInput, NPagination, NSpin} from 'naive-ui';

import {dialog, message} from '#/adapter/naive';
import {modelApi} from '#/api/aigc/model';
import LcEmptyState from '#/components/LcEmptyState/index.vue';
import PageHeader from '#/components/PageHeader/index.vue';

import ModelCard from './card.vue';
import ModelEdit from './edit.vue';
import {
  getAllProviderCapabilities,
  getModelProviderOptions,
  getModelTypeConfigItems,
  getModelTypeIcon,
  getModelTypeLabel,
  getModelTypeMeta,
  getProviderIcon,
  getProviderRecommendedModels,
  getProviderSupportedTypes,
  normalizeModelType,
} from './model-meta';

const loading = ref(false);
const saving = ref(false);
const showEdit = ref(false);
const keyword = ref('');
const selectedProvider = ref('');
const selectedType = ref('');
const currentPage = ref(1);
const pageSize = ref(9);
const items = ref<AigcModel[]>([]);
const currentItem = ref<null | Partial<AigcModel>>(null);

const providerOptions = getModelProviderOptions();

/**
 * 模型类型页签按供应商能力动态派生：
 * 选中供应商时仅展示该供应商已适配的类型，未选中时展示全部供应商能力并集。
 */
const typeOptions = computed(() => {
  const capabilityTypes = selectedProvider.value
    ? getProviderSupportedTypes(selectedProvider.value)
    : getAllProviderCapabilities();
  return capabilityTypes.map((type) => ({
    label: getModelTypeLabel(type),
    value: type,
  }));
});

const providerFilterOptions = computed(() =>
  [
    {
      label: $t('models.list.allProviders'),
      value: '',
    },
    ...providerOptions,
    ...[...new Set(items.value.map((item) => item.provider).filter(Boolean))]
      .filter(
        (provider) =>
          !providerOptions.some((option) => option.value === provider),
      )
      .map((provider) => ({
        label: String(provider),
        value: String(provider),
      })),
  ].map((option) => ({
    ...option,
    count: option.value
      ? items.value.filter((item) => item.provider === option.value).length
      : items.value.length,
    icon:
      option.value === ''
        ? Building2
        : getProviderIcon(String(option.value)) || Bot,
  })),
);

const typeFilterOptions = computed(() => {
  const sorted = [...typeOptions.value].sort((a, b) => {
    if (a.value === 'TEXT2TEXT') return -1;
    if (b.value === 'TEXT2TEXT') return 1;
    return 0;
  });
  return [
    {
      label: $t('common.labels.all'),
      value: '',
      description: $t('models.list.allTypesDescription'),
      icon: Shapes,
    },
    ...sorted.map((option) => ({
      ...option,
      description: getModelTypeMeta(String(option.value)).description,
      icon: getModelTypeIcon(String(option.value ?? '')),
    })),
  ];
});

const normalizedSelectedType = computed(() =>
  selectedType.value ? normalizeModelType(selectedType.value) : '',
);

const filteredItems = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return items.value.filter((item) => {
    const matchProvider =
      !selectedProvider.value || item.provider === selectedProvider.value;
    const matchType =
      !normalizedSelectedType.value ||
      normalizeModelType(item.type) === normalizedSelectedType.value;
    const matchKeyword =
      !query ||
      [item.name, item.model, item.provider, item.type]
        .map((value) => String(value ?? '').toLowerCase())
        .some((value) => value.includes(query));
    return matchProvider && matchType && matchKeyword;
  });
});

const pagedItems = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredItems.value.slice(start, start + pageSize.value);
});

const pageDescription = computed(() => {
  const providerLabel = providerFilterOptions.value.find(
    (item) => item.value === selectedProvider.value,
  )?.label;
  const typeLabel = typeFilterOptions.value.find(
    (item) => item.value === selectedType.value,
  )?.label;
  if (selectedProvider.value && selectedType.value) {
    return $t('models.list.breadcrumbBoth', {
      provider: providerLabel,
      type: typeLabel,
    });
  }
  if (selectedProvider.value) {
    return $t('models.list.breadcrumbProvider', { provider: providerLabel });
  }
  if (selectedType.value) {
    return $t('models.list.breadcrumbType', { type: typeLabel });
  }
  return $t('models.list.breadcrumbAll');
});

const activeProviderMeta = computed(() => {
  const selected = providerFilterOptions.value.find(
    (item) => item.value === selectedProvider.value,
  );
  return {
    icon: selectedProvider.value
      ? getProviderIcon(selectedProvider.value)
      : Building2,
    label: selected?.label || $t('models.list.allProviders'),
  };
});

const activeTypeMeta = computed(() => {
  const selected = typeFilterOptions.value.find(
    (item) => item.value === selectedType.value,
  );
  return {
    icon: selectedType.value ? getModelTypeIcon(selectedType.value) : Shapes,
    label: selected?.label || $t('models.list.allTypes'),
  };
});

const activeTypeConfigItems = computed(() => {
  if (!selectedType.value) {
    return [];
  }
  return getModelTypeConfigItems(selectedType.value);
});

const recommendedModels = computed(() => {
  if (!selectedProvider.value || !selectedType.value) {
    return [];
  }
  return getProviderRecommendedModels(
    selectedProvider.value,
    selectedType.value,
  );
});

watch([keyword, selectedProvider, selectedType], () => {
  currentPage.value = 1;
});

// 切换供应商后，若已选类型不在该供应商能力范围内则重置
watch(selectedProvider, () => {
  const available = typeOptions.value.map((option) => String(option.value));
  if (selectedType.value && !available.includes(selectedType.value)) {
    selectedType.value = "";
  }
});

async function loadList() {
  loading.value = true;
  try {
    items.value = await modelApi.list();
  } finally {
    loading.value = false;
  }
}

function handleCreate() {
  currentItem.value = {
    provider: selectedProvider.value || 'OPENAI',
    type: selectedType.value || 'TEXT2TEXT',
  };
  showEdit.value = true;
}

function handleEdit(item: AigcModel) {
  currentItem.value = item;
  showEdit.value = true;
}

async function handleDelete(item: AigcModel) {
  if (!item.id) {
    return;
  }
  dialog.warning({
    closable: false,
    content: $t('common.messages.deleteConfirmContent', {
      name: item.name || item.model || $t('models.card.unnamed'),
    }),
    negativeText: $t('common.actions.cancel'),
    positiveText: $t('common.actions.confirmDelete'),
    title: $t('models.messages.deleteTitle'),
    onPositiveClick: async () => {
      await modelApi.remove(item.id!);
      message.success($t('models.messages.deleted'));
      await loadList();
    },
  });
}

async function handleSave(payload: Partial<AigcModel>) {
  saving.value = true;
  try {
    if (currentItem.value?.id) {
      await modelApi.update(currentItem.value.id, payload);
      message.success($t('models.messages.updated'));
    } else {
      await modelApi.create(payload);
      message.success($t('models.messages.created'));
    }
    showEdit.value = false;
    await loadList();
  } finally {
    saving.value = false;
  }
}

function resetFilters() {
  keyword.value = '';
  selectedProvider.value = '';
  selectedType.value = '';
}

function handleTypeFilter(type: string) {
  selectedType.value = type ? normalizeModelType(type) : '';
  currentPage.value = 1;
}

onMounted(loadList);
</script>

<template>
  <Page>
    <div class="flex h-full min-h-0 flex-col gap-3">
      <PageHeader
        :description="$t('models.list.description')"
        :title="$t('models.list.title')"
      >
        <template #icon>
          <Bot class="size-5 text-primary" />
        </template>
        <template #actions>
          <NButton secondary @click="loadList">
            <template #icon>
              <RefreshCcw class="size-4" />
            </template>
            {{ $t('common.actions.refresh') }}
          </NButton>
          <NButton type="primary" @click="handleCreate">
            <template #icon>
              <Plus class="size-4" />
            </template>
            {{ $t('models.form.createTitle') }}
          </NButton>
        </template>
      </PageHeader>

      <!-- 搜索筛选工具条 -->
      <div class="lc-surface shrink-0 px-4 py-3">
        <div
          class="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between"
        >
          <div class="flex min-w-0 flex-wrap items-center gap-2">
            <button
              v-for="option in typeFilterOptions"
              :key="String(option.value)"
              :aria-pressed="selectedType === option.value"
              :class="
                selectedType === option.value
                  ? 'border-primary/40 bg-primary/10 text-primary'
                  : 'border-border/80 bg-background text-foreground hover:border-primary/25 hover:bg-primary/5'
              "
              class="flex h-8 cursor-pointer items-center gap-1.5 rounded-md border px-2.5 text-xs font-medium transition-colors"
              type="button"
              @click="handleTypeFilter(String(option.value))"
            >
              <component :is="option.icon" class="size-3.5" />
              <span>{{ option.label }}</span>
            </button>
          </div>

          <div class="flex w-full items-center gap-2 xl:w-auto">
            <NInput
              v-model:value="keyword"
              class="lc-filter-input w-full xl:w-[260px]"
              clearable
              :placeholder="$t('models.list.searchPlaceholder')"
            >
              <template #prefix>
                <Search class="size-4 text-muted-foreground" />
              </template>
            </NInput>
            <NButton secondary @click="resetFilters">
              <template #icon>
                <RotateCcw class="size-4" />
              </template>
              {{ $t('common.actions.reset') }}
            </NButton>
          </div>
        </div>

        <div
          class="mt-2.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-[11px] text-muted-foreground"
        >
          <ListFilter class="size-3.5 shrink-0" />
          <span class="truncate">{{ pageDescription }}</span>
          <span class="h-1 w-1 rounded-full bg-border"></span>
          <span
            class="inline-flex items-center gap-1 rounded-full bg-muted/60 px-2 py-0.5"
          >
            <template v-if="typeof activeProviderMeta.icon === 'string'">
              <img :src="activeProviderMeta.icon" class="size-3" alt="" />
            </template>
            <component
              :is="activeProviderMeta.icon"
              v-else
              class="size-3"
            />
            {{ activeProviderMeta.label }}
          </span>
          <span
            class="inline-flex items-center gap-1 rounded-full bg-muted/60 px-2 py-0.5"
          >
            <component :is="activeTypeMeta.icon" class="size-3" />
            {{ activeTypeMeta.label }}
          </span>
          <span class="ml-auto">
            {{ $t('models.list.total', { count: filteredItems.length }) }}
          </span>
        </div>
      </div>

      <div class="flex min-h-0 flex-1 gap-3">
        <!-- 供应商筛选 -->
        <aside
          class="lc-surface hidden w-[224px] shrink-0 flex-col overflow-y-auto p-3 md:flex"
        >
          <div
            class="mb-2 flex items-center gap-1.5 px-1 text-xs font-semibold text-muted-foreground"
          >
            <Factory class="size-3.5 text-primary" />
            {{ $t('common.labels.provider') }}
          </div>
          <div class="flex flex-col gap-1">
            <button
              v-for="option in providerFilterOptions"
              :key="String(option.value)"
              :class="
                selectedProvider === option.value
                  ? 'bg-primary/10 text-primary'
                  : 'text-foreground hover:bg-muted/60 hover:text-primary'
              "
              class="flex w-full cursor-pointer items-center justify-between gap-2 rounded-md px-2 py-1.5 text-left transition-colors"
              type="button"
              @click="selectedProvider = String(option.value)"
            >
              <span class="flex min-w-0 items-center gap-2">
                <span
                  class="inline-flex size-5 shrink-0 items-center justify-center rounded-md border border-border/70 bg-background"
                >
                  <template v-if="typeof option.icon === 'string'">
                    <img :src="option.icon" class="size-3 shrink-0" alt="" />
                  </template>
                  <component
                    :is="option.icon"
                    v-else
                    class="size-3 shrink-0 text-muted-foreground"
                  />
                </span>
                <span class="truncate text-xs font-medium leading-5">
                  {{ option.label }}
                </span>
              </span>
              <span
                :class="
                  selectedProvider === option.value
                    ? 'bg-primary/10 text-primary'
                    : 'bg-muted text-muted-foreground'
                "
                class="ml-1 rounded-full px-1.5 py-0 text-[10px] font-medium"
              >
                {{ option.count }}
              </span>
            </button>
          </div>
        </aside>

        <section class="flex min-w-0 flex-1 flex-col gap-3">
          <!-- 推荐模型 / 建议配置项 -->
          <div
            v-if="selectedProvider && selectedType"
            class="grid shrink-0 gap-2 lg:grid-cols-2"
          >
            <div class="lc-surface px-3 py-2">
              <div
                class="mb-1 flex items-center gap-1.5 text-[10px] font-medium text-muted-foreground"
              >
                <component :is="activeProviderMeta.icon" class="size-3.5" />
                {{ $t('models.list.recommended') }}
              </div>
              <div class="flex flex-wrap gap-1">
                <span
                  v-for="modelName in recommendedModels"
                  :key="modelName"
                  class="rounded-md border border-border/80 bg-muted/30 px-1.5 py-0.5 text-[10px] text-foreground"
                >
                  {{ modelName }}
                </span>
                <span
                  v-if="recommendedModels.length === 0"
                  class="text-[10px] text-muted-foreground"
                >
                  {{ $t('models.list.noRecommended') }}
                </span>
              </div>
            </div>
            <div class="lc-surface px-3 py-2">
              <div
                class="mb-1 flex items-center gap-1.5 text-[10px] font-medium text-muted-foreground"
              >
                <component :is="activeTypeMeta.icon" class="size-3.5" />
                {{ $t('models.list.suggestedConfig') }}
              </div>
              <div class="flex flex-wrap gap-1">
                <span
                  v-for="configName in activeTypeConfigItems"
                  :key="configName"
                  class="rounded-md border border-border/80 bg-muted/30 px-1.5 py-0.5 text-[10px] text-foreground"
                >
                  {{ configName }}
                </span>
              </div>
            </div>
          </div>

          <!-- 模型卡片列表 -->
          <div class="lc-surface flex min-h-0 flex-1 flex-col overflow-hidden">
            <div class="min-h-0 flex-1 overflow-y-auto p-3">
              <NSpin :show="loading" class="block w-full">
                <div
                  v-if="pagedItems.length > 0"
                  class="grid gap-3 md:grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4"
                >
                  <ModelCard
                    v-for="item in pagedItems"
                    :key="item.id || item.model"
                    :item="item"
                    @delete="handleDelete"
                    @edit="handleEdit"
                  />
                </div>
                <LcEmptyState
                  v-else
                  :description="$t('models.list.empty')"
                  :icon="BotOff"
                  :title="$t('common.empty.noData')"
                >
                  <NButton secondary @click="resetFilters">
                    <template #icon>
                      <Eraser class="size-4" />
                    </template>
                    {{ $t('common.actions.clearFilter') }}
                  </NButton>
                </LcEmptyState>
              </NSpin>
            </div>

            <div
              v-if="filteredItems.length > pageSize"
              class="flex shrink-0 justify-end border-t border-border/60 px-3 py-2"
            >
              <NPagination
                v-model:page="currentPage"
                v-model:page-size="pageSize"
                :item-count="filteredItems.length"
                :page-sizes="[6, 9, 12, 18]"
                show-size-picker
              />
            </div>
          </div>
        </section>
      </div>

      <ModelEdit
        v-model:show="showEdit"
        :model-value="currentItem"
        :saving="saving"
        @save="handleSave"
      />
    </div>
  </Page>
</template>
