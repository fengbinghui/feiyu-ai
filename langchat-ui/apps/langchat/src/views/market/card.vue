<script lang="ts" setup>
import type { AigcAgent } from '#/api/aigc/agent';
import type { LabelOption } from '#/views/shared/aigc/options';

import { MessageCircle } from '@vben/icons';
import { $t } from '@vben/locales';

import { NButton } from 'naive-ui';

import AigcAgentCard from '#/components/AigcAgentCard/index.vue';

interface Props {
  item: AigcAgent;
  knowledgeOptions: LabelOption[];
  modelOptions: LabelOption[];
  skillOptions: LabelOption[];
}

defineProps<Props>();

const emit = defineEmits<{
  enter: [item: AigcAgent];
}>();
</script>

<template>
  <div class="market-agent-card h-full">
    <AigcAgentCard
      :item="item"
      :knowledge-options="knowledgeOptions"
      :model-options="modelOptions"
      :skill-options="skillOptions"
      @click="emit('enter', $event)"
    >
      <template #footer>
        <NButton
          class="market-enter-btn"
          size="small"
          type="primary"
          @click.stop="emit('enter', item)"
        >
          <template #icon>
            <MessageCircle class="size-3.5" />
          </template>
          {{ $t('market.enterApp') }}
        </NButton>
      </template>
    </AigcAgentCard>
  </div>
</template>

<style scoped>
.market-agent-card :deep(.lc-card) {
  min-height: 196px;
}

.market-agent-card :deep(.lc-card:hover) {
  box-shadow: var(--shadow-card-hover);
}

.market-enter-btn {
  min-width: 84px;
  border-radius: calc(var(--radius) * 0.85);
}
</style>
