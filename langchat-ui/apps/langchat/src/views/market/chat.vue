<script lang="ts" setup>
import type { AigcAgent } from '#/api/aigc/agent';
import type {
  LcChatMessage,
  LcChatSendPayload,
} from '#/components/LcChat/types';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { ArrowLeft, Bot, Check, Plus, Search, SquarePen, Trash2, X } from '@vben/icons';
import { $t } from '@vben/locales';

import { NButton, NInput } from 'naive-ui';

import { dialog, message } from '#/adapter/naive';
import {
  createConversationApi,
  removeConversationApi,
  updateConversationApi,
} from '#/api/aigc/chat';
import LcChat from '#/components/LcChat/index.vue';
import LcStatusTag from '#/components/LcStatusTag/index.vue';
import { agentStatusOptions, findOptionLabel } from '#/views/shared/aigc/options';
import { useChatRuntime } from '#/views/shared/chat/use-chat-runtime';

const route = useRoute();
const router = useRouter();
const historyKeyword = ref('');
const historyEditingId = ref('');
const historyEditingTitle = ref('');
const chatRef = ref<InstanceType<typeof LcChat> | null>(null);

const {
  conversationSidebarItems,
  draftMessage,
  initializeRuntime,
  loadConversations,
  messageLoading,
  messageItems,
  selectedAgent,
  selectedAgentId,
  selectedConversationId,
  sending,
  sendMessage,
  selectAgent,
  selectConversation,
} = useChatRuntime({
  enableHistory: true,
});

const routeAgentId = computed(() => String(route.params.agentId || ''));

const agentStatusLabel = computed(() =>
  findOptionLabel(
    agentStatusOptions(),
    String((selectedAgent.value as AigcAgent | null)?.status || ''),
  ),
);
const agentStatusType = computed(() => {
  const status = String((selectedAgent.value as AigcAgent | null)?.status || '');
  if (status === 'PUBLISHED') {
    return 'success' as const;
  }
  if (status === 'DISABLED') {
    return 'error' as const;
  }
  return 'info' as const;
});

const agentDescription = computed(
  () =>
    String((selectedAgent.value as AigcAgent | null)?.description || '') ||
    $t('chat.runtime.agentDescriptionFallback'),
);

const activeConversationTitle = computed(() => {
  const current = conversationSidebarItems.value.find(
    (item) => item.id === selectedConversationId.value,
  );
  return current?.title || $t('chat.conversation.untitled');
});

function parseMessageContent(raw: string) {
  const text = String(raw || '').trim();
  if (!text) {
    return '';
  }
  const segments = text
    .split(/\n\s*\n/)
    .map((item) => item.trim())
    .filter(Boolean);
  const parsed: string[] = [];
  for (const segment of segments) {
    try {
      const payload = JSON.parse(segment) as { content?: unknown };
      const content = String(payload?.content ?? '').trim();
      if (content) {
        parsed.push(content);
      }
    } catch {
      parsed.push(segment);
    }
  }
  return parsed.join('\n\n');
}

const marketChatMessages = computed<LcChatMessage[]>(() =>
  messageItems.value
    .filter(
      (
        item,
      ): item is {
        content: string;
        id: string;
        role: 'assistant' | 'user';
      } => item.role === 'assistant' || item.role === 'user',
    )
    .map((item) => ({
      content: parseMessageContent(item.content),
      id: item.id,
      role: item.role,
      status: 'completed' as const,
    })),
);

const marketSuggestions = computed(() =>
  resolveDefaultSuggestions(selectedAgent.value as AigcAgent).map((label) => ({
    label,
  })),
);

const filteredConversationItems = computed(() => {
  const query = historyKeyword.value.trim().toLowerCase();
  if (!query) {
    return conversationSidebarItems.value;
  }
  return conversationSidebarItems.value.filter((item) =>
    String(item.title || '')
      .toLowerCase()
      .includes(query),
  );
});

function resolveAgentPreviewIcon(item: AigcAgent | null) {
  const icon = String(item?.icon || '').trim();
  if (/^https?:\/\//.test(icon)) {
    return icon;
  }
  const avatar = String(item?.avatar || '').trim();
  if (/^https?:\/\//.test(avatar)) {
    return avatar;
  }
  return '';
}

function resolveDefaultSuggestions(item: AigcAgent | null) {
  if (!item?.defaultSuggestions) {
    return [];
  }
  try {
    const parsed = JSON.parse(item.defaultSuggestions);
  if (Array.isArray(parsed)) {
      return parsed
        .map((value) => String(value || '').trim())
        .filter(Boolean)
        .slice(0, 6);
    }
  } catch {
    // noop
  }
  return [];
}

async function handleSubmitMessage(payload: LcChatSendPayload) {
  draftMessage.value = String(payload.text || '').trim();
  await sendMessage(payload);
}

async function syncChatMessages() {
  await nextTick();
  chatRef.value?.setMessages(
    marketChatMessages.value.map((item, index, list) => ({
      ...item,
      status:
        sending.value &&
        item.role === 'assistant' &&
        index === list.length - 1
          ? 'running'
          : 'completed',
    })),
  );
}

async function createNewConversation() {
  const agentId = selectedAgentId.value;
  if (!agentId) {
    message.warning($t('chat.conversation.selectAgentFirst'));
    return;
  }
  const created = await createConversationApi({
    agentId,
    title: $t('chat.conversation.newTitle', {
      time: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
    }),
  });
  await loadConversations(agentId);
  const createdConversationId = String(created?.id || '');
  const targetConversationId =
    createdConversationId || conversationSidebarItems.value[0]?.id || '';
  if (targetConversationId) {
    await selectConversation(targetConversationId);
  }
}

function startEditConversation(id: string, title?: string) {
  historyEditingId.value = id;
  historyEditingTitle.value = String(title || '');
}

function cancelEditConversation() {
  historyEditingId.value = '';
  historyEditingTitle.value = '';
}

async function confirmEditConversation(id: string) {
  const title = historyEditingTitle.value.trim();
  if (!title) {
    message.warning($t('chat.conversation.titleRequired'));
    return;
  }
  await updateConversationApi(id, { title });
  message.success($t('chat.conversation.titleUpdated'));
  await loadConversations(selectedAgentId.value);
  cancelEditConversation();
}

async function handleDeleteConversation(id: string, title?: string) {
  dialog.warning({
    closable: false,
    content: $t('common.messages.deleteConfirmContent', {
      name: title || $t('chat.conversation.untitled'),
    }),
    negativeText: $t('common.actions.cancel'),
    positiveText: $t('common.actions.confirmDelete'),
    title: $t('chat.conversation.deleteTitle'),
    onPositiveClick: async () => {
      await removeConversationApi(id);
      message.success($t('chat.conversation.deleteSuccess'));
      if (selectedConversationId.value === id) {
        await selectConversation('');
      }
      await loadConversations(selectedAgentId.value);
    },
  });
}

async function prepareRuntime() {
  await initializeRuntime();
  if (routeAgentId.value) {
    await selectAgent(routeAgentId.value);
    const firstConversationId =
      selectedConversationId.value || conversationSidebarItems.value[0]?.id || '';
    if (firstConversationId) {
      await selectConversation(firstConversationId);
    }
  }
}

function goBackToMarket() {
  void router.push('/market');
}

watch(routeAgentId, async (value) => {
  if (value) {
    await selectAgent(value);
    const firstConversationId =
      selectedConversationId.value || conversationSidebarItems.value[0]?.id || '';
    if (firstConversationId) {
      await selectConversation(firstConversationId);
    }
  }
});

watch(
  filteredConversationItems,
  async (items) => {
    if (!selectedConversationId.value && items.length > 0) {
      await selectConversation(items[0]!.id);
    }
  },
  { immediate: true },
);

watch([marketChatMessages, sending], syncChatMessages, {
  deep: true,
  flush: 'post',
  immediate: true,
});

onMounted(prepareRuntime);
</script>

<template>
  <Page>
    <div class="market-chat flex h-full min-h-0 flex-col gap-3">
      <!-- 页头：应用信息 + 返回市场 -->
      <section
        class="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-card px-4 py-3"
      >
        <div class="flex min-w-0 items-center gap-3">
          <NButton
            v-tippy="$t('common.actions.back')"
            :aria-label="$t('common.actions.back')"
            circle
            quaternary
            size="small"
            @click="goBackToMarket"
          >
            <template #icon>
              <ArrowLeft class="size-4" />
            </template>
          </NButton>
          <img
            v-if="resolveAgentPreviewIcon(selectedAgent as AigcAgent)"
            alt=""
            class="size-9 shrink-0 rounded-lg border border-border object-cover"
            :src="resolveAgentPreviewIcon(selectedAgent as AigcAgent)"
          />
          <div
            v-else
            class="flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/15 bg-primary/8 text-primary"
          >
            <Bot class="size-4.5" />
          </div>
          <div class="min-w-0">
            <div class="flex flex-wrap items-center gap-2">
              <h1
                class="truncate text-lg font-semibold tracking-tight text-foreground"
              >
                {{
                  selectedAgent?.agentName ||
                  $t('chat.conversation.defaultAgentName')
                }}
              </h1>
              <LcStatusTag
                v-if="selectedAgent"
                :label="agentStatusLabel"
                :type="agentStatusType"
              />
            </div>
            <p class="mt-0.5 max-w-3xl truncate text-xs text-muted-foreground">
              {{ agentDescription }}
            </p>
          </div>
        </div>

        <div class="flex shrink-0 items-center gap-2">
          <NInput
            v-model:value="historyKeyword"
            class="w-44"
            clearable
            size="small"
            :placeholder="$t('chat.conversation.searchPlaceholder')"
          >
            <template #prefix>
              <Search class="size-3.5 text-muted-foreground" />
            </template>
          </NInput>
          <NButton secondary size="small" @click="goBackToMarket">
            {{ $t('menu.market') }}
          </NButton>
        </div>
      </section>

      <div class="grid min-h-0 flex-1 gap-3 lg:grid-cols-[288px_minmax(0,1fr)]">
        <!-- 会话列表 -->
        <aside
          class="flex min-h-0 flex-col overflow-hidden rounded-xl border border-border bg-card"
        >
          <div
            class="flex shrink-0 items-center justify-between gap-2 border-b border-border/60 px-4 py-3"
          >
            <div class="flex min-w-0 items-center gap-2">
              <span class="h-3 w-0.5 rounded-full bg-primary"></span>
              <div class="text-sm font-semibold text-foreground">
                {{ $t('chat.conversation.history') }}
              </div>
              <span
                class="rounded-md border border-border/60 bg-muted/40 px-1.5 py-0.5 text-[10px] font-medium tabular-nums text-muted-foreground"
              >
                {{ filteredConversationItems.length }}
              </span>
            </div>
            <NButton
              v-tippy="$t('chat.conversation.newConversation')"
              :aria-label="$t('chat.conversation.newConversation')"
              circle
              quaternary
              size="small"
              @click="createNewConversation"
            >
              <template #icon>
                <Plus class="size-4" />
              </template>
            </NButton>
          </div>

          <div class="shrink-0 px-3 pt-3">
            <NButton block size="small" type="primary" @click="createNewConversation">
              <template #icon>
                <Plus class="size-4" />
              </template>
              {{ $t('chat.conversation.newConversation') }}
            </NButton>
          </div>

          <div class="min-h-0 flex-1 space-y-1.5 overflow-y-auto px-3 py-3">
            <div
              v-for="item in filteredConversationItems"
              :key="item.id"
              :class="
                item.id === selectedConversationId
                  ? 'border-primary/35 bg-primary/[0.06]'
                  : 'border-transparent hover:border-border/70 hover:bg-muted/30'
              "
              class="group rounded-lg border px-2.5 py-2 transition-colors"
            >
              <div
                v-if="historyEditingId !== item.id"
                class="flex items-center gap-1"
              >
                <button
                  class="min-w-0 flex-1 cursor-pointer text-left"
                  type="button"
                  @click="selectConversation(item.id)"
                >
                  <div
                    :class="
                      item.id === selectedConversationId
                        ? 'text-primary'
                        : 'text-foreground'
                    "
                    class="truncate text-[13px] font-medium"
                  >
                    {{ item.title }}
                  </div>
                  <div
                    class="mt-0.5 truncate text-[10px] tabular-nums text-muted-foreground"
                  >
                    {{ item.meta || '--' }}
                  </div>
                </button>
                <div
                  class="flex shrink-0 items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100"
                >
                  <NButton
                    v-tippy="$t('common.actions.edit')"
                    :aria-label="$t('common.actions.edit')"
                    circle
                    quaternary
                    size="tiny"
                    @click="startEditConversation(item.id, item.title)"
                  >
                    <SquarePen class="size-3" />
                  </NButton>
                  <NButton
                    v-tippy="$t('common.actions.delete')"
                    :aria-label="$t('common.actions.delete')"
                    circle
                    quaternary
                    size="tiny"
                    type="error"
                    @click="handleDeleteConversation(item.id, item.title)"
                  >
                    <Trash2 class="size-3" />
                  </NButton>
                </div>
              </div>
              <div v-else class="flex items-center gap-1">
                <NInput
                  v-model:value="historyEditingTitle"
                  :placeholder="$t('chat.conversation.titlePlaceholder')"
                  size="small"
                />
                <NButton
                  circle
                  size="tiny"
                  type="primary"
                  @click="confirmEditConversation(item.id)"
                >
                  <Check class="size-3" />
                </NButton>
                <NButton
                  circle
                  quaternary
                  size="tiny"
                  @click="cancelEditConversation"
                >
                  <X class="size-3" />
                </NButton>
              </div>
            </div>

            <div
              v-if="filteredConversationItems.length === 0"
              class="rounded-lg border border-dashed border-border px-3 py-6 text-center text-[11px] leading-5 text-muted-foreground"
            >
              {{ $t('market.sessionEmpty') }}
            </div>
          </div>
        </aside>

        <!-- 会话区 -->
        <section
          class="market-chat-panel flex h-full min-h-0 min-w-0 flex-col overflow-hidden rounded-xl border border-border bg-card"
        >
          <div
            class="flex shrink-0 items-center justify-between gap-3 border-b border-border/60 px-4 py-2.5"
          >
            <div class="min-w-0">
              <div class="flex items-center gap-2">
                <span class="h-3 w-0.5 rounded-full bg-primary"></span>
                <div class="text-sm font-semibold text-foreground">
                  {{ $t('market.sessionHeading') }}
                </div>
                <span
                  class="max-w-[260px] truncate rounded-md border border-border/60 bg-muted/30 px-1.5 py-0.5 text-[10px] text-muted-foreground"
                >
                  {{ activeConversationTitle }}
                </span>
              </div>
              <div class="mt-0.5 truncate pl-2.5 text-[10px] text-muted-foreground">
                {{
                  selectedAgent?.agentName ||
                  $t('chat.conversation.defaultAgentName')
                }}
              </div>
            </div>
            <div class="flex shrink-0 items-center gap-1.5 text-[10px] text-muted-foreground">
              <span
                :class="sending ? 'animate-pulse bg-primary' : 'bg-emerald-500'"
                class="size-1.5 rounded-full"
              ></span>
              <span v-if="sending" class="tabular-nums">
                {{ $t('chat.message.thinking') }}
              </span>
            </div>
          </div>
          <div class="min-h-0 flex-1">
            <LcChat
              ref="chatRef"
              :assistant-icon="
                resolveAgentPreviewIcon(selectedAgent as AigcAgent)
              "
              :disabled="sending"
              :empty-title="
                selectedAgent?.welcomeMessage || $t('chat.empty.welcome')
              "
              :loading="messageLoading"
              :placeholder="$t('chat.composer.inputMessage')"
              :show-attachment="true"
              :show-model="false"
              :suggestions="marketSuggestions"
              @send="handleSubmitMessage"
            >
              <template #empty-head>
                <div class="flex flex-col items-center gap-3 text-center">
                  <img
                    v-if="resolveAgentPreviewIcon(selectedAgent as AigcAgent)"
                    alt=""
                    class="size-12 rounded-xl border border-border object-cover"
                    :src="resolveAgentPreviewIcon(selectedAgent as AigcAgent)"
                  />
                  <div
                    v-else
                    class="flex size-12 items-center justify-center rounded-xl border border-primary/15 bg-primary/8 text-primary"
                  >
                    <Bot class="size-6" />
                  </div>
                  <div class="text-base font-semibold text-foreground">
                    {{
                      selectedAgent?.welcomeMessage || $t('chat.empty.welcome')
                    }}
                  </div>
                </div>
              </template>
            </LcChat>
          </div>
        </section>
      </div>
    </div>
  </Page>
</template>

<style scoped>
/* 消息气泡 / 操作条 / 输入区的轻量视觉打磨：只作用于当前页面的 LcChat */
.market-chat-panel :deep([class*='rounded-[14px]']) {
  border-radius: 16px;
}

.market-chat-panel :deep([class*='rounded-[14px]'][class*='bg-primary']) {
  background-image: linear-gradient(
    135deg,
    hsl(var(--primary)) 0%,
    hsl(212 100% 54%) 100%
  );
  box-shadow: 0 6px 18px -12px hsl(var(--primary) / 60%);
}

.market-chat-panel :deep([class*='rounded-[14px]'][class*='bg-muted/60']) {
  background-color: hsl(var(--card));
  border: 1px solid hsl(var(--border) / 80%);
  box-shadow: 0 1px 2px hsl(var(--foreground) / 4%);
}

.market-chat-panel :deep([class*='group-hover:opacity-100']) {
  opacity: 0.5;
}

.market-chat-panel :deep([class*='group-hover:opacity-100']:hover) {
  opacity: 1;
}

.market-chat-panel :deep(.composer) {
  background: hsl(var(--muted) / 30%);
  border-color: hsl(var(--border) / 85%);
  border-radius: 16px;
}

.market-chat-panel :deep(.composer:focus-within) {
  background: hsl(var(--background));
}
</style>
