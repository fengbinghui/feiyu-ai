<script setup lang="ts">
import type { VxeGridPropTypes } from '#/adapter/vxe-table';
import type { AigcMenu, AigcMenuTreeNode } from '#/api/auth/menu';

import { computed, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { Plus, RefreshCcw, Search } from '@vben/icons';
import { $t } from '@vben/locales';

import { NButton, NInput, NSelect } from 'naive-ui';

import { dialog, message } from '#/adapter/naive';
import LcStatusTag from '#/components/LcStatusTag/index.vue';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { menuApi } from '#/api/auth/menu';
import { formatRelativeTime } from '#/views/shared/aigc/time';
import ManageCard from '#/views/shared/auth/manage-card.vue';
import { filterMenuTree } from '#/views/shared/auth/menu-tree';
import {
  findAuthOptionLabel,
  menuTypeOptions,
} from '#/views/shared/auth/options';

import MenuEdit from './edit.vue';

const saving = ref(false);
const showEdit = ref(false);
const currentItem = ref<null | Partial<AigcMenu>>(null);
const allMenus = ref<AigcMenuTreeNode[]>([]);

// 搜索条件：draft 为输入框草稿值，applied 为已生效值（工具栏刷新时沿用生效值）
const draftKeyword = ref('');
const draftType = ref<null | string>(null);
const appliedKeyword = ref('');
const appliedType = ref<null | string>(null);

function handleSearch() {
  appliedKeyword.value = draftKeyword.value.trim();
  appliedType.value = draftType.value;
  gridApi.reload();
}

function handleReset() {
  draftKeyword.value = '';
  draftType.value = null;
  appliedKeyword.value = '';
  appliedType.value = null;
  gridApi.reload();
}

async function queryMenus(_params: {
  page?: { currentPage: number; pageSize: number };
}) {
  const [filtered, tree] = await Promise.all([
    menuApi.listTree({
      keyword: appliedKeyword.value.trim() || undefined,
      type: appliedType.value || undefined,
    }),
    menuApi.listTree(),
  ]);
  allMenus.value = tree;
  return {
    items: filtered,
    total: filtered.length,
  };
}

const gridColumns = computed<VxeGridPropTypes.Columns<AigcMenuTreeNode>>(() => [
  {
    field: 'name',
    treeNode: true,
    title: $t('menus.columns.name'),
    minWidth: 220,
  },
  {
    field: 'type',
    title: $t('menus.columns.type'),
    width: 100,
    formatter: ({ cellValue }: { cellValue: number | string }) =>
      findAuthOptionLabel(menuTypeOptions(), cellValue),
  },
  {
    field: 'path',
    title: $t('menus.columns.path'),
    minWidth: 180,
  },
  {
    field: 'perms',
    title: $t('menus.columns.perms'),
    minWidth: 180,
  },
  {
    field: 'component',
    title: $t('menus.columns.component'),
    minWidth: 180,
  },
  {
    field: 'orderNo',
    title: $t('menus.columns.orderNo'),
    width: 80,
  },
  {
    field: 'isShow',
    title: $t('menus.columns.isShow'),
    width: 80,
    slots: { default: 'showColumn' },
  },
  {
    field: 'isKeepalive',
    title: $t('menus.columns.isKeepalive'),
    width: 80,
    slots: { default: 'keepaliveColumn' },
  },
  {
    field: 'isDisabled',
    title: $t('menus.columns.isDisabled'),
    width: 80,
    slots: { default: 'disabledColumn' },
  },
  {
    field: 'updateTime',
    title: $t('menus.columns.updateTime'),
    minWidth: 180,
    formatter: ({ cellValue }: { cellValue: number }) =>
      cellValue ? formatRelativeTime(cellValue) : '--',
  },
  {
    field: 'actions',
    fixed: 'right',
    slots: { default: 'actionColumn' },
    title: $t('common.labels.actions'),
    width: 150,
  },
]);

const [Grid, gridApi] = useVbenVxeGrid<AigcMenuTreeNode>({
  gridOptions: {
    columns: gridColumns.value,
    height: 'auto',
    pagerConfig: {
      enabled: false,
    } as any,
    proxyConfig: {
      ajax: {
        query: queryMenus,
      },
    },
    rowConfig: {
      isHover: true,
      keyField: 'id',
    },
    treeConfig: {
      transform: false,
    },
  },
});

watch(
  gridColumns,
  (columns) => {
    gridApi.setGridOptions({ columns });
  },
  { immediate: true },
);

function openCreate() {
  currentItem.value = null;
  showEdit.value = true;
}

function openEdit(item: AigcMenu) {
  currentItem.value = item;
  showEdit.value = true;
}

async function handleDelete(item: AigcMenu) {
  if (!item.id) {
    return;
  }
  const menuId = item.id;
  dialog.warning({
    closable: false,
    content: $t('common.messages.deleteConfirmContent', {
      name: item.name || $t('menus.card.unnamed'),
    }),
    negativeText: $t('common.actions.cancel'),
    positiveText: $t('common.actions.confirmDelete'),
    title: $t('menus.messages.deleteTitle'),
    onPositiveClick: async () => {
      await menuApi.remove(menuId);
      message.success($t('menus.messages.deleted'));
      await gridApi.reload();
    },
  });
}

async function handleSave(payload: Partial<AigcMenu>) {
  saving.value = true;
  try {
    if (currentItem.value?.id) {
      await menuApi.update(currentItem.value.id, payload);
      message.success($t('menus.messages.updated'));
    } else {
      await menuApi.create(payload);
      message.success($t('menus.messages.created'));
    }
    showEdit.value = false;
    await gridApi.reload();
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <Page>
    <ManageCard
      :description="$t('menus.list.description')"
      :title="$t('menus.list.tableTitle')"
    >
      <template #actions>
        <NButton secondary @click="() => gridApi.reload()">
          <template #icon>
            <RefreshCcw class="size-4" />
          </template>
          {{ $t('common.actions.refresh') }}
        </NButton>
        <NButton type="primary" @click="openCreate">
          <template #icon>
            <Plus class="size-4" />
          </template>
          {{ $t('menus.actions.create') }}
        </NButton>
      </template>

      <template #search>
        <div class="flex items-center gap-2">
          <span
            class="shrink-0 text-xs font-medium text-muted-foreground"
          >{{ $t('common.labels.keyword') }}</span>
          <NInput
            v-model:value="draftKeyword"
            class="lc-filter-input w-[280px]"
            clearable
            :placeholder="$t('menus.list.keywordPlaceholder')"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <Search class="size-4 text-muted-foreground" />
            </template>
          </NInput>
        </div>
        <div class="flex items-center gap-2">
          <span
            class="shrink-0 text-xs font-medium text-muted-foreground"
          >{{ $t('menus.form.type') }}</span>
          <NSelect
            v-model:value="draftType"
            :options="menuTypeOptions()"
            clearable
            :placeholder="$t('menus.list.typePlaceholder')"
            :style="{ width: '160px' }"
          />
        </div>
        <div class="ml-auto flex items-center gap-2">
          <NButton secondary @click="handleReset">
            {{ $t('common.actions.reset') }}
          </NButton>
          <NButton type="primary" @click="handleSearch">
            {{ $t('common.actions.search') }}
          </NButton>
        </div>
      </template>

      <Grid class="min-h-0 flex-1" grid-class="px-3 pb-3 pt-2">
        <template #showColumn="{ row }">
          <LcStatusTag
            :label="
              row.isShow === false
                ? $t('menus.list.hide')
                : $t('menus.list.show')
            "
            :type="row.isShow === false ? 'warning' : 'success'"
          />
        </template>

        <template #keepaliveColumn="{ row }">
          <LcStatusTag
            :label="
              row.isKeepalive
                ? $t('common.status.yes')
                : $t('common.status.no')
            "
            :type="row.isKeepalive ? 'primary' : 'default'"
          />
        </template>

        <template #disabledColumn="{ row }">
          <LcStatusTag
            :label="
              row.isDisabled
                ? $t('common.status.yes')
                : $t('common.status.no')
            "
            :type="row.isDisabled ? 'error' : 'success'"
          />
        </template>

        <template #actionColumn="{ row }">
          <div class="lc-row-actions">
            <NButton size="small" text type="primary" @click="openEdit(row)">
              {{ $t('common.actions.edit') }}
            </NButton>
            <span class="h-3 w-px bg-border"></span>
            <NButton size="small" text type="error" @click="handleDelete(row)">
              {{ $t('common.actions.delete') }}
            </NButton>
          </div>
        </template>
      </Grid>
    </ManageCard>

    <MenuEdit
      v-model:show="showEdit"
      :menu-tree="filterMenuTree(allMenus, currentItem?.id)"
      :model-value="currentItem"
      :saving="saving"
      @save="handleSave"
    />
  </Page>
</template>
