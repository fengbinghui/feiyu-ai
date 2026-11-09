<script setup lang="ts">
import type { AigcAgent } from '#/api/aigc/agent';
import type { LabelOption } from '#/views/shared/aigc/options';

import { computed } from 'vue';

import { Bot, Database, FileText, Wrench } from '@vben/icons';
import { $t } from '@vben/locales';

import { NEmpty, NSelect, NTag } from 'naive-ui';

import LcStatusTag from '#/components/LcStatusTag/index.vue';
import { resolveOptionLabels } from '#/views/shared/aigc/id-list';
import {
  agentStatusOptions,
  findOptionLabel,
} from '#/views/shared/aigc/options';

interface Props {
  agent: AigcAgent | null;
  knowledgeOptions: LabelOption[];
  modelOptions: LabelOption[];
  options: Array<{ label: string; value: string }>;
  selectedAgentId?: string;
  skillOptions: LabelOption[];
}

const props = defineProps<Props>();

const emit = defineEmits<{
  builder: [];
  refresh: [];
  'update:selectedAgentId': [value: string];
}>();

const selectedKnowledgeLabels = computed(() =>
  resolveOptionLabels(props.knowledgeOptions, props.agent?.knowledgeIds),
);
const selectedSkillLabels = computed(() =>
  resolveOptionLabels(props.skillOptions, props.agent?.skillIds),
);

const modelConfigText = computed(() => {
  if (!props.agent?.modelConfigJson) {
    return $t('agents.runtimePanel.paramsEmpty');
  }
  try {
    return JSON.stringify(JSON.parse(props.agent.modelConfigJson), null, 2);
  } catch {
    return props.agent.modelConfigJson;
  }
});

const agentStatusType = computed(() => {
  if (props.agent?.status === 'PUBLISHED') {
    return 'success' as const;
  }
  if (props.agent?.status === 'DISABLED') {
    return 'error' as const;
  }
  return 'info' as const;
});

function resolveModelLabel(modelId?: string) {
  return (
    props.modelOptions.find((item) => item.value === modelId)?.label ||
    $t('common.status.notConfigured')
  );
}
</script>

<template>
  <div class="overflow-hidden rounded-xl border border-border bg-card">
    <header
      class="flex flex-wrap items-start justify-between gap-3 border-b border-border/60 px-4 py-3"
    >
      <div class="flex min-w-0 items-start gap-2.5">
        <span
          class="flex size-8 shrink-0 items-center justify-center rounded-lg border border-primary/15 bg-primary/8 text-primary"
        >
          <Bot class="size-4" />
        </span>
        <div class="min-w-0">
          <div class="text-sm font-semibold text-foreground">
            {{ $t('agents.runtimePanel.title') }}
          </div>
          <div class="mt-0.5 text-[11px] leading-5 text-muted-foreground">
            {{ $t('agents.runtimePanel.description') }}
          </div>
        </div>
      </div>
      <div class="flex shrink-0 items-center gap-1.5">
        <button
          v-if="agent"
          class="cursor-pointer rounded-md border border-border/70 px-2.5 py-1 text-[11px] font-medium text-muted-foreground transition-colors hover:border-primary/40 hover:bg-primary/5 hover:text-primary"
          type="button"
          @click="emit('builder')"
        >
          {{ $t('agents.runtimePanel.buildDebug') }}
        </button>
        <button
          class="cursor-pointer rounded-md border border-border/70 px-2.5 py-1 text-[11px] font-medium text-muted-foreground transition-colors hover:border-primary/40 hover:bg-primary/5 hover:text-primary"
          type="button"
          @click="emit('refresh')"
        >
          {{ $t('common.actions.refresh') }}
        </button>
      </div>
    </header>

    <div class="px-4 py-3">
      <div
        class="mb-1.5 text-[10px] font-semibold uppercase tracking-[0.16em] text-muted-foreground"
      >
        {{ $t('agents.runtimePanel.currentAgent') }}
      </div>
      <NSelect
        :options="options"
        :value="selectedAgentId"
        clearable
        :placeholder="$t('agents.runtimePanel.agentPlaceholder')"
        @update:value="emit('update:selectedAgentId', $event || '')"
      />
    </div>

    <div v-if="agent" class="space-y-2 border-t border-border/60 px-4 py-3">
      <div
        class="flex items-start justify-between gap-3 rounded-lg border border-border/70 bg-muted/25 px-3 py-2.5"
      >
        <div class="min-w-0">
          <div class="truncate text-sm font-semibold text-foreground">
            {{ agent.agentName || $t('agents.runtimePanel.unnamed') }}
          </div>
          <div class="mt-0.5 truncate text-[11px] text-muted-foreground">
            {{ agent.description || $t('common.empty.noDescription') }}
          </div>
        </div>
        <LcStatusTag
          :label="findOptionLabel(agentStatusOptions(), agent.status)"
          :type="agentStatusType"
        />
      </div>

      <div class="rounded-lg border border-border/70 bg-card px-3 py-2.5">
        <div class="flex items-center gap-1.5 text-[10px] text-muted-foreground">
          <Bot class="size-3" />
          {{ $t('agents.runtimePanel.modelConfig') }}
        </div>
        <div class="mt-1 truncate text-xs font-medium text-foreground">
          {{ resolveModelLabel(agent.reasoningModelId) }}
        </div>
      </div>

      <div class="rounded-lg border border-border/70 bg-card px-3 py-2.5">
        <div class="flex items-center gap-1.5 text-[10px] text-muted-foreground">
          <Database class="size-3" />
          {{ $t('agents.runtimePanel.boundKnowledge') }}
        </div>
        <div class="mt-1.5 flex flex-wrap gap-1.5">
          <NTag
            v-for="label in selectedKnowledgeLabels"
            :key="label"
            :bordered="false"
            round
            size="small"
            type="info"
          >
            {{ label }}
          </NTag>
          <span
            v-if="selectedKnowledgeLabels.length === 0"
            class="text-[11px] text-muted-foreground"
          >
            {{ $t('agents.runtimePanel.knowledgeEmpty') }}
          </span>
        </div>
      </div>

      <div class="rounded-lg border border-border/70 bg-card px-3 py-2.5">
        <div class="flex items-center gap-1.5 text-[10px] text-muted-foreground">
          <Wrench class="size-3" />
          {{ $t('agents.runtimePanel.boundSkills') }}
        </div>
        <div class="mt-1.5 flex flex-wrap gap-1.5">
          <NTag
            v-for="label in selectedSkillLabels"
            :key="label"
            :bordered="false"
            round
            size="small"
            type="success"
          >
            {{ label }}
          </NTag>
          <span
            v-if="selectedSkillLabels.length === 0"
            class="text-[11px] text-muted-foreground"
          >
            {{ $t('agents.runtimePanel.skillsEmpty') }}
          </span>
        </div>
      </div>

      <div class="rounded-lg border border-border/70 bg-card px-3 py-2.5">
        <div class="flex items-center gap-1.5 text-[10px] text-muted-foreground">
          <FileText class="size-3" />
          {{ $t('agents.runtimePanel.systemPrompt') }}
        </div>
        <div
          class="mt-1.5 line-clamp-6 whitespace-pre-wrap text-xs leading-6 text-foreground/85"
        >
          {{ agent.systemPrompt || $t('agents.runtimePanel.systemPromptEmpty') }}
        </div>
      </div>

      <div class="rounded-lg border border-border/70 bg-card px-3 py-2.5">
        <div
          class="flex items-center gap-1.5 text-[10px] uppercase tracking-[0.12em] text-muted-foreground"
        >
          {{ $t('agents.runtimePanel.paramsOverride') }}
        </div>
        <pre
          class="mt-1.5 overflow-x-auto whitespace-pre-wrap rounded-md bg-muted/25 p-2 text-[11px] leading-5 text-foreground/85"
          >{{ modelConfigText }}</pre
        >
      </div>
    </div>

    <div v-else class="border-t border-border/60">
      <NEmpty :description="$t('agents.runtimePanel.empty')" />
    </div>
  </div>
</template>
