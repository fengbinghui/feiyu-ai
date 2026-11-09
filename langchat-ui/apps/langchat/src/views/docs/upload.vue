<script lang="ts" setup>
import type {SelectMixedOption} from 'naive-ui/es/select/src/interface';

import type {AigcDocs} from '#/api/aigc/docs';

import {computed, onMounted, ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';

import {Page} from '@vben/common-ui';
import {ArrowLeft, ArrowRight, Check, FileUp, Files, Upload} from '@vben/icons';
import {$t} from '@vben/locales';

import {NButton, NInput, NInputNumber, NSelect, NSwitch} from 'naive-ui';

import {message} from '#/adapter/naive';
import {indexKnowledgeApi, uploadDocsApi} from '#/api/aigc/docs';
import {useAigcLookups} from '#/views/shared/aigc/lookups';
import {docTypeOptions} from '#/views/shared/aigc/options';

import {
  buildDocsIngestionConfig,
  buildKnowledgeDocsRouteLocation,
  buildKnowledgePreviewRouteLocation,
  buildKnowledgeUploadRouteLocation,
  docParseModeOptions,
  formatDocsFileSize,
  normalizeRouteParam,
  removeDocsFileExtension,
  resolveDocsTypeByFilename,
} from './shared';

type QueueStatus = 'failed' | 'pending' | 'uploaded' | 'uploading';

interface UploadQueueItem {
  error?: string;
  file: File;
  name: string;
  sourceKey: string;
  status: QueueStatus;
  type: string;
  uid: string;
  uploadedDoc?: AigcDocs;
}

const route = useRoute();
const router = useRouter();
const { lookups, loadLookups } = useAigcLookups({
  knowledges: true,
});
const isCurrentUploadRoute = computed(
  () => route.name === 'KnowledgeUpload',
);
const routeKnowledgeId = computed(() =>
  normalizeRouteParam(route.query.knowledgeId),
);

const fileInputRef = ref<HTMLInputElement | null>(null);
const currentStep = ref(1);
const dragActive = ref(false);
const submitting = ref(false);
const queueItems = ref<UploadQueueItem[]>([]);

const uploadForm = ref({
  autoIndex: true,
  chunkSize: 800,
  enabled: true,
  knowledgeId: '',
  openPreviewAfterUpload: true,
  overlapSize: 120,
  parseMode: 'BUILTIN',
  type: '',
});

const parseModeOptions = computed<SelectMixedOption[]>(() =>
  docParseModeOptions().map((item) => ({
    label: item.label,
    value: item.value,
  })),
);

const currentParseModeDescription = computed(
  () =>
    docParseModeOptions().find(
      (item) => item.value === uploadForm.value.parseMode,
    )?.description ?? '',
);

const typeOptions = computed<SelectMixedOption[]>(() => [
  { label: $t('docs.upload.typeAuto'), value: '' },
  ...docTypeOptions().map((item) => ({
    label: item.label,
    value: item.value,
  })),
]);

const knowledgeOptions = computed<SelectMixedOption[]>(
  () => lookups.value.knowledges as SelectMixedOption[],
);

const totalSize = computed(() =>
  queueItems.value.reduce((total, item) => total + item.file.size, 0),
);

const uploadedCount = computed(
  () => queueItems.value.filter((item) => item.status === 'uploaded').length,
);

const failedCount = computed(
  () => queueItems.value.filter((item) => item.status === 'failed').length,
);

const stepDescription = computed(() => {
  switch (currentStep.value) {
    case 1: {
      return $t('docs.upload.stepHint.upload');
    }
    case 2: {
      return $t('docs.upload.stepHint.segment');
    }
    default: {
      return $t('docs.upload.stepHint.execute');
    }
  }
});

/** 顶部步骤条数据（视觉层，不参与流程控制） */
const stepItems = computed(() => [
  {
    description: $t('docs.upload.stepUploadDescription'),
    title: $t('docs.upload.stepUpload'),
  },
  {
    description: $t('docs.upload.stepSegmentDescription'),
    title: $t('docs.upload.stepSegment'),
  },
  {
    description: $t('docs.upload.stepExecuteDescription'),
    title: $t('docs.upload.stepExecute'),
  },
]);

watch(
  routeKnowledgeId,
  (value) => {
    if (!isCurrentUploadRoute.value) {
      return;
    }
    if (!value) {
      void router.replace('/knowledges');
      return;
    }
    if (value !== uploadForm.value.knowledgeId) {
      uploadForm.value.knowledgeId = value;
    }
  },
  { immediate: true },
);

watch(
  () => lookups.value.knowledges,
  (knowledgeOptions) => {
    if (!uploadForm.value.knowledgeId && knowledgeOptions[0]) {
      uploadForm.value.knowledgeId = String(knowledgeOptions[0].value);
    }
  },
  { immediate: true },
);

watch(
  () => uploadForm.value.knowledgeId,
  (knowledgeId) => {
    if (!isCurrentUploadRoute.value) {
      return;
    }
    if (knowledgeId && knowledgeId !== routeKnowledgeId.value) {
      void router.replace(buildKnowledgeUploadRouteLocation(knowledgeId));
    }
  },
);

onMounted(loadLookups);

function openFileDialog() {
  fileInputRef.value?.click();
}

function handleNativeFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  addFiles([...input.files ?? []]);
  input.value = '';
}

function handleDrop(event: DragEvent) {
  event.preventDefault();
  dragActive.value = false;
  addFiles([...event.dataTransfer?.files ?? []]);
}

function addFiles(files: File[]) {
  const exists = new Set(queueItems.value.map((item) => item.sourceKey));
  const nextItems = files
    .filter((file) => file.size > 0)
    .map((file) => {
      const sourceKey = buildSourceKey(file);
      return {
        file,
        name: removeDocsFileExtension(file.name),
        sourceKey,
        status: 'pending' as QueueStatus,
        type: resolveDocsTypeByFilename(file.name),
        uid: buildUid(sourceKey),
      };
    })
    .filter((item) => !exists.has(item.sourceKey));

  if (nextItems.length === 0 && files.length > 0) {
    message.warning($t('docs.upload.duplicateFile'));
    return;
  }

  queueItems.value = [...queueItems.value, ...nextItems];
}

function removeQueueItem(uid: string) {
  queueItems.value = queueItems.value.filter((item) => item.uid !== uid);
}

function resetFailedItem(item: UploadQueueItem) {
  item.error = undefined;
  item.status = 'pending';
}

function validateCurrentStep(step = currentStep.value) {
  if (step >= 1 && queueItems.value.length === 0) {
    message.error($t('docs.upload.noFiles'));
    return false;
  }
  if (step >= 2) {
    if (!uploadForm.value.knowledgeId) {
      message.error($t('docs.upload.selectKnowledge'));
      return false;
    }
    if (!uploadForm.value.chunkSize || uploadForm.value.chunkSize < 100) {
      message.error($t('docs.upload.chunkSizeTooSmall'));
      return false;
    }
    if (uploadForm.value.overlapSize < 0) {
      message.error($t('docs.upload.overlapInvalid'));
      return false;
    }
  }
  return true;
}

function goNextStep() {
  if (!validateCurrentStep()) {
    return;
  }
  currentStep.value = Math.min(3, currentStep.value + 1);
}

function goPrevStep() {
  currentStep.value = Math.max(1, currentStep.value - 1);
}

async function submitUploadQueue() {
  if (!validateCurrentStep(3)) {
    return;
  }

  submitting.value = true;
  const uploadedDocs: AigcDocs[] = [];
  try {
    for (const item of queueItems.value) {
      if (item.uploadedDoc?.id) {
        uploadedDocs.push(item.uploadedDoc);
        continue;
      }

      resetFailedItem(item);
      item.status = 'uploading';
      try {
        const formData = new FormData();
        formData.append('file', item.file);
        formData.append('knowledgeId', uploadForm.value.knowledgeId);
        formData.append(
          'name',
          item.name || removeDocsFileExtension(item.file.name),
        );
        formData.append('enabled', String(uploadForm.value.enabled));
        formData.append(
          'ingestionConfig',
          buildDocsIngestionConfig(
            uploadForm.value.chunkSize,
            uploadForm.value.overlapSize,
            uploadForm.value.parseMode,
          ),
        );
        if (uploadForm.value.type) {
          formData.append('type', uploadForm.value.type);
        }

        const uploaded = await uploadDocsApi(formData);
        item.status = 'uploaded';
        item.uploadedDoc = uploaded;
        uploadedDocs.push(uploaded);
      } catch (error) {
        item.status = 'failed';
        item.error = error instanceof Error ? error.message : $t('docs.upload.uploadFailed');
      }
    }

    if (uploadedDocs.length === 0) {
      message.error($t('docs.upload.noSuccessVectorize'));
      return;
    }

    if (uploadForm.value.autoIndex) {
      await indexKnowledgeApi(uploadForm.value.knowledgeId, {
        chunkSize: uploadForm.value.chunkSize,
        docsIds: uploadedDocs
          .map((item) => item.id)
          .filter((id): id is string => Boolean(id)),
        overlapSize: uploadForm.value.overlapSize,
      });
    }

    const successText = uploadForm.value.autoIndex
      ? $t('docs.upload.uploadedWithVectorize', {
          count: uploadedDocs.length,
        })
      : $t('docs.upload.uploadedOnly', { count: uploadedDocs.length });

    if (failedCount.value > 0) {
      message.warning(
        $t('docs.upload.partialFailed', {
          success: successText,
          failed: failedCount.value,
        }),
      );
      return;
    }

    message.success(successText);

    if (uploadForm.value.openPreviewAfterUpload && uploadedDocs[0]?.id) {
      void router.push(
        buildKnowledgePreviewRouteLocation({
          docsId: uploadedDocs[0].id,
          knowledgeId: uploadForm.value.knowledgeId,
        }),
      );
      return;
    }

    void router.push(
      buildKnowledgeDocsRouteLocation(uploadForm.value.knowledgeId),
    );
  } finally {
    submitting.value = false;
  }
}

function buildSourceKey(file: File) {
  return `${file.name}-${file.size}-${file.lastModified}`;
}

function buildUid(sourceKey: string) {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}-${sourceKey}`;
}
</script>

<template>
  <Page>
    <input
      ref="fileInputRef"
      class="hidden"
      multiple
      type="file"
      @change="handleNativeFileChange"
    />

    <div class="flex h-full min-h-0 flex-col gap-3">
      <!-- 页头 -->
      <section class="rounded-xl border border-border bg-card px-4 py-3.5 sm:px-5">
        <div
          class="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between"
        >
          <div class="flex min-w-0 items-start gap-3">
            <span
              class="flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/15 bg-primary/8 text-primary"
            >
              <Upload class="size-4.5" />
            </span>
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-2">
                <h1 class="text-xl font-semibold tracking-tight text-foreground">
                  {{ $t('docs.title.upload') }}
                </h1>
                <span
                  class="rounded-md bg-muted px-2 py-0.5 text-[10px] font-medium tabular-nums text-muted-foreground"
                >
                  {{
                    $t('docs.upload.stepIndicator', {
                      current: currentStep,
                      total: 3,
                    })
                  }}
                </span>
              </div>
              <p class="mt-1 max-w-3xl text-sm leading-6 text-muted-foreground">
                {{ stepDescription }}
              </p>
            </div>
          </div>

          <div class="flex shrink-0 flex-wrap items-center gap-2">
            <NButton
              secondary
              @click="
                router.push(
                  buildKnowledgeDocsRouteLocation(uploadForm.knowledgeId),
                )
              "
            >
              <template #icon>
                <ArrowLeft class="size-4" />
              </template>
              {{ $t('docs.actions.backToList') }}
            </NButton>
            <NButton secondary @click="openFileDialog">
              <template #icon>
                <Upload class="size-4" />
              </template>
              {{ $t('docs.upload.addMore') }}
            </NButton>
          </div>
        </div>
      </section>

      <!-- 主卡片：步骤条 + 内容 + 底部操作条 -->
      <div
        class="flex min-h-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card"
      >
        <!-- 步骤条 -->
        <div class="shrink-0 border-b border-border/70 px-3 py-3 sm:px-4">
          <ol class="grid gap-2 sm:grid-cols-3">
            <li v-for="(step, index) in stepItems" :key="step.title">
              <div
                :class="
                  currentStep > index + 1
                    ? 'border-primary/25 bg-primary/5'
                    : currentStep === index + 1
                      ? 'border-primary/40 bg-primary/[0.07] shadow-sm'
                      : 'border-border/70 bg-muted/20'
                "
                class="flex h-full items-start gap-2.5 rounded-lg border px-3 py-2.5 transition-colors"
              >
                <span
                  :class="
                    currentStep > index + 1
                      ? 'border-primary bg-primary text-primary-foreground'
                      : currentStep === index + 1
                        ? 'border-primary/50 bg-card text-primary'
                        : 'border-border bg-card text-muted-foreground'
                  "
                  class="mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full border text-[11px] font-semibold tabular-nums"
                >
                  <Check v-if="currentStep > index + 1" class="size-3.5" />
                  <template v-else>{{ index + 1 }}</template>
                </span>
                <div class="min-w-0">
                  <div
                    :class="
                      currentStep >= index + 1
                        ? 'text-foreground'
                        : 'text-muted-foreground'
                    "
                    class="text-xs font-semibold"
                  >
                    {{ step.title }}
                  </div>
                  <div class="mt-0.5 text-[11px] leading-4 text-muted-foreground">
                    {{ step.description }}
                  </div>
                </div>
              </div>
            </li>
          </ol>
        </div>

        <!-- 内容区 -->
        <div class="min-h-0 flex-1 overflow-y-auto px-3 py-3.5 sm:px-4">
          <!-- 第 1 步：整理上传队列 -->
          <div
            v-if="currentStep === 1"
            class="grid gap-3 xl:grid-cols-[minmax(0,1.35fr)_minmax(0,1fr)]"
          >
            <div class="flex min-w-0 flex-col gap-3">
              <div
                :class="
                  dragActive
                    ? 'border-primary bg-primary/5'
                    : 'border-border bg-muted/20 hover:border-primary/50 hover:bg-primary/[0.03]'
                "
                class="flex min-h-[260px] cursor-pointer flex-col items-center justify-center rounded-xl border border-dashed px-6 py-8 text-center transition-colors"
                @click="openFileDialog"
                @drop="handleDrop"
                @dragenter.prevent="dragActive = true"
                @dragleave.prevent="dragActive = false"
                @dragover.prevent
              >
                <div
                  class="flex size-14 items-center justify-center rounded-2xl border border-primary/15 bg-primary/8 text-primary"
                >
                  <FileUp class="size-6" />
                </div>
                <div class="mt-4 text-base font-semibold text-foreground">
                  {{ $t('docs.upload.dragHere') }}
                </div>
                <div
                  class="mt-1.5 max-w-lg text-sm leading-6 text-muted-foreground"
                >
                  {{ $t('docs.upload.supportedFormats') }}
                  {{ $t('docs.upload.supportedFormatsSuffix') }}
                </div>
                <div
                  class="mt-4 flex flex-wrap items-center justify-center gap-2"
                >
                  <span
                    class="rounded-md border border-border/70 bg-card px-2.5 py-1 text-[11px] font-medium text-muted-foreground"
                  >
                    {{
                      $t('docs.upload.selectedCount', {
                        count: queueItems.length,
                      })
                    }}
                  </span>
                  <span
                    class="rounded-md border border-primary/20 bg-primary/5 px-2.5 py-1 text-[11px] font-medium text-primary"
                  >
                    {{
                      $t('docs.upload.totalSize', {
                        size: formatDocsFileSize(totalSize),
                      })
                    }}
                  </span>
                </div>
              </div>

              <div
                class="rounded-xl border border-border/70 bg-muted/20 px-4 py-3"
              >
                <div class="text-xs font-semibold text-foreground">
                  {{ $t('docs.upload.guideTitle') }}
                </div>
                <div class="mt-1.5 space-y-1 text-[11px] leading-5 text-muted-foreground">
                  <div>{{ $t('docs.upload.guideFirst') }}</div>
                  <div>{{ $t('docs.upload.guideSecond') }}</div>
                  <div>{{ $t('docs.upload.guideThird') }}</div>
                </div>
              </div>
            </div>

            <section
              class="flex min-w-0 flex-col rounded-xl border border-border/70 bg-card"
            >
              <div
                class="flex items-start justify-between gap-3 border-b border-border/60 px-4 py-3"
              >
                <div class="min-w-0">
                  <div class="text-sm font-semibold text-foreground">
                    {{ $t('docs.upload.queueTitle') }}
                  </div>
                  <div class="mt-0.5 text-[11px] leading-5 text-muted-foreground">
                    {{
                      $t('docs.upload.queueDescription', {
                        count: queueItems.length,
                      })
                    }}
                  </div>
                </div>
                <span
                  class="inline-flex shrink-0 items-center gap-1 rounded-md bg-muted px-2 py-0.5 text-[10px] font-medium tabular-nums text-muted-foreground"
                >
                  <Files class="size-3" />
                  {{ queueItems.length }}
                </span>
              </div>

              <div v-if="queueItems.length > 0" class="space-y-2 p-3">
                <div
                  v-for="item in queueItems"
                  :key="item.uid"
                  class="rounded-lg border border-border/70 bg-muted/15 px-3 py-2.5 transition-colors hover:border-border"
                >
                  <div
                    class="grid gap-2 md:grid-cols-[minmax(0,1fr)_76px_76px] md:items-center"
                  >
                    <NInput
                      v-model:value="item.name"
                      :placeholder="$t('docs.upload.namePlaceholder')"
                    />
                    <div
                      class="flex h-8 items-center justify-center rounded-md border border-border/70 bg-card px-2 text-[11px] uppercase tracking-wide text-muted-foreground"
                    >
                      {{ item.type }}
                    </div>
                    <div
                      class="flex h-8 items-center justify-center rounded-md border border-border/70 bg-card px-2 text-[11px] tabular-nums text-muted-foreground"
                    >
                      {{ formatDocsFileSize(item.file.size) }}
                    </div>
                  </div>
                  <div
                    class="mt-2 flex items-center justify-between gap-2 whitespace-nowrap"
                  >
                    <div class="min-w-0 truncate text-[11px] text-muted-foreground">
                      {{ item.file.name }}
                    </div>
                    <div class="flex shrink-0 items-center gap-1.5">
                      <span
                        :class="
                          item.status === 'failed'
                            ? 'border-destructive/30 bg-destructive/10 text-destructive'
                            : item.status === 'uploaded'
                              ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                              : item.status === 'uploading'
                                ? 'border-primary/30 bg-primary/10 text-primary'
                                : 'border-border bg-muted/40 text-muted-foreground'
                        "
                        class="inline-flex h-[20px] items-center rounded-[5px] border px-1.5 text-[10px] font-medium"
                      >
                        {{
                          item.status === 'uploaded'
                            ? $t('docs.upload.statusUploaded')
                            : item.status === 'uploading'
                              ? $t('docs.upload.statusUploading')
                              : item.status === 'failed'
                                ? $t('docs.upload.statusFailed')
                                : $t('docs.upload.statusPending')
                        }}
                      </span>
                      <NButton
                        quaternary
                        size="tiny"
                        type="error"
                        @click="removeQueueItem(item.uid)"
                      >
                        {{ $t('docs.upload.remove') }}
                      </NButton>
                    </div>
                  </div>
                  <div v-if="item.error" class="mt-1 text-[11px] text-destructive">
                    {{ item.error }}
                  </div>
                </div>
              </div>

              <div
                v-else
                class="flex flex-1 flex-col items-center justify-center gap-2 px-6 py-12 text-center"
              >
                <div
                  class="flex size-10 items-center justify-center rounded-lg border border-border/70 bg-muted/30 text-muted-foreground/70"
                >
                  <Files class="size-5" />
                </div>
                <div class="text-xs leading-5 text-muted-foreground">
                  {{ $t('docs.upload.queueEmpty') }}
                </div>
              </div>
            </section>
          </div>

          <!-- 第 2 步：分段设置 -->
          <div
            v-else-if="currentStep === 2"
            class="grid gap-3 xl:grid-cols-[minmax(0,1.3fr)_minmax(300px,0.7fr)]"
          >
            <div class="flex min-w-0 flex-col gap-3">
              <section class="rounded-xl border border-border/70 bg-card">
                <div class="border-b border-border/60 px-4 py-3">
                  <h2 class="text-sm font-semibold text-foreground">
                    {{ $t('knowledge.settings.basicTitle') }}
                  </h2>
                  <p class="mt-0.5 text-[11px] leading-5 text-muted-foreground">
                    {{ $t('knowledge.settings.basicDescription') }}
                  </p>
                </div>
                <div class="grid gap-3 p-4 md:grid-cols-2">
                  <div>
                    <div class="mb-1.5 text-xs font-medium text-foreground/80">
                      {{ $t('docs.upload.knowledge') }}
                    </div>
                    <NSelect
                      v-model:value="uploadForm.knowledgeId"
                      :options="knowledgeOptions"
                      :placeholder="$t('docs.upload.knowledgePlaceholder')"
                    />
                  </div>
                  <div>
                    <div class="mb-1.5 text-xs font-medium text-foreground/80">
                      {{ $t('docs.upload.typeOverride') }}
                    </div>
                    <NSelect
                      v-model:value="uploadForm.type"
                      :options="typeOptions"
                      :placeholder="$t('docs.upload.typePlaceholder')"
                    />
                  </div>
                  <div class="md:col-span-2">
                    <div class="mb-1.5 text-xs font-medium text-foreground/80">
                      {{ $t('docs.upload.parserMode') }}
                    </div>
                    <NSelect
                      v-model:value="uploadForm.parseMode"
                      :options="parseModeOptions"
                      :placeholder="$t('docs.upload.parserPlaceholder')"
                    />
                    <div class="mt-1.5 text-[11px] leading-5 text-muted-foreground">
                      {{ currentParseModeDescription }}
                    </div>
                  </div>
                  <div>
                    <div class="mb-1.5 text-xs font-medium text-foreground/80">
                      {{ $t('docs.upload.chunkSize') }}
                    </div>
                    <NInputNumber
                      v-model:value="uploadForm.chunkSize"
                      :min="100"
                      :step="50"
                      class="w-full"
                    />
                  </div>
                  <div>
                    <div class="mb-1.5 text-xs font-medium text-foreground/80">
                      {{ $t('docs.upload.overlapSize') }}
                    </div>
                    <NInputNumber
                      v-model:value="uploadForm.overlapSize"
                      :min="0"
                      :step="20"
                      class="w-full"
                    />
                  </div>
                </div>
              </section>

              <section class="rounded-xl border border-border/70 bg-card">
                <div class="border-b border-border/60 px-4 py-3">
                  <h2 class="text-sm font-semibold text-foreground">
                    {{ $t('docs.upload.executeStrategy') }}
                  </h2>
                  <p class="mt-0.5 text-[11px] leading-5 text-muted-foreground">
                    {{ $t('docs.upload.configPersistHint') }}
                  </p>
                </div>
                <div class="grid gap-3 p-4 md:grid-cols-2">
                  <div
                    class="flex items-center justify-between gap-3 rounded-lg border border-border/70 bg-muted/15 px-3 py-2.5"
                  >
                    <div class="min-w-0">
                      <div class="text-xs font-medium text-foreground">
                        {{ $t('docs.upload.autoVectorize') }}
                      </div>
                      <div class="mt-0.5 text-[11px] leading-5 text-muted-foreground">
                        {{
                          uploadForm.autoIndex
                            ? $t('docs.upload.autoVectorizeOn')
                            : $t('docs.upload.autoVectorizeOff')
                        }}
                      </div>
                    </div>
                    <NSwitch v-model:value="uploadForm.autoIndex" />
                  </div>
                  <div
                    class="flex items-center justify-between gap-3 rounded-lg border border-border/70 bg-muted/15 px-3 py-2.5"
                  >
                    <div class="min-w-0">
                      <div class="text-xs font-medium text-foreground">
                        {{ $t('docs.upload.docEnabled') }}
                      </div>
                      <div class="mt-0.5 text-[11px] leading-5 text-muted-foreground">
                        {{
                          uploadForm.enabled
                            ? $t('docs.upload.docEnabledOn')
                            : $t('docs.upload.docEnabledOff')
                        }}
                      </div>
                    </div>
                    <NSwitch v-model:value="uploadForm.enabled" />
                  </div>
                </div>
              </section>
            </div>

            <aside
              class="rounded-xl border border-dashed border-border bg-muted/20 p-4"
            >
              <div class="text-xs font-semibold text-foreground">
                {{ $t('docs.upload.segmenterGuide') }}
              </div>
              <div
                class="mt-2.5 space-y-2 text-[11px] leading-5 text-muted-foreground"
              >
                <div>{{ $t('docs.upload.chunkSizeHint') }}</div>
                <div>{{ $t('docs.upload.overlapSizeHint') }}</div>
                <div>
                  {{ $t('docs.upload.parserHint') }} docling-serve{{
                    $t('docs.upload.parserHintSuffix')
                  }}
                </div>
                <div>{{ $t('docs.upload.configPersistHint') }}</div>
              </div>
            </aside>
          </div>

          <!-- 第 3 步：确认并执行 -->
          <div
            v-else
            class="grid gap-3 xl:grid-cols-[minmax(0,1.3fr)_minmax(300px,0.7fr)]"
          >
            <div class="flex min-w-0 flex-col gap-3">
              <div class="grid gap-3 sm:grid-cols-3">
                <div
                  class="rounded-xl border border-border/70 bg-muted/20 px-4 py-3"
                >
                  <div
                    class="text-[10px] font-medium uppercase tracking-[0.16em] text-muted-foreground"
                  >
                    {{ $t('docs.upload.summaryDocCount') }}
                  </div>
                  <div class="mt-2 text-2xl font-semibold tabular-nums text-foreground">
                    {{ queueItems.length }}
                  </div>
                </div>
                <div
                  class="rounded-xl border border-border/70 bg-muted/20 px-4 py-3"
                >
                  <div
                    class="text-[10px] font-medium uppercase tracking-[0.16em] text-muted-foreground"
                  >
                    {{ $t('docs.upload.summaryTotalSize') }}
                  </div>
                  <div class="mt-2 text-2xl font-semibold tabular-nums text-primary">
                    {{ formatDocsFileSize(totalSize) }}
                  </div>
                </div>
                <div
                  class="rounded-xl border border-border/70 bg-muted/20 px-4 py-3"
                >
                  <div
                    class="text-[10px] font-medium uppercase tracking-[0.16em] text-muted-foreground"
                  >
                    {{ $t('docs.upload.summaryTargetKnowledge') }}
                  </div>
                  <div
                    class="mt-2 truncate text-sm font-semibold text-foreground"
                  >
                    {{
                      lookups.knowledges.find(
                        (item) => item.value === uploadForm.knowledgeId,
                      )?.label || $t('docs.upload.summaryNotSelected')
                    }}
                  </div>
                </div>
              </div>

              <div class="grid gap-3 lg:grid-cols-2">
                <section class="rounded-xl border border-border/70 bg-card p-4">
                  <div class="text-xs font-semibold text-foreground">
                    {{ $t('docs.upload.segmentConfig') }}
                  </div>
                  <div class="mt-2 space-y-1.5 text-[11px] leading-5 text-muted-foreground">
                    <div>
                      {{
                        $t('docs.upload.chunkSizeSummary', {
                          size: uploadForm.chunkSize,
                        })
                      }}
                    </div>
                    <div>
                      {{
                        $t('docs.upload.overlapSizeSummary', {
                          size: uploadForm.overlapSize,
                        })
                      }}
                    </div>
                    <div>
                      {{
                        $t('docs.upload.typeSummary', {
                          type:
                            uploadForm.type ||
                            $t('docs.upload.typeSummaryDefault'),
                        })
                      }}
                    </div>
                    <div>
                      {{
                        $t('docs.upload.parserSummary', {
                          parser:
                            docParseModeOptions().find(
                              (item) => item.value === uploadForm.parseMode,
                            )?.label || $t('docs.upload.parserSummaryDefault'),
                        })
                      }}
                    </div>
                  </div>
                </section>
                <section class="rounded-xl border border-border/70 bg-card p-4">
                  <div class="text-xs font-semibold text-foreground">
                    {{ $t('docs.upload.executeStrategy') }}
                  </div>
                  <div class="mt-2 space-y-1.5 text-[11px] leading-5 text-muted-foreground">
                    <div>
                      {{
                        $t('docs.upload.autoVectorizeSummary', {
                          value: uploadForm.autoIndex
                            ? $t('common.status.yes')
                            : $t('common.status.no'),
                        })
                      }}
                    </div>
                    <div>
                      {{
                        $t('docs.upload.docEnabledSummary', {
                          value: uploadForm.enabled
                            ? $t('common.status.yes')
                            : $t('common.status.no'),
                        })
                      }}
                    </div>
                    <div>
                      {{
                        $t('docs.upload.openPreviewSummary', {
                          value: uploadForm.openPreviewAfterUpload
                            ? $t('common.status.yes')
                            : $t('common.status.no'),
                        })
                      }}
                    </div>
                  </div>
                </section>
              </div>

              <section
                class="rounded-xl border border-border/70 bg-card p-4"
              >
                <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
                  <div class="text-xs font-semibold text-foreground">
                    {{ $t('docs.upload.queueConfirm') }}
                  </div>
                  <div class="flex items-center gap-1.5">
                    <span
                      class="inline-flex h-[20px] items-center rounded-[5px] border border-emerald-500/30 bg-emerald-500/10 px-1.5 text-[10px] font-medium text-emerald-600 dark:text-emerald-400"
                    >
                      {{
                        $t('docs.upload.uploadedCount', {
                          count: uploadedCount,
                        })
                      }}
                    </span>
                    <span
                      class="inline-flex h-[20px] items-center rounded-[5px] border border-destructive/30 bg-destructive/10 px-1.5 text-[10px] font-medium text-destructive"
                    >
                      {{
                        $t('docs.upload.failedCount', { count: failedCount })
                      }}
                    </span>
                  </div>
                </div>
                <div class="space-y-2">
                  <div
                    v-for="item in queueItems"
                    :key="item.uid"
                    class="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border/70 bg-muted/15 px-3 py-2"
                  >
                    <div class="min-w-0">
                      <div class="truncate text-xs font-medium text-foreground">
                        {{ item.name || removeDocsFileExtension(item.file.name) }}
                      </div>
                      <div class="mt-0.5 text-[11px] text-muted-foreground">
                        {{ item.file.name }} ·
                        {{ formatDocsFileSize(item.file.size) }}
                      </div>
                      <div v-if="item.error" class="mt-1 text-[11px] text-destructive">
                        {{ item.error }}
                      </div>
                    </div>
                    <span
                      :class="
                        item.status === 'uploaded'
                          ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                          : item.status === 'uploading'
                            ? 'border-primary/30 bg-primary/10 text-primary'
                            : item.status === 'failed'
                              ? 'border-destructive/30 bg-destructive/10 text-destructive'
                              : 'border-border bg-muted/40 text-muted-foreground'
                      "
                      class="inline-flex h-[20px] shrink-0 items-center rounded-[5px] border px-1.5 text-[10px] font-medium"
                    >
                      {{
                        item.status === 'uploaded'
                          ? $t('docs.upload.statusUploaded')
                          : item.status === 'uploading'
                            ? $t('docs.upload.statusUploading')
                            : item.status === 'failed'
                              ? $t('docs.upload.statusFailed')
                              : $t('docs.upload.statusPending')
                      }}
                    </span>
                  </div>
                </div>
              </section>
            </div>

            <aside class="flex min-w-0 flex-col gap-3">
              <section class="rounded-xl border border-border/70 bg-card p-4">
                <div class="text-xs font-semibold text-foreground">
                  {{ $t('docs.upload.afterUpload') }}
                </div>
                <div
                  class="mt-2.5 flex items-center justify-between gap-3 rounded-lg border border-border/70 bg-muted/15 px-3 py-2.5"
                >
                  <span class="text-[11px] leading-5 text-muted-foreground">
                    {{ $t('docs.upload.openPreviewHint') }}
                  </span>
                  <NSwitch v-model:value="uploadForm.openPreviewAfterUpload" />
                </div>
              </section>

              <section
                class="rounded-xl border border-dashed border-border bg-muted/20 px-4 py-3"
              >
                <div class="space-y-2 text-[11px] leading-5 text-muted-foreground">
                  <div>{{ $t('docs.upload.stayHint') }}</div>
                  <div>{{ $t('docs.upload.asyncHint') }}</div>
                </div>
              </section>
            </aside>
          </div>
        </div>

        <!-- 底部操作条 -->
        <div
          class="flex shrink-0 flex-wrap items-center justify-between gap-3 border-t border-border/70 bg-muted/20 px-3 py-3 sm:px-4"
        >
          <div class="flex items-center gap-2 text-xs text-muted-foreground">
            <span
              class="inline-flex h-6 min-w-6 items-center justify-center rounded-full bg-primary/10 px-1.5 text-[11px] font-semibold tabular-nums text-primary"
            >
              {{ currentStep }}
            </span>
            <span class="tabular-nums">
              {{ $t('docs.upload.stepIndicator', { current: currentStep, total: 3 }) }}
            </span>
          </div>
          <div class="flex flex-wrap items-center gap-2">
            <NButton
              secondary
              :disabled="currentStep === 1 || submitting"
              @click="goPrevStep"
            >
              {{ $t('docs.upload.prev') }}
            </NButton>
            <NButton
              v-if="currentStep < 3"
              :disabled="submitting"
              type="primary"
              @click="goNextStep"
            >
              <span class="inline-flex items-center gap-1.5">
                {{ $t('docs.upload.next') }}
                <ArrowRight class="size-3.5" />
              </span>
            </NButton>
            <NButton
              v-else
              :loading="submitting"
              type="primary"
              @click="submitUploadQueue"
            >
              {{ $t('docs.upload.confirmUpload') }}
            </NButton>
          </div>
        </div>
      </div>
    </div>
  </Page>
</template>
