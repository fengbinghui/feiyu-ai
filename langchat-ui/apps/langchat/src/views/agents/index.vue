<script lang="ts" setup>
import type {AigcAgent} from '#/api/aigc/agent';

import {computed, markRaw, onBeforeUnmount, onMounted, reactive, ref, watch} from 'vue';
import {useRouter} from 'vue-router';

import {Page} from '@vben/common-ui';
import {Plus, RefreshCcw} from '@vben/icons';
import {$t} from '@vben/locales';

import {useVbenModal} from '@vben-core/popup-ui';

import {NButton, NPagination} from 'naive-ui';

import {useVbenForm, type VbenFormSchema} from '#/adapter/form';
import {dialog, message} from '#/adapter/naive';
import {agentApi} from '#/api/aigc/agent';
import LcListCard from '#/components/LcListCard/index.vue';
import LcIcon from '#/components/LcIcon/index.vue';
import ModelSelector from '#/components/ModelSelector/index.vue';
import PageHeader from '#/components/PageHeader/index.vue';
import {useAigcLookups} from '#/views/shared/aigc/lookups';
import {aigcCommonTagOptions} from '#/views/shared/aigc/options';
import {stringifyTagList} from '#/views/shared/aigc/tags';

import AgentCard from './card.vue';
import AgentEdit from './edit.vue';

const { loadLookups, lookups } = useAigcLookups({
  knowledges: true,
  models: true,
  skills: true,
});

const router = useRouter();

const loading = ref(false);
const saving = ref(false);
const creating = ref(false);
const showEdit = ref(false);
const keyword = ref('');
const appliedKeyword = ref('');
const statusFilter = ref('ALL');
const currentPage = ref(1);
const pageSize = ref(6);
const total = ref(0);
const items = ref<AigcAgent[]>([]);
const currentItem = ref<null | Partial<AigcAgent>>(null);
const LcIconEditor = markRaw(LcIcon);
const ModelSelectorEditor = markRaw(ModelSelector);
const currentBaseConfigAgent = ref<AigcAgent | null>(null);

/** 状态快捷筛选（服务端 /page 支持 status），替代原先依赖全量数据的标签筛选 */
const statusFilterOptions = computed(() => [
  { label: $t('common.labels.all'), value: 'ALL' },
  { label: $t('agents.status.draft'), value: 'DRAFT' },
  { label: $t('agents.status.published'), value: 'PUBLISHED' },
  { label: $t('agents.status.disabled'), value: 'DISABLED' },
]);

function handleStatusFilterChange(value: string) {
  statusFilter.value = value;
  if (currentPage.value === 1) {
    void loadPage();
  } else {
    currentPage.value = 1;
  }
}

// 搜索输入 → 已生效关键字（防抖后按服务端分页重新查询）
let searchTimer: ReturnType<typeof setTimeout> | undefined;
watch(keyword, (value) => {
  if (searchTimer !== undefined) {
    clearTimeout(searchTimer);
  }
  searchTimer = setTimeout(() => {
    appliedKeyword.value = value.trim();
    if (currentPage.value === 1) {
      void loadPage();
    } else {
      // 重置到第一页并交由分页 watch 触发查询，避免重复请求
      currentPage.value = 1;
    }
  }, 300);
});

watch([currentPage, pageSize], () => {
  void loadPage();
});

onBeforeUnmount(() => {
  if (searchTimer !== undefined) {
    clearTimeout(searchTimer);
  }
});
const [BaseConfigModal, baseConfigModalApi] = useVbenModal({
  onCancel() {
    baseConfigModalApi.close();
  },
});

watch(
  () => $t('agents.list.baseConfigTitle'),
  (value) => {
    baseConfigModalApi.setState({ title: value });
  },
  { immediate: true },
);

const baseConfigSchema = computed<VbenFormSchema[]>(() => [
  {
    component: 'Input',
    fieldName: 'agentName',
    label: $t('agents.form.appName'),
    rules: 'required',
  },
  {
    component: LcIconEditor,
    componentProps: {
      editable: true,
      fallbackIcon: 'lucide:bot',
      showSvgTab: false,
      showUrlTab: false,
      size: 64,
    },
    fieldName: 'icon',
    label: $t('agents.form.appIcon'),
    modelPropName: 'modelValue',
  },
  {
    component: 'Input',
    componentProps: {
      autosize: { minRows: 2, maxRows: 6 },
      placeholder: $t('agents.form.appDescriptionPlaceholder'),
      type: 'textarea',
    },
    fieldName: 'description',
    label: $t('agents.form.appDescription'),
  },
]);
const [BaseConfigForm, baseConfigFormApi] = useVbenForm({
  layout: 'vertical',
  schema: baseConfigSchema.value,
  showDefaultActions: false,
  wrapperClass: 'grid-cols-1',
});

watch(
  () => baseConfigSchema.value,
  (schema) => {
    baseConfigFormApi.setState({ schema });
  },
  { immediate: true },
);

// 新建智能体弹窗：仅包含模型、名称、描述、标签等基础配置，创建后跳转编辑页
const [CreateModal, createModalApi] = useVbenModal({
  onCancel() {
    createModalApi.close();
  },
});

watch(
  () => $t('agents.title.create'),
  (value) => {
    createModalApi.setState({ title: value });
  },
  { immediate: true },
);

const createModelConfig = reactive({
  maxOutputTokens: 2048,
  temperature: 0.7,
  topP: 0.9,
});
const createFormSchema = computed<VbenFormSchema[]>(() => [
  {
    component: 'Input',
    componentProps: {
      placeholder: $t('agents.form.appNamePlaceholder'),
    },
    fieldName: 'agentName',
    label: $t('agents.form.appName'),
    rules: 'required',
  },
  {
    component: ModelSelectorEditor,
    componentProps: {
      config: createModelConfig,
      'onUpdate:config': (value: Record<string, any>) =>
        Object.assign(createModelConfig, value),
      modelEntities: lookups.value.modelEntities,
    },
    fieldName: 'reasoningModelId',
    label: $t('agents.form.selectModel'),
    modelPropName: 'modelId',
    rules: 'selectRequired',
  },
  {
    component: 'Input',
    componentProps: {
      autosize: { minRows: 2, maxRows: 6 },
      placeholder: $t('agents.form.appDescriptionPlaceholder'),
      type: 'textarea',
    },
    fieldName: 'description',
    label: $t('agents.form.appDescription'),
  },
  {
    component: 'Select',
    componentProps: {
      clearable: true,
      multiple: true,
      options: aigcCommonTagOptions(),
      placeholder: $t('common.placeholder.selectTags'),
    },
    fieldName: 'tags',
    label: $t('common.labels.tags'),
  },
]);
const [CreateForm, createFormApi] = useVbenForm({
  layout: 'vertical',
  schema: createFormSchema.value,
  showDefaultActions: false,
  wrapperClass: 'grid-cols-1',
});

watch(
  () => createFormSchema.value,
  (schema) => {
    createFormApi.setState({ schema });
  },
  { immediate: true },
);

watch(
  () => $t('common.actions.create'),
  (value) => {
    createModalApi.setState({ confirmText: value });
  },
  { immediate: true },
);

async function loadPage() {
  loading.value = true;
  try {
    const result = await agentApi.page({
      keyword: appliedKeyword.value || undefined,
      pageNo: currentPage.value,
      pageSize: pageSize.value,
      status: statusFilter.value === 'ALL' ? undefined : statusFilter.value,
    });
    items.value = result.records;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

/** 删除/保存后刷新当前页；若当前页已空则回退一页（由分页 watch 触发重新查询） */
async function reloadCurrentPage() {
  await loadPage();
  if (items.value.length === 0 && currentPage.value > 1) {
    currentPage.value -= 1;
  }
}

function openDetail(item?: AigcAgent | null) {
  if (item?.id) {
    void router.push(`/agents/${item.id}/builder`);
    return;
  }
  void router.push('/agents/new/builder');
}

function openCreateModal() {
  // 先打开弹窗再重置表单：表单挂在弹窗内，未挂载时调用 resetForm 会一直等待
  createModalApi.open();
  void createFormApi.resetForm();
}

async function handleCreateAgent() {
  const values = await createFormApi.validateAndSubmitForm();
  if (!values) {
    return;
  }
  const payload = values as Record<string, any>;
  const agentName = String(payload.agentName || '').trim();
  creating.value = true;
  try {
    await agentApi.create({
      agentName,
      description: String(payload.description || '').trim(),
      reasoningModelId: String(payload.reasoningModelId || ''),
      status: 'DRAFT',
      tags: stringifyTagList(payload.tags),
    });
    message.success($t('agents.messages.created'));
    createModalApi.close();
    // create 接口仅返回布尔值，按名称查出新记录后跳转编辑页
    const list = await agentApi.list();
    const created = list.find((item) => item.agentName === agentName);
    await (created?.id ? router.push(`/agents/${created.id}/builder`) : reloadCurrentPage());
  } finally {
    creating.value = false;
  }
}

function handleEdit(item: AigcAgent) {
  currentItem.value = item;
  showEdit.value = true;
}

async function handleEditBaseConfig(item: AigcAgent) {
  currentBaseConfigAgent.value = item;
  baseConfigModalApi.open();
  await baseConfigFormApi.resetForm();
  await baseConfigFormApi.setValues(
    {
      agentName: String(item.agentName || ''),
      description: String(item.description || ''),
      icon: String(item.icon || item.avatar || ''),
    },
    false,
  );
}

async function handleSaveBaseConfig() {
  const target = currentBaseConfigAgent.value;
  if (!target?.id) {
    return;
  }
  const values = await baseConfigFormApi.validateAndSubmitForm();
  if (!values) {
    return;
  }
  const payload = values as Record<string, any>;
  const normalizedIcon = String(payload.icon || '').trim();
  await agentApi.update(target.id, {
    agentName: String(payload.agentName || '').trim(),
    avatar: /^https?:\/\//.test(normalizedIcon) ? normalizedIcon : '',
    description: String(payload.description || '').trim(),
    icon: normalizedIcon,
  });
  message.success($t('agents.messages.baseConfigUpdated'));
  baseConfigModalApi.close();
  await reloadCurrentPage();
}

async function handleSave(payload: Partial<AigcAgent>) {
  saving.value = true;
  try {
    if (currentItem.value?.id) {
      await agentApi.update(currentItem.value.id, payload);
      message.success($t('agents.messages.updated'));
    } else {
      await agentApi.create(payload);
      message.success($t('agents.messages.created'));
    }
    showEdit.value = false;
    await reloadCurrentPage();
  } finally {
    saving.value = false;
  }
}

async function handleDelete(item: AigcAgent) {
  if (!item.id) {
    return;
  }
  dialog.warning({
    closable: false,
    content: $t('common.messages.deleteConfirmContent', {
      name: item.agentName || $t('agents.card.unnamed'),
    }),
    negativeText: $t('common.actions.cancel'),
    positiveText: $t('common.actions.confirmDelete'),
    title: $t('agents.messages.deleteTitle'),
    onPositiveClick: async () => {
      await agentApi.remove(item.id!);
      message.success($t('agents.messages.deleted'));
      await reloadCurrentPage();
    },
  });
}

async function initializePage() {
  await Promise.all([loadLookups(), reloadCurrentPage()]);
}

onMounted(initializePage);
</script>

<template>
  <Page>
    <div class="flex flex-col gap-3">
      <PageHeader
        :description="$t('agents.list.description')"
        :title="$t('menu.dynamic.agents')"
      >
        <template #actions>
          <NButton secondary @click="() => initializePage()">
            <template #icon>
              <RefreshCcw class="size-4" />
            </template>
            {{ $t('agents.actions.refreshList') }}
          </NButton>
          <NButton type="primary" @click="openCreateModal">
            <template #icon>
              <Plus class="size-4" />
            </template>
            {{ $t('agents.actions.create') }}
          </NButton>
        </template>
      </PageHeader>

      <LcListCard
        :active-tag="statusFilter"
        :items="items"
        :loading="loading"
        :search-placeholder="$t('agents.list.searchPlaceholder')"
        :search-value="keyword"
        :tags="statusFilterOptions"
        @update:active-tag="handleStatusFilterChange"
        @update:search-value="keyword = $event"
      >
        <template #item="{ item }">
          <AgentCard
            :item="item"
            :knowledge-options="lookups.knowledges"
            :model-options="lookups.models"
            :skill-options="lookups.skills"
            @builder="openDetail"
            @delete="handleDelete"
            @edit="handleEdit"
            @base-config="handleEditBaseConfig"
          />
        </template>
        <template #empty-extra>
          <NButton type="primary" @click="openCreateModal">
            {{ $t('agents.actions.create') }}
          </NButton>
        </template>
      </LcListCard>

      <div
        v-if="total > pageSize"
        class="flex justify-end rounded-xl border border-border bg-card px-4 py-2.5"
      >
        <NPagination
          v-model:page="currentPage"
          v-model:page-size="pageSize"
          :item-count="total"
          :page-sizes="[6, 9, 12, 18]"
          show-size-picker
        />
      </div>

      <BaseConfigModal
        class="w-[560px]"
        header-class="border-b"
        :title="$t('agents.list.baseConfigTitle')"
      >
        <BaseConfigForm />

        <template #footer>
          <div class="flex w-full justify-end gap-2">
            <NButton @click="baseConfigModalApi.close()">
              {{ $t('common.actions.cancel') }}
            </NButton>
            <NButton type="primary" @click="handleSaveBaseConfig">
              {{ $t('common.actions.save') }}
            </NButton>
          </div>
        </template>
      </BaseConfigModal>

      <CreateModal
        class="w-[560px]"
        header-class="border-b"
        :title="$t('agents.title.create')"
      >
        <CreateForm />

        <template #footer>
          <div class="flex w-full justify-end gap-2">
            <NButton @click="createModalApi.close()">
              {{ $t('common.actions.cancel') }}
            </NButton>
            <NButton :loading="creating" type="primary" @click="handleCreateAgent">
              {{ $t('common.actions.create') }}
            </NButton>
          </div>
        </template>
      </CreateModal>

      <AgentEdit
        v-model:show="showEdit"
        :model-options="lookups.models"
        :model-value="currentItem"
        :saving="saving"
        @save="handleSave"
      />
    </div>
  </Page>
</template>
