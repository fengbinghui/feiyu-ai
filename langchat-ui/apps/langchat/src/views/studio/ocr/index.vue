<script lang="ts" setup>
import type { UploadFileInfo } from 'naive-ui';

import type { AigcModel } from '#/api/aigc/model';

import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { Copy, FileSearch, ScanText } from '@vben/icons';
import { $t } from '@vben/locales';

import {
  NButton,
  NForm,
  NFormItem,
  NInput,
  NSpin,
  NUpload,
  NUploadDragger,
} from 'naive-ui';

import {
  ResizableHandle,
  ResizablePanel,
  ResizablePanelGroup,
} from '@vben-core/shadcn-ui';

import { message } from '#/adapter/naive';
import { recognizeImage } from '#/api/aigc/image';
import { modelApi } from '#/api/aigc/model';
import ModelSelector from '#/components/ModelSelector/index.vue';

const loading = ref(false);
const recognizeText = ref('');
const previewUrl = ref('');
const models = ref<AigcModel[]>([]);
const fileList = ref<UploadFileInfo[]>([]);

const form = ref({
  modelId: '',
  prompt: $t('studio.ocr.defaultPrompt'),
});

async function loadModels() {
  models.value = await modelApi.list();
}

function readFileAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.addEventListener('load', () => resolve(String(reader.result || '')));
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

async function handleFileChange(options: { file: UploadFileInfo }) {
  const file = options.file.file;
  if (!file) {
    previewUrl.value = '';
    return;
  }
  previewUrl.value = await readFileAsDataUrl(file);
}

function handleRemove() {
  fileList.value = [];
  previewUrl.value = '';
  recognizeText.value = '';
}

async function handleRecognize() {
  if (!form.value.modelId) {
    message.warning($t('studio.ocr.selectModel'));
    return;
  }
  if (!previewUrl.value) {
    message.warning($t('studio.ocr.uploadFirst'));
    return;
  }
  loading.value = true;
  recognizeText.value = '';
  try {
    const result = await recognizeImage({
      image: previewUrl.value,
      modelId: form.value.modelId,
      prompt: form.value.prompt,
    });
    recognizeText.value = result?.text || '';
    if (!recognizeText.value) {
      message.warning($t('studio.ocr.noTextFound'));
    }
  } catch (error) {
    message.error(
      $t('studio.ocr.recognizeFailed', {
        message: (error as Error)?.message || $t('errors.unknown'),
      }),
    );
  } finally {
    loading.value = false;
  }
}

async function handleCopy() {
  if (!recognizeText.value) {
    return;
  }
  try {
    await navigator.clipboard.writeText(recognizeText.value);
    message.success($t('common.messages.copySuccess'));
  } catch {
    message.error($t('common.messages.copyFailed'));
  }
}

onMounted(loadModels);
</script>

<template>
  <Page
    content-class="p-4"
    :description="$t('studio.ocr.description')"
    header-class="px-4 py-3.5"
    :title="$t('studio.ocr.title')"
  >
    <div class="flex h-full min-h-0 min-w-0 w-full flex-col">
      <ResizablePanelGroup class="min-h-0 flex-1" direction="horizontal">
        <!-- 左侧配置 -->
        <ResizablePanel :default-size="30" :min-size="22">
          <div
            class="flex h-full min-h-0 flex-col rounded-lg border border-border bg-card shadow-[var(--shadow-card)]"
          >
            <div class="min-h-0 flex-1 overflow-y-auto px-4 py-3">
              <NForm label-placement="top">
                <NFormItem :label="$t('studio.ocr.model')">
                  <ModelSelector
                    :allowed-types="['IMAGE2TEXT', 'OCR', 'VISION']"
                    :config="{}"
                    :model-entities="models"
                    :model-id="form.modelId"
                    @update:model-id="form.modelId = $event"
                  />
                </NFormItem>

                <NFormItem :label="$t('studio.ocr.upload')">
                  <NUpload
                    v-model:file-list="fileList"
                    :max="1"
                    :on-remove="handleRemove"
                    :show-file-list="false"
                    accept="image/*"
                    @change="handleFileChange"
                  >
                    <NUploadDragger>
                      <div
                        class="flex flex-col items-center gap-2 py-6 text-muted-foreground"
                      >
                        <FileSearch class="size-10" />
                        <span class="text-sm">{{
                          $t('studio.ocr.dropHere')
                        }}</span>
                        <span class="text-xs">{{
                          $t('studio.ocr.supportedFormats')
                        }}</span>
                      </div>
                    </NUploadDragger>
                  </NUpload>
                  <img
                    v-if="previewUrl"
                    :alt="$t('studio.ocr.previewAlt')"
                    :src="previewUrl"
                    class="mt-2 max-h-56 w-full rounded-lg border border-border object-contain"
                  />
                </NFormItem>

                <NFormItem :label="$t('studio.ocr.prompt')">
                  <NInput
                    v-model:value="form.prompt"
                    :autosize="{ minRows: 2, maxRows: 5 }"
                    type="textarea"
                  />
                </NFormItem>
              </NForm>
            </div>

            <div class="shrink-0 border-t border-border p-4">
              <NButton
                :loading="loading"
                block
                size="large"
                type="primary"
                @click="handleRecognize"
              >
                <template #icon>
                  <ScanText class="size-4" />
                </template>
                {{ $t('studio.ocr.start') }}
              </NButton>
            </div>
          </div>
        </ResizablePanel>

        <ResizableHandle
          class="mx-1.5 w-px transition-colors hover:bg-primary/60"
          with-handle
        />

        <!-- 右侧结果 -->
        <ResizablePanel :default-size="70" :min-size="40">
          <div
            class="flex h-full min-h-[420px] flex-col rounded-lg border border-border bg-card shadow-[var(--shadow-card)]"
          >
            <div
              class="flex shrink-0 items-center justify-between gap-3 border-b border-border px-4 py-3"
            >
              <div class="flex items-center gap-2">
                <span
                  class="flex size-7 items-center justify-center rounded-md bg-primary/10 text-primary"
                >
                  <ScanText class="size-3.5" />
                </span>
                <span class="text-sm font-semibold text-foreground">
                  {{ $t('studio.ocr.resultTitle') }}
                </span>
              </div>
              <NButton
                v-if="recognizeText"
                size="small"
                secondary
                @click="handleCopy"
              >
                <template #icon>
                  <Copy class="size-3.5" />
                </template>
                {{ $t('studio.ocr.copyText') }}
              </NButton>
            </div>

            <div class="min-h-0 flex-1 overflow-y-auto bg-muted/20 p-4">
              <NSpin :show="loading" class="w-full">
                <div
                  v-if="recognizeText"
                  class="whitespace-pre-wrap text-sm leading-relaxed text-foreground"
                >
                  {{ recognizeText }}
                </div>
                <div
                  v-else
                  class="flex min-h-[360px] flex-col items-center justify-center gap-3 px-8 py-12 text-center"
                >
                  <span
                    class="flex size-12 items-center justify-center rounded-lg border border-border/70 bg-card text-muted-foreground/60"
                  >
                    <ScanText class="size-5" />
                  </span>
                  <span class="text-[13px] font-semibold text-foreground/80">
                    {{ $t('studio.ocr.emptyResult') }}
                  </span>
                  <span class="max-w-sm text-xs leading-5 text-muted-foreground">
                    {{ $t('studio.ocr.supportedFormats') }}
                  </span>
                </div>
              </NSpin>
            </div>
          </div>
        </ResizablePanel>
      </ResizablePanelGroup>
    </div>
  </Page>
</template>
