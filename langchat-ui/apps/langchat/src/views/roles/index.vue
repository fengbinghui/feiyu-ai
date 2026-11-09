<script setup lang="ts">
import type { VxeGridPropTypes } from '#/adapter/vxe-table';
import type { AigcMenuTreeNode } from '#/api/auth/menu';
import type { AigcRole, AigcRoleMenu } from '#/api/auth/role';

import { computed, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { Plus, RefreshCcw, Search } from '@vben/icons';
import { $t } from '@vben/locales';

import { NButton, NInput, NTag } from 'naive-ui';

import { dialog, message } from '#/adapter/naive';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { menuApi } from '#/api/auth/menu';
import { roleApi, roleMenuApi } from '#/api/auth/role';
import { formatRelativeTime } from '#/views/shared/aigc/time';
import ManageCard from '#/views/shared/auth/manage-card.vue';
import { buildMenuNameMap } from '#/views/shared/auth/menu-tree';

import RoleEdit from './edit.vue';

interface RoleFormPayload extends Partial<AigcRole> {
  menuIds?: string[];
}

const menuTree = ref<AigcMenuTreeNode[]>([]);
const roleMenus = ref<AigcRoleMenu[]>([]);
const saving = ref(false);
const showEdit = ref(false);
const currentItem = ref<null | RoleFormPayload>(null);

// 搜索条件：draft 为输入框草稿值，applied 为已生效值（工具栏刷新时沿用生效值）
const draftKeyword = ref('');
const appliedKeyword = ref('');

// 系统内置角色（如超管）不允许编辑与删除
const BUILTIN_ROLE_CODES = new Set(['ADMIN']);

function isBuiltinRole(role: AigcRole) {
  return BUILTIN_ROLE_CODES.has(String(role.code || '').toUpperCase());
}

function resolveMenuIds(roleId?: string) {
  return roleMenus.value
    .filter((item) => item.roleId === roleId)
    .map((item) => item.menuId ?? '')
    .filter(Boolean);
}

// 菜单授权列按角色聚合已授权菜单名称，模板里直接查表，避免同一次渲染重复计算
const menuLabelsByRole = computed<Record<string, string[]>>(() => {
  const nameMap = buildMenuNameMap(menuTree.value);
  const labels: Record<string, string[]> = {};
  for (const item of roleMenus.value) {
    const roleId = String(item.roleId ?? '');
    const menuId = String(item.menuId ?? '');
    if (!roleId || !menuId) {
      continue;
    }
    (labels[roleId] ||= []).push(nameMap[menuId] || menuId);
  }
  return labels;
});

function resolveMenuLabels(roleId?: string) {
  return (roleId && menuLabelsByRole.value[String(roleId)]) || [];
}

function handleSearch() {
  appliedKeyword.value = draftKeyword.value.trim();
  gridApi.reload();
}

function handleReset() {
  draftKeyword.value = '';
  appliedKeyword.value = '';
  gridApi.reload();
}

async function queryRoles(params: {
  page?: { currentPage: number; pageSize: number };
}) {
  const pageNo = params.page?.currentPage ?? 1;
  const pageSize = params.page?.pageSize ?? 10;
  const keyword = appliedKeyword.value.trim();

  // 菜单树与角色-菜单关系仍走全量 listTree()/list()，用于菜单授权列展示与编辑回填
  const [rolesPage, menuList, relations] = await Promise.all([
    roleApi.page({
      keyword: keyword || undefined,
      pageNo,
      pageSize,
    }),
    menuApi.listTree(),
    roleMenuApi.list(),
  ]);
  menuTree.value = menuList;
  roleMenus.value = relations;

  return {
    items: rolesPage.records,
    total: rolesPage.total,
  };
}

const gridColumns = computed<VxeGridPropTypes.Columns<AigcRole>>(() => [
  { type: 'seq', title: $t('roles.columns.seq'), width: 60 },
  {
    field: 'name',
    title: $t('roles.columns.name'),
    minWidth: 160,
    showOverflow: true,
  },
  {
    field: 'code',
    title: $t('roles.columns.code'),
    minWidth: 140,
    showOverflow: true,
  },
  {
    field: 'description',
    title: $t('roles.columns.description'),
    minWidth: 200,
    showOverflow: true,
  },
  {
    field: 'menuIds',
    title: $t('roles.columns.menuAuth'),
    minWidth: 280,
    showOverflow: false,
    slots: { default: 'menuColumn' },
  },
  {
    field: 'updateTime',
    title: $t('roles.columns.updateTime'),
    minWidth: 180,
    showOverflow: true,
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

const [Grid, gridApi] = useVbenVxeGrid<AigcRole>({
  gridOptions: {
    columns: gridColumns.value,
    height: 'auto',
    // 关闭单元格裁切，菜单授权列的多行标签才能撑开行高
    showOverflow: false,
    pagerConfig: {
      pageSize: 10,
      pageSizes: [10, 20, 50],
    },
    proxyConfig: {
      ajax: {
        query: queryRoles,
      },
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

function openEdit(item: AigcRole) {
  currentItem.value = {
    ...item,
    menuIds: resolveMenuIds(item.id),
  };
  showEdit.value = true;
}

async function handleDelete(item: AigcRole) {
  if (!item.id) {
    return;
  }
  const roleId = item.id;
  dialog.warning({
    closable: false,
    content: $t('common.messages.deleteConfirmContent', {
      name: item.name || item.code || $t('roles.card.unnamed'),
    }),
    negativeText: $t('common.actions.cancel'),
    positiveText: $t('common.actions.confirmDelete'),
    title: $t('roles.messages.deleteTitle'),
    onPositiveClick: async () => {
      await roleApi.remove(roleId);
      message.success($t('roles.messages.deleted'));
      await gridApi.reload();
    },
  });
}

async function handleSave(payload: RoleFormPayload) {
  saving.value = true;
  try {
    const nextMenuIds = [...new Set(payload.menuIds)];
    const rolePayload: Partial<AigcRole> = { ...payload };
    delete (rolePayload as RoleFormPayload).menuIds;

    let targetRoleId = currentItem.value?.id ?? '';
    if (currentItem.value?.id) {
      await roleApi.update(currentItem.value.id, rolePayload);
      message.success($t('roles.messages.updated'));
    } else {
      await roleApi.create(rolePayload);
      const roles = await roleApi.list();
      targetRoleId =
        roles.find((item) => item.code === rolePayload.code)?.id ?? '';
      message.success($t('roles.messages.created'));
    }

    if (targetRoleId) {
      await syncRoleMenus(targetRoleId, nextMenuIds);
    }
    showEdit.value = false;
    await gridApi.reload();
  } finally {
    saving.value = false;
  }
}

async function syncRoleMenus(roleId: string, nextMenuIds: string[]) {
  const currentMenuIds = roleMenus.value
    .filter((item) => item.roleId === roleId)
    .map((item) => item.menuId ?? '')
    .filter(Boolean);

  const addMenuIds = nextMenuIds.filter(
    (menuId) => !currentMenuIds.includes(menuId),
  );
  const removeMenuIds = currentMenuIds.filter(
    (menuId) => !nextMenuIds.includes(menuId),
  );

  await Promise.all([
    ...addMenuIds.map((menuId) =>
      roleMenuApi.create({
        menuId,
        roleId,
      }),
    ),
    ...removeMenuIds.map((menuId) =>
      roleMenuApi.remove({
        menuId,
        roleId,
      }),
    ),
  ]);
}
</script>

<template>
  <Page>
    <ManageCard
      :description="$t('roles.list.description')"
      :title="$t('roles.list.tableTitle')"
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
          {{ $t('roles.actions.create') }}
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
            :placeholder="$t('roles.list.keywordPlaceholder')"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <Search class="size-4 text-muted-foreground" />
            </template>
          </NInput>
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
        <template #menuColumn="{ row }">
          <div
            v-if="resolveMenuLabels(row.id).length > 0"
            class="flex flex-wrap gap-x-2 gap-y-2.5 py-2"
          >
            <NTag
              v-for="menu in resolveMenuLabels(row.id)"
              :key="menu"
              :bordered="false"
              round
              size="small"
              type="info"
            >
              {{ menu }}
            </NTag>
          </div>
          <span v-else class="text-xs text-muted-foreground">
            {{ $t('roles.list.unauthorizedMenus') }}
          </span>
        </template>

        <template #actionColumn="{ row }">
          <div class="lc-row-actions">
            <NButton
              v-tippy="
                isBuiltinRole(row)
                  ? $t('roles.list.builtinRoleEditTip')
                  : $t('roles.list.editRole')
              "
              :disabled="isBuiltinRole(row)"
              size="small"
              text
              type="primary"
              @click="openEdit(row)"
            >
              {{ $t('common.actions.edit') }}
            </NButton>
            <span class="h-3 w-px bg-border"></span>
            <NButton
              v-tippy="
                isBuiltinRole(row)
                  ? $t('roles.list.builtinRoleDeleteTip')
                  : $t('roles.list.deleteRole')
              "
              :disabled="isBuiltinRole(row)"
              size="small"
              text
              type="error"
              @click="handleDelete(row)"
            >
              {{ $t('common.actions.delete') }}
            </NButton>
          </div>
        </template>
      </Grid>
    </ManageCard>

    <RoleEdit
      v-model:show="showEdit"
      :menu-tree="menuTree"
      :model-value="currentItem"
      :saving="saving"
      @save="handleSave"
    />
  </Page>
</template>
