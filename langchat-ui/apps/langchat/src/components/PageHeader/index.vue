<script lang="ts" setup>
/**
 * 列表页统一页头（风格 A）
 * 标题 20px/600 + 一行说明，右侧操作区由 #actions 插槽提供。
 */
interface Props {
  description?: string;
  title: string;
}

withDefaults(defineProps<Props>(), {
  description: '',
});
</script>

<template>
  <div class="flex flex-wrap items-start justify-between gap-x-4 gap-y-2">
    <div class="min-w-0">
      <div class="flex min-w-0 items-center gap-2">
        <slot name="icon"></slot>
        <h1
          class="truncate text-[20px] font-semibold leading-7 tracking-tight text-foreground"
        >
          {{ title }}
        </h1>
        <slot name="badge"></slot>
      </div>
      <p
        v-if="description"
        class="mt-1 truncate text-xs leading-5 text-muted-foreground"
      >
        {{ description }}
      </p>
    </div>
    <div
      v-if="$slots.actions"
      class="flex shrink-0 flex-wrap items-center gap-2"
    >
      <slot name="actions"></slot>
    </div>
  </div>
</template>
