<script lang="ts" setup>
import type { SharedConversation } from '#/api/aigc/chat';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { ChevronLeft, CircleAlert, FileText, Printer } from '@vben/icons';
import { $t, i18n } from '@vben/locales';

import { NButton, NSpin } from 'naive-ui';

import { getSharedConversationApi } from '#/api/aigc/chat';

defineOptions({ name: 'Share' });

const router = useRouter();

const loading = ref(true);
const notFound = ref(false);
const data = ref<null | SharedConversation>(null);

const messages = computed(() =>
  (data.value?.messages ?? [])
    .filter((item) => {
      const role = String(item.role || '').toLowerCase();
      return role === 'user' || role === 'assistant';
    })
    .map((item, index) => ({
      content: String(item.content || '').trim(),
      createTime: item.createTime,
      id: `${index}-${item.role}`,
      role: String(item.role || '').toLowerCase(),
    }))
    .filter((item) => item.content),
);

const agentName = computed(
  () => data.value?.agentName || $t('chat.publicShare.agentFallback'),
);

const createdAtText = computed(() => {
  const value = data.value?.createdAt;
  if (!value) {
    return '';
  }
  const timestamp = value > 10_000_000_000 ? value : value * 1000;
  return new Date(timestamp).toLocaleString(
    i18n.global.locale.value || 'zh-CN',
    { hour12: false },
  );
});

function backHome() {
  void router.push('/');
}

function handlePrint() {
  window.print();
}

onMounted(async () => {
  const shareId = String(
    router.currentRoute.value.params.shareId || '',
  ).trim();
  if (!shareId) {
    notFound.value = true;
    loading.value = false;
    return;
  }
  try {
    const result = await getSharedConversationApi(shareId);
    if (!result || !result.title) {
      notFound.value = true;
      return;
    }
    data.value = result;
    document.title = `${result.title} - 肥鱼AI`;
  } catch {
    notFound.value = true;
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="share-page min-h-screen bg-background-deep">
    <div class="mx-auto flex min-h-screen w-full max-w-3xl flex-col px-4 py-6 sm:px-6">
      <!-- 品牌头 -->
      <header class="no-print flex items-center justify-between gap-3">
        <div class="flex min-w-0 items-center gap-2.5">
          <img alt="肥鱼AI" class="size-9 rounded-xl" src="/logo.svg" />
          <div class="min-w-0">
            <div class="text-sm font-semibold text-foreground">肥鱼AI</div>
            <div class="text-[10px] text-muted-foreground">
              {{ $t('chat.publicShare.poweredBy') }}
            </div>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <NButton v-if="data" secondary size="small" @click="backHome">
            <template #icon>
              <ChevronLeft class="size-3.5" />
            </template>
            {{ $t('chat.publicShare.backHome') }}
          </NButton>
          <NButton
            v-if="data"
            secondary
            size="small"
            @click="handlePrint"
          >
            <template #icon>
              <Printer class="size-3.5" />
            </template>
            {{ $t('chat.publicShare.print') }}
          </NButton>
        </div>
      </header>

      <!-- 加载态 -->
      <div
        v-if="loading"
        class="flex flex-1 items-center justify-center py-24"
      >
        <NSpin size="medium" />
        <span class="ml-3 text-sm text-muted-foreground">
          {{ $t('chat.publicShare.loading') }}
        </span>
      </div>

      <!-- 不存在 -->
      <div
        v-else-if="notFound"
        class="flex flex-1 flex-col items-center justify-center py-20 text-center"
      >
        <div
          class="flex size-14 items-center justify-center rounded-2xl border border-border bg-card text-muted-foreground"
        >
          <CircleAlert class="size-6" />
        </div>
        <h1 class="mt-4 text-xl font-semibold text-foreground">
          {{ $t('chat.publicShare.notFoundTitle') }}
        </h1>
        <p class="mt-2 max-w-md text-sm leading-6 text-muted-foreground">
          {{ $t('chat.publicShare.notFoundDescription') }}
        </p>
        <NButton class="mt-6" type="primary" @click="backHome">
          {{ $t('chat.publicShare.backHome') }}
        </NButton>
      </div>

      <!-- 分享内容 -->
      <template v-else-if="data">
        <section class="mt-6 rounded-xl border border-border bg-card px-5 py-5 shadow-sm sm:px-6">
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <h1 class="text-balance text-xl font-semibold text-foreground">
                {{ data.title }}
              </h1>
              <div
                class="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-muted-foreground"
              >
                <span class="inline-flex items-center gap-1.5">
                  <img
                    v-if="data.agentAvatar"
                    alt=""
                    class="size-4 rounded"
                    :src="data.agentAvatar"
                  />
                  <FileText v-else class="size-3.5" />
                  {{ agentName }}
                </span>
                <span v-if="createdAtText">{{ createdAtText }}</span>
              </div>
            </div>
            <div class="no-print shrink-0 text-[10px] text-muted-foreground">
              {{ $t('chat.publicShare.poweredBy') }}
            </div>
          </div>

          <div class="mt-5 flex flex-col gap-4">
            <div
              v-for="item in messages"
              :key="item.id"
              class="flex flex-col"
              :class="item.role === 'user' ? 'items-end' : 'items-start'"
            >
              <div class="mb-1 text-[10px] text-muted-foreground">
                {{
                  item.role === 'user'
                    ? $t('chat.publicShare.user')
                    : agentName
                }}
              </div>
              <div
                class="max-w-[86%] whitespace-pre-wrap break-words rounded-2xl px-3.5 py-2 text-sm leading-6"
                :class="
                  item.role === 'user'
                    ? 'bg-primary text-primary-foreground'
                    : 'border border-border/80 bg-muted/40 text-foreground'
                "
              >
                {{ item.content }}
              </div>
            </div>

            <div
              v-if="messages.length === 0"
              class="py-10 text-center text-sm text-muted-foreground"
            >
              {{ $t('chat.publicShare.noMessages') }}
            </div>
          </div>
        </section>

        <footer
          class="mt-6 flex flex-col items-center gap-1 pb-4 text-[11px] text-muted-foreground"
        >
          <div>{{ $t('chat.publicShare.poweredBy') }}</div>
          <div>© 2026 肥鱼AI</div>
        </footer>
      </template>
    </div>
  </div>
</template>

<style scoped>
@media print {
  .share-page {
    background: #fff !important;
    min-height: auto;
  }

  .share-page :deep(.no-print),
  .share-page .no-print {
    display: none !important;
  }

  .share-page :deep(section) {
    border: none !important;
    box-shadow: none !important;
    padding: 0 !important;
  }

  .share-page :deep(.bg-primary) {
    background: #eef4ff !important;
    color: #0b2a54 !important;
  }

  .share-page :deep(.bg-muted\/40) {
    background: #f7f8fa !important;
  }

  @page {
    margin: 16mm;
  }
}
</style>
