<script setup lang="ts">
import type { AigcAgent } from '#/api/aigc/agent';
import type { LabelOption } from '#/views/shared/aigc/options';

import { computed, markRaw, watch } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { $t } from '@vben/locales';

import { useVbenForm, type VbenFormSchema } from '#/adapter/form';
import LcIcon from '#/components/LcIcon/index.vue';
import {
  agentStatusOptions,
} from '#/views/shared/aigc/options';
import {
  formGroupDivider,
  stripFormGroupFields,
} from '#/views/shared/forms';

interface Props {
  modelOptions: LabelOption[];
  modelValue?: null | Partial<AigcAgent>;
  saving?: boolean;
  show: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  saving: false,
});
const emit = defineEmits<{
  cancel: [];
  save: [payload: Partial<AigcAgent>];
  'update:show': [value: boolean];
}>();

const LcIconEditor = markRaw(LcIcon);

const drawerTitle = computed(() =>
  props.modelValue?.id ? $t('agents.title.edit') : $t('agents.title.create'),
);

const formSchema = computed<VbenFormSchema[]>(() => [
  formGroupDivider('basic', $t('agents.form.groups.basic')),
  {
    component: 'Input',
    fieldName: 'agentName',
    label: $t('agents.form.name'),
    rules: 'required',
  },
  {
    component: LcIconEditor,
    componentProps: {
      editable: true,
      fallbackIcon: 'lucide:bot',
      showSvgTab: false,
      size: 64,
    },
    fieldName: 'icon',
    formItemClass: 'sm:col-span-2',
    label: $t('agents.form.appIcon'),
    modelPropName: 'modelValue',
  },
  formGroupDivider('model', $t('agents.form.groups.model')),
  {
    component: 'Select',
    componentProps: {
      options: agentStatusOptions(),
    },
    fieldName: 'status',
    label: $t('agents.form.status'),
  },
  {
    component: 'Select',
    componentProps: {
      clearable: true,
      options: props.modelOptions,
    },
    fieldName: 'reasoningModelId',
    label: $t('agents.form.reasoningModel'),
    rules: 'selectRequired',
  },
  formGroupDivider('content', $t('agents.form.groups.content')),
  {
    component: 'Input',
    componentProps: {
      autosize: { maxRows: 6, minRows: 3 },
      placeholder: $t('agents.form.descriptionPlaceholder'),
      type: 'textarea',
    },
    fieldName: 'description',
    formItemClass: 'sm:col-span-2',
    label: $t('agents.form.description'),
  },
]);

const [Form, formApi] = useVbenForm({
  layout: 'vertical',
  schema: formSchema.value,
  showDefaultActions: false,
  wrapperClass: 'lc-form-grid grid-cols-1 gap-x-4 px-1 pb-1 sm:grid-cols-2',
});

watch(
  () => formSchema.value,
  (schema) => {
    formApi.setState({ schema });
  },
  { immediate: true },
);

function emitClose(withCancel: boolean = true) {
  emit('update:show', false);
  if (withCancel) {
    emit('cancel');
  }
}

async function handleSave() {
  const values = await formApi.validateAndSubmitForm();
  if (!values) {
    return;
  }
  const payload = stripFormGroupFields(values as Record<string, any>);
  const normalizedIcon = String(payload.icon || '').trim();
  emit('save', {
    ...payload,
    avatar: /^https?:\/\//.test(normalizedIcon) ? normalizedIcon : '',
    icon: normalizedIcon,
  });
}

const [Drawer, drawerApi] = useVbenDrawer({
  class: 'w-[920px]',
  confirmText: $t('common.actions.save'),
  onCancel: () => emitClose(true),
  onConfirm: handleSave,
  onOpenChange: (isOpen) => {
    if (!isOpen) {
      emitClose(true);
    }
  },
  title: drawerTitle.value,
});

watch(
  () => $t('common.actions.save'),
  (value) => {
    drawerApi.setState({ confirmText: value });
  },
  { immediate: true },
);

watch(
  () => props.saving,
  (value) => {
    drawerApi.setState({ confirmLoading: value });
  },
  { immediate: true },
);

watch(
  () => drawerTitle.value,
  (value) => {
    drawerApi.setState({ title: value });
  },
  { immediate: true },
);

watch(
  () => [props.show, props.modelValue],
  async () => {
    if (!props.show) {
      drawerApi.close();
      return;
    }

    const currentValue = props.modelValue ?? {};
    drawerApi.open();

    await formApi.resetForm();
    await formApi.setValues(
      {
        icon: String(currentValue.icon || currentValue.avatar || ''),
        status: 'DRAFT',
        ...currentValue,
      },
      false,
    );
  },
  { immediate: true },
);
</script>

<template>
  <Drawer>
    <Form />
  </Drawer>
</template>
