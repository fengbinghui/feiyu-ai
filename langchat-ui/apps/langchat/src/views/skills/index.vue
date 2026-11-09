<script lang="ts" setup>
import type { UploadFileInfo } from 'naive-ui';

import type { AigcSkill } from '#/api/aigc/skill';

import { computed, onMounted, ref, watch } from 'vue';

import { useDebounceFn } from '@vueuse/core';

import { Page, useVbenModal } from '@vben/common-ui';
import {
  FolderUp,
  LayoutGrid,
  PackageOpen,
  RefreshCcw,
  Search,
  Tag,
} from '@vben/icons';
import { $t } from '@vben/locales';

import {
  NAlert,
  NButton,
  NInput,
  NPagination,
  NRadioButton,
  NRadioGroup,
  NTag,
  NUpload,
  NUploadDragger,
} from 'naive-ui';

import { dialog, message } from '#/adapter/naive';
import { skillApi } from '#/api/aigc/skill';
import LcCard from '#/components/LcCard/index.vue';
import LcEmptyState from '#/components/LcEmptyState/index.vue';
import { aigcCommonTagOptions } from '#/views/shared/aigc/options';
import { stringifyTagList } from '#/views/shared/aigc/tags';

import SkillCard from './card.vue';
import SkillEdit from './edit.vue';

const loading = ref(false);
const showEdit = ref(false);
const uploadMode = ref<'folder' | 'zip'>('zip');
const zipFileList = ref<UploadFileInfo[]>([]);
const folderFileList = ref<UploadFileInfo[]>([]);
const uploadTags = ref<string[]>([]);
const keyword = ref('');
const selectedTag = ref('ALL');
const currentPage = ref(1);
const pageSize = ref(9);
const items = ref<AigcSkill[]>([]);
const total = ref(0);
const currentSkill = ref<null | Partial<AigcSkill>>(null);

const tagOptions = computed(() =>
  aigcCommonTagOptions().map((item) => ({
    label: item.label,
    value: String(item.value),
  })),
);

const tagFilterOptions = computed(() => [
  { label: $t('common.labels.all'), value: 'ALL' },
  ...tagOptions.value,
]);

let loadSeq = 0;

/** 服务端分页加载：pageNo/pageSize/keyword/tags 均由后端处理。 */
async function loadPage() {
  const seq = ++loadSeq;
  loading.value = true;
  try {
    const result = await skillApi.page({
      keyword: keyword.value.trim() || undefined,
      pageNo: currentPage.value,
      pageSize: pageSize.value,
      tags: selectedTag.value === 'ALL' ? undefined : selectedTag.value,
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
watch(selectedTag, () => reload());
watch([currentPage, pageSize], () => {
  void loadPageDebounced();
});

const uploadReady = computed(() =>
  uploadMode.value === 'zip'
    ? zipFileList.value.length > 0
    : folderFileList.value.length > 0,
);

const hasActiveFilter = computed(
  () => keyword.value.trim() !== '' || selectedTag.value !== 'ALL',
);

const [UploadModal, uploadModalApi] = useVbenModal({
  class: 'w-[640px]',
  confirmDisabled: true,
  onConfirm: handleUpload,
});

watch(
  () => $t('skills.list.startUpload'),
  (value) => {
    uploadModalApi.setState({ confirmText: value });
  },
  { immediate: true },
);

watch(
  () => $t('skills.list.upload'),
  (value) => {
    uploadModalApi.setState({ title: value });
  },
  { immediate: true },
);

async function loadList() {
  await loadPage();
}

function openUpload() {
  uploadMode.value = 'zip';
  zipFileList.value = [];
  folderFileList.value = [];
  uploadModalApi.setState({ confirmDisabled: true });
  uploadModalApi.open();
}

function resolveRelativePath(option: UploadFileInfo) {
  const rawFile = option.file as undefined | { webkitRelativePath?: string };
  return rawFile?.webkitRelativePath || option.fullPath || option.name;
}

async function handleUpload() {
  uploadModalApi.setState({ confirmLoading: true });
  try {
    const tags = stringifyTagList(uploadTags.value);
    let skill: AigcSkill;
    if (uploadMode.value === 'zip') {
      const file = zipFileList.value[0]?.file;
      if (!file) {
        message.warning($t('skills.messages.zipRequired'));
        return;
      }
      const formData = new FormData();
      formData.append('file', file);
      if (tags) {
        formData.append('tags', tags);
      }
      skill = await skillApi.uploadPackage(formData);
    } else {
      const entries = folderFileList.value
        .map((option) => {
          const file = option.file;
          if (!file) {
            return null;
          }
          return { file, path: resolveRelativePath(option) };
        })
        .filter((entry): entry is { file: File; path: string } => !!entry);
      if (entries.length === 0) {
        message.warning($t('skills.messages.folderRequired'));
        return;
      }
      const formData = new FormData();
      entries.forEach((entry) => {
        formData.append('files', entry.file);
        formData.append('paths', entry.path);
      });
      if (tags) {
        formData.append('tags', tags);
      }
      skill = await skillApi.uploadFolder(formData);
    }
    message.success(
      $t('skills.messages.uploaded', { name: skill.title || skill.name }),
    );
    uploadModalApi.close();
    await loadList();
  } finally {
    uploadModalApi.setState({ confirmLoading: false });
  }
}

watch(uploadReady, (ready) => {
  uploadModalApi.setState({ confirmDisabled: !ready });
});

function handleEdit(item: AigcSkill) {
  currentSkill.value = item;
  showEdit.value = true;
}

function handleToggle(item: AigcSkill) {
  if (!item.id) {
    return;
  }
  const nextEnabled = !(item.enabled ?? false);
  const name = item.title || item.name;
  dialog.warning({
    closable: false,
    content: nextEnabled
      ? $t('skills.messages.enableConfirm', { name })
      : $t('skills.messages.disableConfirm', { name }),
    negativeText: $t('common.actions.cancel'),
    positiveText: $t('common.actions.confirm'),
    title: nextEnabled
      ? $t('skills.messages.enableTitle')
      : $t('skills.messages.disableTitle'),
    onPositiveClick: async () => {
      await skillApi.update(item.id!, { enabled: nextEnabled });
      message.success(
        nextEnabled
          ? $t('skills.messages.enabled')
          : $t('skills.messages.disabled'),
      );
      await loadList();
    },
  });
}

async function handleDelete(item: AigcSkill) {
  if (!item.id) {
    return;
  }
  dialog.warning({
    closable: false,
    content: $t('skills.messages.deleteConfirm', {
      name: item.title || item.name,
    }),
    negativeText: $t('common.actions.cancel'),
    positiveText: $t('common.actions.confirmDelete'),
    title: $t('skills.messages.deleteTitle'),
    onPositiveClick: async () => {
      await skillApi.remove(item.id!);
      message.success($t('skills.messages.deleted'));
      await loadList();
    },
  });
}

function handleSaved(updated: AigcSkill) {
  if (currentSkill.value) {
    Object.assign(currentSkill.value, updated);
  }
}

onMounted(loadList);
</script>

<template>
  <Page
    content-class="p-4"
    :description="$t('skills.list.actionsDescription')"
    header-class="px-4 py-3.5"
    :title="$t('menu.dynamic.skills')"
  >
    <template #extra>
      <div class="flex items-center gap-2">
        <NButton secondary @click="loadList">
          <template #icon>
            <RefreshCcw class="size-3.5" />
          </template>
          {{ $t('common.actions.refresh') }}
        </NButton>
        <NButton type="primary" @click="openUpload">
          <template #icon>
            <PackageOpen class="size-3.5" />
          </template>
          {{ $t('skills.list.upload') }}
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
        <div class="w-full shrink-0 lg:w-72">
          <NInput
            v-model:value="keyword"
            clearable
            :placeholder="$t('skills.list.searchPlaceholder')"
          >
            <template #prefix>
              <Search class="size-4 text-muted-foreground" />
            </template>
          </NInput>
        </div>
      </div>

      <!-- 技能卡片网格 -->
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
        <SkillCard
          v-for="item in items"
          :key="item.id"
          :item="item"
          @delete="handleDelete"
          @edit="handleEdit"
          @toggle="handleToggle"
        />
      </div>
      <div
        v-else
        class="rounded-lg border border-border bg-card shadow-[var(--shadow-card)]"
      >
        <LcEmptyState
          :description="
            hasActiveFilter
              ? $t('skills.list.searchPlaceholder')
              : $t('skills.list.zipDropHint')
          "
          :icon="PackageOpen"
          :title="$t('common.empty.noData')"
        >
          <NButton v-if="!hasActiveFilter" type="primary" @click="openUpload">
            <template #icon>
              <PackageOpen class="size-3.5" />
            </template>
            {{ $t('skills.list.upload') }}
          </NButton>
          <NButton v-else quaternary @click="keyword = ''; selectedTag = 'ALL'">
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

      <SkillEdit
        v-model:show="showEdit"
        :skill="currentSkill"
        @saved="handleSaved"
      />
    </div>

    <UploadModal>
      <div class="flex flex-col gap-3">
        <NAlert
          :show-icon="true"
          :title="$t('skills.list.formatTitle')"
          type="info"
        >
          <div class="flex flex-col gap-1 text-xs leading-5">
            <span>
              {{ $t('skills.list.formatFirstPre') }}<b>SKILL.md</b>{{
                $t('skills.list.formatFirstPost')
              }}<code>name</code>{{ $t('skills.list.formatFirstNameHint')
              }}<code>description</code
              >{{ $t('skills.list.formatFirstDescHint')
              }}<code>version</code>{{ $t('skills.list.formatFirstVersionHint') }}
            </span>
            <span>
              {{ $t('skills.list.formatSecondPre') }}<code>scripts/</code>{{
                $t('skills.list.formatSecondScriptsHint')
              }}<code>references/</code
              >{{ $t('skills.list.formatSecondRefsHint')
              }}<code>assets/</code>{{ $t('skills.list.formatSecondAssetsHint') }}
            </span>
            <span>{{ $t('skills.list.formatThird') }}</span>
            <span>{{ $t('skills.list.formatFourth') }}</span>
          </div>
        </NAlert>

        <NRadioGroup v-model:value="uploadMode">
          <NRadioButton value="zip">
            {{ $t('skills.list.zipMode') }}
          </NRadioButton>
          <NRadioButton value="folder">
            {{ $t('skills.list.folderMode') }}
          </NRadioButton>
        </NRadioGroup>

        <NUpload
          v-if="uploadMode === 'zip'"
          v-model:file-list="zipFileList"
          accept=".zip"
          :default-upload="false"
          :max="1"
        >
          <NUploadDragger>
            <div class="flex flex-col items-center gap-1 py-4">
              <PackageOpen class="size-8 text-primary" />
              <span class="text-sm">{{ $t('skills.list.zipDropTitle') }}</span>
              <span class="text-[11px] text-muted-foreground">
                {{ $t('skills.list.zipDropHint') }}
              </span>
            </div>
          </NUploadDragger>
        </NUpload>

        <NUpload
          v-else
          v-model:file-list="folderFileList"
          :default-upload="false"
          :directory="true"
          :directory-dnd="true"
          multiple
        >
          <NUploadDragger>
            <div class="flex flex-col items-center gap-1 py-4">
              <FolderUp class="size-8 text-primary" />
              <span class="text-sm">{{
                $t('skills.list.folderDropTitle')
              }}</span>
              <span class="text-[11px] text-muted-foreground">
                {{ $t('skills.list.folderDropHint') }}
              </span>
            </div>
          </NUploadDragger>
        </NUpload>

        <div class="flex items-center gap-2">
          <span class="text-xs text-muted-foreground">
            {{
              $t('skills.list.selectedFiles', {
                count:
                  uploadMode === 'zip'
                    ? zipFileList.length
                    : folderFileList.length,
              })
            }}
          </span>
        </div>
      </div>
    </UploadModal>
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
