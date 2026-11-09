<script lang="ts" setup>
import type { AigcDatasource } from '#/api/aigc/datasource';

import { computed } from 'vue';

import { Eye, SquarePen, Trash2 } from '@vben/icons';
import { $t } from '@vben/locales';

import { NButton, NSpace } from 'naive-ui';

import LcCard from '#/components/LcCard/index.vue';
import LcStatusTag from '#/components/LcStatusTag/index.vue';

import { formatRelativeTime } from '#/views/shared/aigc/time';

import { getDatasourceTypeMeta } from './datasource-meta';

interface Props {
  item: AigcDatasource;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  delete: [item: AigcDatasource];
  detail: [item: AigcDatasource];
  edit: [item: AigcDatasource];
}>();

const datasourceTypeMeta = computed(() =>
  getDatasourceTypeMeta(props.item.dbType),
);

const connectionSummary = computed(() => {
  const address = [props.item.host, props.item.port]
    .filter((value) => value !== undefined && value !== null && value !== '')
    .join(':');
  const database = props.item.databaseName || '';
  return (
    [address, database].filter(Boolean).join(' / ') ||
    $t('datasource.card.noConnectionInfo')
  );
});

const metaItems = computed(() => [
  { label: $t('common.labels.type'), value: datasourceTypeMeta.value.label },
  {
    label: $t('common.labels.host'),
    value: props.item.host || '--',
  },
  {
    label: $t('common.labels.port'),
    value: props.item.port ? String(props.item.port) : '--',
  },
  {
    label: $t('datasource.card.databaseShort'),
    value: props.item.databaseName || '--',
  },
]);
</script>

<template>
  <LcCard
    :icon="datasourceTypeMeta.icon"
    :meta-items="metaItems"
    class="c-datasource-card h-full cursor-pointer"
    hoverable
    @click="emit('detail', item)"
  >
    <template #header>
      <div class="min-w-0">
        <div
          class="truncate text-sm font-semibold text-foreground transition-colors group-hover:text-primary"
        >
          {{ item.name || $t('datasource.card.unnamed') }}
        </div>
      </div>
    </template>

    <template #description>
      <p class="line-clamp-2 text-[11px] leading-4 text-muted-foreground">
        {{ item.remark || connectionSummary }}
      </p>
    </template>

    <template #header-extra>
      <LcStatusTag
        :label="
          item.enabled ? $t('common.status.enabled') : $t('common.status.disabled')
        "
        :type="item.enabled ? 'success' : 'default'"
      />
    </template>

    <template #footer-extra>
      <div class="truncate whitespace-nowrap text-[10px] text-muted-foreground">
        {{ formatRelativeTime(item.updateTime) }}
      </div>
    </template>

    <template #footer>
      <NSpace :size="2">
        <NButton
          v-tippy="$t('common.actions.viewDetails')"
          circle
          class="text-muted-foreground hover:text-primary"
          quaternary
          size="small"
          @click.stop="emit('detail', item)"
        >
          <Eye class="size-3.5" />
        </NButton>
        <NButton
          v-tippy="$t('common.actions.edit')"
          circle
          class="text-muted-foreground hover:text-primary"
          quaternary
          size="small"
          @click.stop="emit('edit', item)"
        >
          <SquarePen class="size-3.5" />
        </NButton>
        <NButton
          v-tippy="$t('common.actions.delete')"
          circle
          class="text-muted-foreground hover:text-destructive"
          quaternary
          size="small"
          type="error"
          @click.stop="emit('delete', item)"
        >
          <Trash2 class="size-3.5" />
        </NButton>
      </NSpace>
    </template>
  </LcCard>
</template>

<style scoped>
.c-datasource-card {
  transition:
    transform 0.2s ease,
    border-color 0.2s ease,
    background-color 0.2s ease,
    box-shadow 0.2s ease;
}

.c-datasource-card:hover {
  transform: translateY(-2px);
}
</style>
