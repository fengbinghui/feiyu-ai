<script lang="ts" setup>
import type {
  AigcDatasource,
  ColumnStructure,
  TableStructure,
} from '#/api/aigc/datasource';

import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import {
  ArrowLeft,
  Check,
  ChevronDown,
  ChevronRight,
  Database,
  Download,
  Save,
  Trash2,
} from '@vben/icons';
import { $t } from '@vben/locales';

import { NButton, NEmpty, NInput, NSpin, NSwitch, NTag } from 'naive-ui';

import { message } from '#/adapter/naive';
import type { VxeGridPropTypes } from '#/adapter/vxe-table';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  getDataSource,
  getDataSourceStructure,
  introspectDataSource,
  saveDataSourceStructure,
} from '#/api/aigc/datasource';
import LcStatusTag from '#/components/LcStatusTag/index.vue';

import { getDatasourceTypeMeta } from './datasource-meta';

interface ManagedColumn extends ColumnStructure {
  originalName?: string;
}

interface ManagedTable extends TableStructure {
  enabled?: boolean;
  imported?: boolean;
  sourceTableName?: string;
}

interface ColumnRow extends ManagedColumn {
  rowKey: string;
  tableName: string;
}

const route = useRoute();
const router = useRouter();
const datasourceId = computed(() => String(route.params.id || ''));

const loading = ref(false);
const savingStructure = ref(false);
const datasource = ref<AigcDatasource | null>(null);
const structure = ref<TableStructure[]>([]);
const customStructure = ref<ManagedTable[]>([]);
const selectedTable = ref('');
const selectedColumn = ref('');
const expandedTables = ref<Record<string, boolean>>({});
const activeTab = ref<'custom' | 'original'>('original');

const datasourceTypeMeta = computed(() =>
  getDatasourceTypeMeta(datasource.value?.dbType),
);
const importedTableMap = computed(() => {
  const map = new Map<string, ManagedTable>();
  for (const table of customStructure.value) {
    map.set(table.sourceTableName || table.tableName, table);
  }
  return map;
});
const enabledTableCount = computed(
  () => customStructure.value.filter((table) => table.enabled !== false).length,
);
const pendingImportCount = computed(
  () =>
    structure.value.filter(
      (table) => !importedTableMap.value.has(table.tableName),
    ).length,
);
const sourceTables = computed<TableStructure[]>(() =>
  activeTab.value === 'custom' ? customStructure.value : structure.value,
);
const selectedTableMeta = computed(() =>
  sourceTables.value.find((table) => table.tableName === selectedTable.value),
);
const selectedManagedTable = computed(() =>
  activeTab.value === 'custom' && selectedTable.value !== '__all__'
    ? customStructure.value.find(
        (table) => table.tableName === selectedTable.value,
      ) || null
    : null,
);
const selectedLabel = computed(() => {
  if (!selectedTable.value || selectedTable.value === '__all__') {
    return activeTab.value === 'custom'
      ? $t('datasource.detail.allImportedTables')
      : $t('datasource.detail.allOriginalTables');
  }
  return selectedColumn.value
    ? $t('datasource.detail.selectedLabelWithColumn', {
        column: selectedColumn.value,
        table: selectedTable.value,
      })
    : $t('datasource.detail.selectedLabelAllFields', {
        table: selectedTable.value,
      });
});
const currentRows = computed<ColumnRow[]>(() => {
  const tables =
    selectedTable.value === '__all__'
      ? sourceTables.value
      : sourceTables.value.filter(
          (table) => table.tableName === selectedTable.value,
        );
  const rows = tables.flatMap((table) =>
    table.columns.map(
      (column) =>
        Object.assign(column, {
          rowKey: `${table.tableName}.${column.name}`,
          tableName: table.tableName,
        }) as ColumnRow,
    ),
  );
  return selectedColumn.value
    ? rows.filter(
        (row) =>
          row.name === selectedColumn.value ||
          row.originalName === selectedColumn.value,
      )
    : rows;
});

/** 页面顶部概览指标：仅做展示层汇总，不参与任何数据加载逻辑。 */
const statTiles = computed(() => [
  {
    label: $t('datasource.detail.originalTables'),
    value: structure.value.length,
    tone: 'primary',
  },
  {
    label: $t('datasource.detail.importedTables'),
    value: customStructure.value.length,
    tone: 'info',
  },
  {
    label: $t('datasource.detail.retrievalEnabled'),
    value: enabledTableCount.value,
    tone: 'success',
  },
  {
    label: $t('common.labels.status'),
    value: datasource.value?.enabled
      ? $t('common.status.enabled')
      : $t('common.status.disabled'),
    tone: 'default',
  },
]);

const originalColumns = computed<VxeGridPropTypes.Columns<ColumnRow>>(() => [
  {
    field: 'tableName',
    title: $t('datasource.detail.columns.tableName'),
    minWidth: 180,
  },
  {
    field: 'name',
    title: $t('datasource.detail.columns.originalName'),
    minWidth: 180,
  },
  {
    field: 'type',
    title: $t('datasource.detail.columns.originalType'),
    width: 140,
  },
  {
    field: 'size',
    title: $t('datasource.detail.columns.length'),
    width: 90,
    formatter: ({ cellValue }) => cellValue ?? '--',
  },
  {
    field: 'primaryKey',
    title: $t('datasource.detail.columns.primaryKey'),
    width: 80,
    formatter: ({ cellValue }) =>
      cellValue ? $t('common.status.yes') : $t('common.status.no'),
  },
  {
    field: 'nullable',
    title: $t('datasource.detail.columns.nullable'),
    width: 80,
    formatter: ({ cellValue }) =>
      cellValue ? $t('common.status.yes') : $t('common.status.no'),
  },
  {
    field: 'comment',
    title: $t('datasource.detail.columns.comment'),
    minWidth: 220,
    formatter: ({ cellValue }) => cellValue || '--',
  },
]);
const customColumns = computed<VxeGridPropTypes.Columns<ColumnRow>>(() => [
  {
    field: 'tableName',
    title: $t('datasource.detail.columns.importedTable'),
    minWidth: 160,
  },
  {
    field: 'name',
    title: $t('datasource.detail.columns.aiName'),
    minWidth: 190,
    slots: { default: 'customName' },
  },
  {
    field: 'type',
    title: $t('datasource.detail.columns.aiType'),
    minWidth: 160,
    slots: { default: 'customType' },
  },
  {
    field: 'primaryKey',
    title: $t('datasource.detail.columns.primaryKey'),
    width: 80,
    formatter: ({ cellValue }) =>
      cellValue ? $t('common.status.yes') : $t('common.status.no'),
  },
  {
    field: 'comment',
    title: $t('datasource.detail.columns.aiComment'),
    minWidth: 300,
    slots: { default: 'customComment' },
  },
]);

const [Grid, gridApi] = useVbenVxeGrid<ColumnRow>({
  class: 'bg-transparent shadow-none',
  gridClass: 'px-0 pb-0',
  gridOptions: {
    border: false,
    columns: originalColumns.value,
    data: [],
    minHeight: 260,
    rowConfig: { keyField: 'rowKey' },
    showOverflow: true,
    stripe: true,
  },
});

const gridDataKey = computed(() =>
  sourceTables.value
    .map((table) => `${table.tableName}:${table.columns.length}`)
    .join('|'),
);

watch(
  [
    selectedTable,
    selectedColumn,
    activeTab,
    gridDataKey,
    originalColumns,
    customColumns,
  ],
  () => {
    gridApi.setGridOptions({
      columns:
        activeTab.value === 'custom'
          ? customColumns.value
          : originalColumns.value,
      data: currentRows.value,
    });
  },
  { immediate: true },
);

function normalizeCustomTables(tables: TableStructure[]): ManagedTable[] {
  return tables
    .filter((table) => (table as ManagedTable).imported === true)
    .map((table) => ({
      ...table,
      enabled: (table as ManagedTable).enabled !== false,
      imported: true,
      sourceTableName:
        (table as ManagedTable).sourceTableName || table.tableName,
      columns: table.columns.map((column) => ({
        ...column,
        originalName: (column as ManagedColumn).originalName || column.name,
      })),
    }));
}

function ensureSelection() {
  if (sourceTables.value.length === 0) {
    selectedTable.value = '';
    selectedColumn.value = '';
    return;
  }
  if (
    selectedTable.value !== '__all__' &&
    !sourceTables.value.some((table) => table.tableName === selectedTable.value)
  ) {
    selectedTable.value = sourceTables.value[0]?.tableName || '';
  }
  if (
    selectedColumn.value &&
    !selectedTableMeta.value?.columns.some(
      (column) =>
        column.name === selectedColumn.value ||
        (column as ManagedColumn).originalName === selectedColumn.value,
    )
  ) {
    selectedColumn.value = '';
  }
}

function selectTable(tableName: string, columnName = '') {
  selectedTable.value = tableName;
  selectedColumn.value = columnName;
}

function toggleTable(tableName: string) {
  expandedTables.value[tableName] = !expandedTables.value[tableName];
}

function expandAllTables() {
  for (const table of sourceTables.value) {
    expandedTables.value[table.tableName] = true;
  }
}

function switchTab(tab: 'custom' | 'original') {
  activeTab.value = tab;
  selectedColumn.value = '';
  ensureSelection();
}

function importTable(table: TableStructure) {
  if (importedTableMap.value.has(table.tableName)) {
    const imported = importedTableMap.value.get(table.tableName);
    if (imported) imported.enabled = true;
    switchTab('custom');
    selectTable(imported?.tableName || table.tableName);
    return;
  }
  const imported: ManagedTable = {
    ...JSON.parse(JSON.stringify(table)),
    enabled: true,
    imported: true,
    sourceTableName: table.tableName,
    columns: table.columns.map((column) => ({
      ...JSON.parse(JSON.stringify(column)),
      originalName: column.name,
    })),
  };
  customStructure.value.push(imported);
  switchTab('custom');
  selectTable(imported.tableName);
  message.success(
    $t('datasource.messages.tableImported', { table: table.tableName }),
  );
}

function removeImportedTable(table: TableStructure) {
  customStructure.value = customStructure.value.filter(
    (item) => item !== table,
  );
  ensureSelection();
  message.success(
    $t('datasource.messages.tableRemoved', { table: table.tableName }),
  );
}

function toggleSelectedTableEnabled(enabled: boolean) {
  if (selectedManagedTable.value) {
    selectedManagedTable.value.enabled = enabled;
  }
}

function removeSelectedTable() {
  if (selectedManagedTable.value) {
    removeImportedTable(selectedManagedTable.value);
  }
}

async function loadDatasource() {
  if (!datasourceId.value) return;
  loading.value = true;
  try {
    datasource.value = await getDataSource(datasourceId.value);
  } catch (error) {
    message.error(
      $t('datasource.messages.datasourceLoadFailed', {
        message: (error as Error)?.message || $t('errors.unknown'),
      }),
    );
    loading.value = false;
    return;
  }
  try {
    structure.value =
      (await introspectDataSource(datasourceId.value))?.tables || [];
  } catch (error) {
    message.error(
      $t('datasource.messages.structureLoadFailed', {
        message: (error as Error)?.message || $t('errors.unknown'),
      }),
    );
  }
  try {
    const saved = await getDataSourceStructure(datasourceId.value);
    if (saved?.structureJson) {
      const parsed = JSON.parse(saved.structureJson);
      customStructure.value = normalizeCustomTables(
        Array.isArray(parsed?.tables) ? parsed.tables : [],
      );
    }
  } catch (error) {
    message.error(
      $t('datasource.messages.customStructureLoadFailed', {
        message: (error as Error)?.message || $t('errors.unknown'),
      }),
    );
  } finally {
    ensureSelection();
    loading.value = false;
  }
}

async function handleSaveStructure() {
  if (!datasourceId.value) return;
  savingStructure.value = true;
  try {
    await saveDataSourceStructure(
      datasourceId.value,
      JSON.stringify({ tables: customStructure.value }),
    );
    message.success($t('datasource.messages.structureSaved'));
  } catch (error) {
    message.error(
      $t('common.messages.saveFailed', {
        message: (error as Error)?.message || $t('errors.unknown'),
      }),
    );
  } finally {
    savingStructure.value = false;
  }
}

function goBack() {
  void router.push('/datasources');
}

onMounted(loadDatasource);
</script>

<template>
  <Page content-class="p-4" header-class="px-4 py-3.5">
    <template #title>
      <div class="flex min-w-0 items-center gap-3">
        <span
          class="flex size-9 shrink-0 items-center justify-center rounded-lg border border-border/70 bg-white p-1.5 shadow-sm"
        >
          <img
            :alt="`${datasourceTypeMeta.label} logo`"
            class="size-full object-contain"
            :src="datasourceTypeMeta.icon"
          />
        </span>
        <div class="min-w-0">
          <div class="flex items-center gap-2">
            <span class="truncate text-base font-semibold text-foreground">
              {{ datasource?.name || $t('datasource.detail.fallbackTitle') }}
            </span>
            <LcStatusTag
              v-if="datasource"
              :label="
                datasource.enabled
                  ? $t('common.status.enabled')
                  : $t('common.status.disabled')
              "
              :type="datasource.enabled ? 'success' : 'default'"
            />
          </div>
          <div
            class="mt-0.5 flex min-w-0 items-center gap-1.5 text-[11px] text-muted-foreground"
          >
            <span class="shrink-0">{{ datasourceTypeMeta.label }}</span>
            <span class="shrink-0 opacity-40">·</span>
            <span class="shrink-0 tabular-nums">{{
              datasource?.host || '--'
            }}</span>
            <span class="shrink-0 opacity-40">·</span>
            <span class="truncate">{{ datasource?.databaseName || '--' }}</span>
          </div>
        </div>
      </div>
    </template>

    <template #extra>
      <div class="flex items-center gap-2">
        <NButton secondary @click="goBack">
          <template #icon>
            <ArrowLeft class="size-3.5" />
          </template>
          {{ $t('common.actions.back') }}
        </NButton>
        <NButton
          v-if="activeTab === 'custom'"
          :loading="savingStructure"
          type="primary"
          @click="handleSaveStructure"
        >
          <template #icon>
            <Save class="size-3.5" />
          </template>
          {{ $t('datasource.detail.saveConfig') }}
        </NButton>
      </div>
    </template>

    <div class="flex h-full min-h-0 flex-col gap-3">
      <!-- 概览指标 -->
      <div class="grid shrink-0 gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <div
          v-for="tile in statTiles"
          :key="tile.label"
          class="flex items-center justify-between gap-3 rounded-lg border border-border bg-card px-4 py-3 shadow-[var(--shadow-card)]"
        >
          <span class="min-w-0 truncate text-xs text-muted-foreground">
            {{ tile.label }}
          </span>
          <span
            class="c-stat-value shrink-0 text-lg font-semibold text-foreground"
            :class="{
              'text-primary': tile.tone === 'primary',
              'text-sky-600 dark:text-sky-400': tile.tone === 'info',
              'text-emerald-600 dark:text-emerald-400': tile.tone === 'success',
            }"
          >
            {{ tile.value }}
          </span>
        </div>
      </div>

      <!-- 内省结果 -->
      <div
        class="flex min-h-0 flex-1 flex-col overflow-hidden rounded-lg border border-border bg-card shadow-[var(--shadow-card)]"
      >
        <div
          class="flex shrink-0 flex-wrap items-center justify-between gap-3 border-b border-border px-4 py-3"
        >
          <div
            class="flex items-center gap-1 rounded-lg border border-border bg-muted/30 p-1"
          >
            <button
              class="inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition-colors"
              :class="
                activeTab === 'original'
                  ? 'bg-card text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground'
              "
              type="button"
              @click="switchTab('original')"
            >
              <Database class="size-3.5" />
              <span>{{ $t('datasource.detail.discoveryTitle') }}</span>
              <span
                class="rounded bg-muted px-1.5 text-[10px] tabular-nums text-muted-foreground"
              >
                {{ structure.length }}
              </span>
              <span
                v-if="pendingImportCount > 0"
                class="rounded bg-amber-100 px-1.5 text-[10px] tabular-nums text-amber-700 dark:bg-amber-950/50 dark:text-amber-300"
              >
                +{{ pendingImportCount }}
              </span>
            </button>
            <button
              class="inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition-colors"
              :class="
                activeTab === 'custom'
                  ? 'bg-card text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground'
              "
              type="button"
              @click="switchTab('custom')"
            >
              <Check class="size-3.5" />
              <span>{{ $t('datasource.detail.aiConfigTitle') }}</span>
              <span
                class="rounded bg-muted px-1.5 text-[10px] tabular-nums text-muted-foreground"
              >
                {{ customStructure.length }}
              </span>
            </button>
          </div>

          <div class="flex min-w-0 items-center gap-2">
            <span
              v-if="activeTab === 'custom'"
              class="hidden truncate text-[11px] text-muted-foreground xl:inline"
            >
              {{ $t('datasource.detail.legend') }}
            </span>
            <NTag
              v-else
              :bordered="false"
              class="hidden xl:inline-flex"
              size="small"
            >
              {{ $t('datasource.detail.discoveryHint') }}
            </NTag>
          </div>
        </div>

        <NSpin
          :show="loading"
          class="min-h-0 flex-1"
          content-class="h-full overflow-y-auto"
        >
          <div
            v-if="sourceTables.length > 0"
            class="grid min-h-[420px] gap-4 p-4 lg:grid-cols-[288px_minmax(0,1fr)]"
          >
            <aside class="rounded-lg border border-border/80 bg-muted/15 p-2">
              <div class="mb-2 flex items-center justify-between px-2 py-1">
                <span class="text-xs font-semibold text-foreground">
                  {{
                    activeTab === 'custom'
                      ? $t('datasource.detail.importedTables')
                      : $t('datasource.detail.originalTables')
                  }}
                </span>
                <NButton quaternary size="tiny" @click="expandAllTables">
                  {{ $t('common.actions.expandAll') }}
                </NButton>
              </div>
              <button
                class="mb-1 flex w-full items-center gap-2 rounded-md px-2.5 py-2 text-left text-xs transition-colors"
                :class="
                  selectedTable === '__all__'
                    ? 'bg-primary/10 font-semibold text-primary'
                    : 'text-muted-foreground hover:bg-muted/60'
                "
                type="button"
                @click="selectTable('__all__')"
              >
                <Database class="size-3.5" />
                <span class="truncate">
                  {{
                    activeTab === 'custom'
                      ? $t('datasource.detail.allImportedTables')
                      : $t('datasource.detail.allOriginalTables')
                  }}
                </span>
                <span class="ml-auto text-[10px] tabular-nums opacity-60">
                  {{ sourceTables.length }}
                </span>
              </button>
              <div
                v-for="table in sourceTables"
                :key="table.tableName"
                class="mb-0.5"
              >
                <div
                  class="grid grid-cols-[28px_minmax(0,1fr)_76px_28px] items-center rounded-md pr-3 transition-colors"
                  :class="
                    selectedTable === table.tableName && !selectedColumn
                      ? 'bg-primary/10 text-primary'
                      : 'text-foreground hover:bg-muted/60'
                  "
                >
                  <button
                    class="flex size-7 shrink-0 items-center justify-center text-muted-foreground"
                    type="button"
                    :aria-label="
                      expandedTables[table.tableName]
                        ? $t('common.actions.collapse')
                        : $t('common.actions.expand')
                    "
                    @click="toggleTable(table.tableName)"
                  >
                    <ChevronDown
                      v-if="expandedTables[table.tableName]"
                      class="size-3.5"
                    />
                    <ChevronRight v-else class="size-3.5" />
                  </button>
                  <button
                    class="min-w-0 flex-1 truncate py-2 text-left text-xs font-medium"
                    type="button"
                    :title="table.tableName"
                    @click="selectTable(table.tableName)"
                  >
                    {{ table.tableName }}
                  </button>
                  <div class="flex w-[76px] justify-end">
                    <NTag
                      v-if="
                        activeTab === 'original' &&
                        importedTableMap.has(table.tableName)
                      "
                      :bordered="false"
                      size="small"
                      type="success"
                    >
                      {{ $t('datasource.detail.imported') }}
                    </NTag>
                    <NButton
                      v-if="
                        activeTab === 'original' &&
                        !importedTableMap.has(table.tableName)
                      "
                      class="w-[66px]"
                      secondary
                      size="tiny"
                      @click="importTable(table)"
                    >
                      <template #icon>
                        <Download class="size-3" />
                      </template>
                      {{ $t('datasource.detail.import') }}
                    </NButton>
                    <NTag
                      v-if="activeTab === 'custom'"
                      :bordered="false"
                      size="small"
                      :type="table.enabled === false ? 'default' : 'success'"
                    >
                      {{
                        table.enabled === false
                          ? $t('datasource.detail.retrievalDisabled')
                          : $t('datasource.detail.retrievalEnabled')
                      }}
                    </NTag>
                  </div>
                  <span
                    class="text-right text-[10px] tabular-nums text-muted-foreground"
                  >
                    {{ table.columns.length }}
                  </span>
                </div>
                <div
                  v-if="expandedTables[table.tableName]"
                  class="ml-7 border-l border-border/70 pl-2"
                >
                  <button
                    class="flex w-full items-center rounded-md px-2 py-1.5 text-left text-[11px] text-muted-foreground hover:bg-muted/60"
                    :class="
                      selectedTable === table.tableName && !selectedColumn
                        ? 'text-primary'
                        : ''
                    "
                    type="button"
                    @click="selectTable(table.tableName)"
                  >
                    {{ $t('datasource.detail.allFields') }}
                  </button>
                  <button
                    v-for="column in table.columns"
                    :key="column.name"
                    class="flex w-full items-center rounded-md px-2 py-1.5 text-left text-[11px] text-muted-foreground hover:bg-muted/60"
                    :class="
                      selectedTable === table.tableName &&
                      (selectedColumn === column.name ||
                        selectedColumn === column.originalName)
                        ? 'bg-primary/10 text-primary'
                        : ''
                    "
                    type="button"
                    @click="selectTable(table.tableName, column.name)"
                  >
                    <span class="truncate">{{ column.name }}</span>
                    <span class="ml-auto pl-2 text-[10px] opacity-60">{{
                      column.type
                    }}</span>
                  </button>
                </div>
              </div>
            </aside>

            <section class="min-w-0">
              <div class="mb-3 flex items-center justify-between gap-3">
                <div class="min-w-0">
                  <div class="truncate text-sm font-semibold text-foreground">
                    {{ selectedLabel }}
                  </div>
                  <div class="mt-0.5 text-[11px] text-muted-foreground">
                    {{
                      $t('datasource.detail.fieldsCount', {
                        count: currentRows.length,
                      })
                    }}
                  </div>
                </div>
                <div class="flex shrink-0 items-center gap-3">
                  <NTag
                    v-if="activeTab === 'custom'"
                    :bordered="false"
                    type="info"
                  >
                    {{ $t('datasource.detail.fieldsEditable') }}
                  </NTag>
                  <div
                    v-if="selectedManagedTable"
                    class="flex items-center gap-2 rounded-md border border-border bg-muted/20 px-2.5 py-1.5"
                  >
                    <span class="text-xs text-muted-foreground">
                      {{ $t('datasource.detail.allowAiRetrieval') }}
                    </span>
                    <NSwitch
                      :value="selectedManagedTable.enabled !== false"
                      size="small"
                      @update:value="toggleSelectedTableEnabled"
                    />
                    <NButton
                      v-tippy="$t('datasource.detail.removeImportedTable')"
                      circle
                      quaternary
                      size="tiny"
                      type="error"
                      :aria-label="$t('datasource.detail.removeImportedTable')"
                      @click="removeSelectedTable"
                    >
                      <Trash2 class="size-3.5" />
                    </NButton>
                  </div>
                </div>
              </div>
              <Grid>
                <template #customName="{ row }">
                  <NInput
                    v-model:value="row.name"
                    size="small"
                    :placeholder="
                      $t('datasource.detail.aiFieldNamePlaceholder')
                    "
                  />
                </template>
                <template #customType="{ row }">
                  <NInput
                    v-model:value="row.type"
                    size="small"
                    :placeholder="
                      $t('datasource.detail.aiFieldTypePlaceholder')
                    "
                  />
                </template>
                <template #customComment="{ row }">
                  <NInput
                    v-model:value="row.comment"
                    size="small"
                    :placeholder="
                      $t('datasource.detail.aiFieldCommentPlaceholder')
                    "
                  />
                </template>
              </Grid>
            </section>
          </div>
          <NEmpty
            v-else
            class="py-16"
            :description="
              activeTab === 'custom'
                ? $t('datasource.detail.noImportedTables')
                : $t('datasource.detail.noIntrospectedTables')
            "
          />
        </NSpin>
      </div>
    </div>
  </Page>
</template>

<style scoped>
.c-stat-value {
  font-family:
    'Inter', 'SF Mono', 'JetBrains Mono', ui-monospace, 'Cascadia Mono',
    'Cascadia Code', Consolas, monospace;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}
</style>
