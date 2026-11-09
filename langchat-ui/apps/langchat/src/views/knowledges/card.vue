<script setup lang="ts">
import type { AigcKnowledge } from '#/api/aigc/knowledge';
import type { LabelOption } from '#/views/shared/aigc/options';

import { computed } from 'vue';

import { FileText, SquarePen, Trash2 } from '@vben/icons';
import { $t } from '@vben/locales';

import { NButton, NSpace } from 'naive-ui';

import LcCard from '#/components/LcCard/index.vue';
import { parseTagList } from '#/views/shared/aigc/tags';
import { formatRelativeTime } from '#/views/shared/aigc/time';

interface Props {
  item: AigcKnowledge;
  modelOptions: LabelOption[];
  vectorStoreOptions: LabelOption[];
}

const props = defineProps<Props>();

const emit = defineEmits<{
  delete: [item: AigcKnowledge];
  docs: [item: AigcKnowledge];
  edit: [item: AigcKnowledge];
  index: [item: AigcKnowledge];
}>();

const vectorModelLabel = computed(() => {
  const found = props.modelOptions.find(
    (o) => o.value === props.item.vectorModelId,
  );
  return found?.label || $t('common.status.notConfigured');
});

const vectorStoreLabel = computed(() => {
  const found = props.vectorStoreOptions.find(
    (o) => o.value === props.item.vectorStoreId,
  );
  return found?.label || $t('common.status.notConfigured');
});

/** 知识库自定义图标（coverUrl 字段），缺省回退到数据库图标 */
const knowledgeIcon = computed(() => String(props.item.coverUrl || '').trim());

const tags = computed(() => parseTagList(props.item.tags));

const metaItems = computed(() => [
  { label: $t('knowledge.card.vectorModel'), value: vectorModelLabel.value },
  { label: $t('knowledge.card.vectorStore'), value: vectorStoreLabel.value },
  {
    label: $t('knowledge.card.recall'),
    value: props.item.maxResults
      ? $t('knowledge.card.topN', { count: props.item.maxResults })
      : $t('common.status.default'),
  },
]);
</script>

<template>
  <LcCard
    default-icon="lucide:database"
    :hoverable="true"
    :icon="knowledgeIcon"
    :meta-items="metaItems"
    class="h-full cursor-pointer"
    @click="emit('docs', item)"
  >
    <template #header>
      <div class="min-w-0">
        <div
          class="truncate text-sm font-semibold text-foreground transition-colors group-hover:text-primary"
        >
          {{ item.name || $t('knowledge.card.unnamed') }}
        </div>
      </div>
    </template>

    <template #description>
      <p class="line-clamp-2 text-[11px] leading-5 text-muted-foreground">
        {{ item.description || $t('knowledge.card.defaultDescription') }}
      </p>
    </template>

    <div v-if="tags.length > 0" class="flex flex-wrap items-center gap-1">
      <span
        v-for="tag in tags"
        :key="tag"
        class="rounded-md border border-border/60 bg-muted/40 px-2 py-0.5 text-[10px] leading-4 text-muted-foreground"
      >
        {{ tag }}
      </span>
    </div>

    <template #footer-extra>
      <div
        class="inline-flex min-w-0 items-center gap-1.5 truncate whitespace-nowrap text-[11px] text-muted-foreground"
      >
        <FileText class="size-3 shrink-0" />
        <span class="truncate">{{ formatRelativeTime(item.updateTime) }}</span>
      </div>
    </template>

    <template #footer>
      <NSpace :size="2">
        <NButton
          v-tippy="$t('docs.actions.backToList')"
          :aria-label="$t('docs.actions.backToList')"
          circle
          class="text-muted-foreground hover:text-primary"
          quaternary
          size="small"
          @click.stop="emit('docs', item)"
        >
          <FileText class="size-3.5" />
        </NButton>
        <NButton
          v-tippy="$t('common.actions.edit')"
          :aria-label="$t('common.actions.edit')"
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
          :aria-label="$t('common.actions.delete')"
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
