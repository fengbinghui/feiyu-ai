import type { AigcModel } from '#/api/aigc/model';
import type { LabelOption } from '#/views/shared/aigc/options';

import { computed, ref } from 'vue';

import { $t } from '@vben/locales';
import { useAccessStore } from '@vben/stores';

import { knowledgeApi } from '#/api/aigc/knowledge';
import { mcpApi } from '#/api/aigc/mcp';
import { modelApi } from '#/api/aigc/model';
import { skillApi } from '#/api/aigc/skill';
import { vectorStoreApi } from '#/api/aigc/vector-store';

interface LookupOptions {
  knowledges?: boolean;
  mcps?: boolean;
  models?: boolean;
  skills?: boolean;
  vectorStores?: boolean;
}

/** lookups 对应的权限码：无权限时跳过请求，避免 403 干扰普通用户 */
const LOOKUP_PERMISSIONS = {
  knowledges: 'aigc:knowledge:list',
  mcps: 'aigc:mcp:list',
  models: 'aigc:model:list',
  skills: 'aigc:skill:list',
  vectorStores: 'aigc:vector-store:list',
} as const;

function toOption(value: number | string, label: string): LabelOption {
  return {
    label,
    value,
  };
}

/**
 * AIGC 关联下拉数据。原始数据与展示 label 分离存储，
 * label 在 computed 中按当前语言生成，语言切换时实时更新。
 */
export function useAigcLookups(options: LookupOptions = {}) {
  const models = ref<AigcModel[]>([]);
  const vectorStores = ref<Awaited<ReturnType<typeof vectorStoreApi.list>>>([]);
  const knowledges = ref<Awaited<ReturnType<typeof knowledgeApi.list>>>([]);
  const mcps = ref<Awaited<ReturnType<typeof mcpApi.list>>>([]);
  const skills = ref<Awaited<ReturnType<typeof skillApi.list>>>([]);

  async function loadLookups() {
    const tasks: Promise<void>[] = [];
    const accessCodes = useAccessStore().accessCodes ?? [];
    const canLoad = (key: keyof typeof LOOKUP_PERMISSIONS) =>
      accessCodes.includes(LOOKUP_PERMISSIONS[key]);

    if (options.models && canLoad('models')) {
      tasks.push(
        modelApi.list().then((items) => {
          models.value = items;
        }),
      );
    }

    if (options.vectorStores && canLoad('vectorStores')) {
      tasks.push(
        vectorStoreApi.list().then((items) => {
          vectorStores.value = items;
        }),
      );
    }

    if (options.knowledges && canLoad('knowledges')) {
      tasks.push(
        knowledgeApi.list().then((items) => {
          knowledges.value = items;
        }),
      );
    }

    if (options.mcps && canLoad('mcps')) {
      tasks.push(
        mcpApi.list().then((items) => {
          mcps.value = items;
        }),
      );
    }

    if (options.skills && canLoad('skills')) {
      tasks.push(
        skillApi.list().then((items) => {
          skills.value = items;
        }),
      );
    }

    await Promise.all(tasks);
  }

  const modelOptions = computed<LabelOption[]>(() =>
    models.value.map((item) =>
      toOption(
        item.id ?? '',
        `${item.name || item.model || $t('models.card.unnamed')} · ${item.provider || 'UNKNOWN'}`,
      ),
    ),
  );

  const vectorStoreOptions = computed<LabelOption[]>(() =>
    vectorStores.value.map((item) =>
      toOption(
        item.id ?? '',
        `${item.name || $t('vectorStores.card.unnamed')} · ${item.provider || 'UNKNOWN'}`,
      ),
    ),
  );

  const knowledgeOptions = computed<LabelOption[]>(() =>
    knowledges.value.map((item) =>
      toOption(item.id ?? '', item.name || $t('knowledge.card.unnamed')),
    ),
  );

  const mcpOptions = computed<LabelOption[]>(() =>
    mcps.value.map((item) =>
      toOption(item.id ?? '', item.name || $t('mcp.card.unnamed')),
    ),
  );

  const skillOptions = computed<LabelOption[]>(() =>
    skills.value.map((item) =>
      toOption(
        item.id ?? '',
        item.title || item.name || $t('skills.empty.unnamed'),
      ),
    ),
  );

  return {
    lookups: computed(() => ({
      knowledges: knowledgeOptions.value,
      mcps: mcpOptions.value,
      modelEntities: models.value,
      models: modelOptions.value,
      skills: skillOptions.value,
      vectorStoreEntities: vectorStores.value,
      vectorStores: vectorStoreOptions.value,
    })),
    loadLookups,
  };
}
