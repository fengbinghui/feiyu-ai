<!--
  - © 2026 肥鱼AI. 版权所有.
  -->
<script lang="ts" setup>
import PageHeader from '#/components/PageHeader/index.vue';

/**
 * 系统管理通用页面骨架（风格 A）：
 * 页头（标题 + 说明 + 右侧操作）→ 搜索筛选工具条 → 表格白卡。
 */
interface Props {
  description?: string;
  title?: string;
}

withDefaults(defineProps<Props>(), {
  description: '',
  title: '',
});
</script>

<template>
  <div class="flex h-full min-h-0 flex-col gap-3">
    <PageHeader :description="description" :title="title">
      <template #actions>
        <slot name="actions"></slot>
      </template>
    </PageHeader>

    <div
      v-if="$slots.search"
      class="lc-surface shrink-0 px-4 py-3"
    >
      <div class="flex flex-wrap items-center gap-x-5 gap-y-3">
        <slot name="search"></slot>
      </div>
    </div>

    <div
      class="lc-surface flex min-h-0 flex-1 flex-col overflow-hidden"
    >
      <slot></slot>
    </div>
  </div>
</template>
