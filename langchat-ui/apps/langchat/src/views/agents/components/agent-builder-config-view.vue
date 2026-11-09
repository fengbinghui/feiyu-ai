<script lang="ts" setup>
import type {
  LcChatMessage,
  LcChatSendPayload,
} from '#/components/LcChat/types';

import { ref } from 'vue';

import {
  ChevronDown,
  ChevronUp,
  FileText,
  Maximize2,
  Minimize2,
} from '@vben/icons';
import { $t } from '@vben/locales';

import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from '@vben-core/shadcn-ui';

import { NDynamicInput, NInput, NSwitch } from 'naive-ui';

import LcChat from '#/components/LcChat/index.vue';
import LcIconDisplay from '#/components/LcIcon/display.vue';
import AgentConfigPanel from '#/views/agents/components/agent-config-panel.vue';

interface RelationOption {
  description?: string;
  label: string;
  metrics?: string[];
  tags?: string[];
  value: string;
}

interface Props {
  appIcon?: string;
  chatLoading?: boolean;
  defaultSuggestions: string[];
  historyLoading?: boolean;
  knowledgeRelationOptions: RelationOption[];
  mcpRelationOptions: RelationOption[];
  modelEntities: any[];
  skillRelationOptions: RelationOption[];
  userIcon?: string;
  welcomeMessage: string;
}

withDefaults(defineProps<Props>(), {
  appIcon: '',
  chatLoading: false,
  historyLoading: false,
  userIcon: '',
});

const emit = defineEmits<{
  submit: [payload: LcChatSendPayload];
}>();

const formModel = defineModel<any>({ required: true });

/** 首行合并折叠卡片(提示词与欢迎语)的展开状态 */
const promptExpanded = ref(true);

type BuilderPanelKey = 'chat' | 'config';

/** 当前页面内全屏的面板，null 表示两栏常规布局 */
const maximizedPanel = ref<BuilderPanelKey | null>(null);

function toggleMaximize(panel: BuilderPanelKey) {
  maximizedPanel.value = maximizedPanel.value === panel ? null : panel;
}

/** 聊天布局实例,由父级(builder)通过它驱动消息流 */
const layoutRef = ref<InstanceType<typeof LcChat> | null>(null);
/** 独立于 LcChat 实例保存消息，避免面板全屏切换重建组件后丢失历史。 */
const chatMessages = ref<LcChatMessage[]>([]);

defineExpose({ layoutRef });

</script>

<template>
  <ResizablePanelGroup
    :key="maximizedPanel ?? 'split'"
    class="overflow-hidden rounded-xl"
    direction="horizontal"
  >
    <!-- 配置列:应用配置 + 折叠分组(系统提示词 / 欢迎语与建议) -->
    <ResizablePanel
      v-if="!maximizedPanel || maximizedPanel === 'config'"
      :default-size="maximizedPanel ? 100 : 34"
      :min-size="maximizedPanel ? undefined : 24"
    >
      <div
        class="flex h-full min-h-0 flex-col overflow-hidden rounded-xl border border-border/70 bg-card"
      >
        <div
          class="flex shrink-0 items-center justify-between gap-2 border-b border-border/60 px-3 py-2.5"
        >
          <div class="min-w-0">
            <div class="flex items-center gap-2">
              <span class="h-3 w-0.5 rounded-full bg-primary"></span>
              <div class="text-sm font-semibold text-foreground">
                {{ $t('agents.configView.appConfig') }}
              </div>
            </div>
            <div class="mt-1 flex flex-wrap items-center gap-x-2 gap-y-0.5 pl-2.5 text-[10px] text-muted-foreground">
              <span>
                {{ formModel.knowledgeIdsList?.length ?? 0 }}
                {{ $t('common.labels.knowledgeBase') }}
              </span>
              <span class="size-0.5 rounded-full bg-muted-foreground/40"></span>
              <span>
                {{ formModel.skillIdsList?.length ?? 0 }}
                {{ $t('common.labels.skill') }}
              </span>
              <span class="size-0.5 rounded-full bg-muted-foreground/40"></span>
              <span>
                {{ formModel.mcpIdsList?.length ?? 0 }}
                {{ $t('agents.configPanel.mcp.title') }}
              </span>
            </div>
          </div>
          <button
            v-tippy="
              maximizedPanel === 'config'
                ? $t('agents.actions.restore')
                : $t('agents.actions.maximize')
            "
            class="flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-primary/10 hover:text-primary"
            type="button"
            @click="toggleMaximize('config')"
          >
            <Minimize2 v-if="maximizedPanel === 'config'" class="size-4" />
            <Maximize2 v-else class="size-4" />
          </button>
        </div>

        <div class="flex min-h-0 flex-1 flex-col p-2.5">
          <AgentConfigPanel
            v-model="formModel"
            :knowledge-relation-options="knowledgeRelationOptions"
            :mcp-relation-options="mcpRelationOptions"
            :model-entities="modelEntities"
            :skill-relation-options="skillRelationOptions"
          >
            <template #after-model>
              <section
                class="overflow-hidden rounded-xl border border-border/70 bg-card"
              >
                <button
                  class="flex w-full items-center justify-between gap-2 px-3 py-2.5 text-left transition-colors hover:bg-muted/50"
                  type="button"
                  @click="promptExpanded = !promptExpanded"
                >
                  <span
                    class="inline-flex items-center gap-2 text-sm font-semibold text-foreground"
                  >
                    <span
                      class="flex size-6 items-center justify-center rounded-md bg-primary/8 text-primary"
                    >
                      <FileText class="size-3.5" />
                    </span>
                    {{ $t('agents.configView.promptGroup') }}
                  </span>
                  <ChevronUp
                    v-if="promptExpanded"
                    class="size-3.5 text-muted-foreground"
                  />
                  <ChevronDown v-else class="size-3.5 text-muted-foreground" />
                </button>
                <div
                  v-if="promptExpanded"
                  class="space-y-3 border-t border-border/60 p-3"
                >
                  <div>
                    <div class="mb-1.5 text-xs font-medium text-foreground/80">
                      {{ $t('agents.configView.systemPrompt') }}
                    </div>
                    <NInput
                      v-model:value="formModel.systemPrompt"
                      :autosize="{ minRows: 6, maxRows: 14 }"
                      :placeholder="$t('agents.configView.systemPromptPlaceholder')"
                      type="textarea"
                    />
                  </div>
                  <div>
                    <div class="mb-1.5 text-xs font-medium text-foreground/80">
                      {{ $t('agents.configView.welcome') }}
                    </div>
                    <NInput
                      v-model:value="formModel.welcomeMessage"
                      :placeholder="$t('agents.configView.welcomePlaceholder')"
                    />
                  </div>
                  <div
                    class="rounded-lg border border-border/70 bg-muted/20 px-3 py-2"
                  >
                    <div class="flex items-center justify-between gap-3">
                      <div class="min-w-0">
                        <div class="text-xs font-semibold text-foreground">
                          {{ $t('agents.configView.autoSuggest') }}
                        </div>
                        <div class="mt-0.5 text-[11px] leading-5 text-muted-foreground">
                          {{ $t('agents.configView.autoSuggestHint') }}
                        </div>
                      </div>
                      <NSwitch v-model:value="formModel.enableAutoSuggestion" />
                    </div>
                  </div>
                  <div>
                    <div class="mb-1.5 flex items-center justify-between gap-2">
                      <span class="text-xs font-medium text-foreground/80">
                        {{ $t('agents.configView.defaultSuggestions') }}
                      </span>
                      <span class="text-[10px] tabular-nums text-muted-foreground">
                        {{ formModel.defaultSuggestionsList?.length ?? 0 }}
                      </span>
                    </div>
                    <NDynamicInput
                      v-model:value="formModel.defaultSuggestionsList"
                      :on-create="() => ''"
                    >
                      <template #default="{ index, value }">
                        <NInput
                          :placeholder="
                            $t('agents.configView.suggestionPlaceholder', {
                              index: index + 1,
                            })
                          "
                          :value="value"
                          @update:value="
                            formModel.defaultSuggestionsList[index] = $event
                          "
                        />
                      </template>
                    </NDynamicInput>
                  </div>
                </div>
              </section>
            </template>
          </AgentConfigPanel>
        </div>
      </div>
    </ResizablePanel>

    <ResizableHandle
      v-if="!maximizedPanel"
      class="w-3 bg-transparent transition-colors hover:bg-primary/10"
      with-handle
    />

    <!-- 聊天调试列:尽量占据剩余宽度 -->
    <ResizablePanel
      v-if="!maximizedPanel || maximizedPanel === 'chat'"
      :default-size="maximizedPanel ? 100 : 66"
      :min-size="maximizedPanel ? undefined : 40"
    >
      <div
        class="builder-chat-panel flex h-full min-h-0 flex-col overflow-hidden rounded-xl border border-border/70 bg-card"
      >
        <div
          class="flex shrink-0 items-center justify-between gap-2 border-b border-border/60 px-3 py-2.5"
        >
          <div class="min-w-0">
            <div class="flex items-center gap-2">
              <span class="h-3 w-0.5 rounded-full bg-primary"></span>
              <div class="text-sm font-semibold text-foreground">
                {{ $t('agents.configView.chatDebug') }}
              </div>
              <span
                v-if="formModel.agentName"
                class="max-w-[220px] truncate rounded-md border border-border/70 bg-muted/30 px-1.5 py-0.5 text-[10px] text-muted-foreground"
              >
                {{ formModel.agentName }}
              </span>
            </div>
            <div class="mt-1 pl-2.5 text-[10px] text-muted-foreground">
              {{ $t('agents.configView.debugPlaceholder') }}
            </div>
          </div>
          <button
            v-tippy="
              maximizedPanel === 'chat'
                ? $t('agents.actions.restore')
                : $t('agents.actions.maximize')
            "
            class="flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-primary/10 hover:text-primary"
            type="button"
            @click="toggleMaximize('chat')"
          >
            <Minimize2 v-if="maximizedPanel === 'chat'" class="size-4" />
            <Maximize2 v-else class="size-4" />
          </button>
        </div>
        <div class="flex min-h-0 flex-1 flex-col p-2.5">
          <LcChat
            ref="layoutRef"
            :assistant-icon="appIcon || ''"
            :disabled="chatLoading"
            :empty-title="welcomeMessage"
            :initial-messages="chatMessages"
            :loading="historyLoading"
            :placeholder="$t('agents.configView.debugPlaceholder')"
            :show-attachment="true"
            :show-model="false"
            :suggestions="defaultSuggestions.map((label) => ({ label }))"
            :user-avatar="userIcon || ''"
            @messages-change="chatMessages = $event"
            @send="emit('submit', $event)"
          >
            <template #empty-head>
              <div class="flex flex-col items-center gap-3 text-center">
                <LcIconDisplay
                  :icon="appIcon"
                  fallback-icon="lucide:bot"
                  :size="64"
                  class="!rounded-2xl border-border/80 bg-background"
                />
                <div class="text-base font-semibold text-foreground">
                  {{ welcomeMessage }}
                </div>
                <div class="text-xs text-muted-foreground">
                  {{ $t('agents.configView.debugPlaceholder') }}
                </div>
              </div>
            </template>
          </LcChat>
        </div>
      </div>
    </ResizablePanel>
  </ResizablePanelGroup>
</template>

<style scoped>
/* 调试对话气泡与输入区的轻量打磨：只作用于构建器内的 LcChat */
.builder-chat-panel :deep([class*='rounded-[14px]']) {
  border-radius: 16px;
}

.builder-chat-panel :deep([class*='rounded-[14px]'][class*='bg-primary']) {
  background-image: linear-gradient(
    135deg,
    hsl(var(--primary)) 0%,
    hsl(212 100% 54%) 100%
  );
  box-shadow: 0 6px 18px -12px hsl(var(--primary) / 60%);
}

.builder-chat-panel :deep([class*='rounded-[14px]'][class*='bg-muted/60']) {
  background-color: hsl(var(--card));
  border: 1px solid hsl(var(--border) / 80%);
  box-shadow: 0 1px 2px hsl(var(--foreground) / 4%);
}

.builder-chat-panel :deep([class*='group-hover:opacity-100']) {
  opacity: 0.5;
}

.builder-chat-panel :deep([class*='group-hover:opacity-100']:hover) {
  opacity: 1;
}

.builder-chat-panel :deep(.composer) {
  background: hsl(var(--muted) / 30%);
  border-color: hsl(var(--border) / 85%);
  border-radius: 16px;
}

.builder-chat-panel :deep(.composer:focus-within) {
  background: hsl(var(--background));
}
</style>
