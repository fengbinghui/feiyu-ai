<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import { computed, onMounted, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { z } from '@vben/common-ui';
import { UserRound } from '@vben/icons';
import { $t } from '@vben/locales';
import { useUserStore } from '@vben/stores';

import { NButton, NSpin, NTag } from 'naive-ui';

import { useVbenForm } from '#/adapter/form';
import { message } from '#/adapter/naive';
import {
  changePasswordApi,
  getCurrentUserProfileApi,
  updateProfileApi,
} from '#/api/core/auth';
import PageHeader from '#/components/PageHeader/index.vue';
import { useAuthStore } from '#/store';

defineOptions({ name: 'Profile' });

const userStore = useUserStore();
const authStore = useAuthStore();

const loading = ref(false);
const savingProfile = ref(false);
const savingPassword = ref(false);

const username = computed(() => userStore.userInfo?.username || '--');
const roles = computed(() => userStore.userInfo?.roles ?? []);
const userId = computed(() => userStore.userInfo?.userId || '--');

function optionalText(value: unknown) {
  const text = String(value ?? '').trim();
  return text || undefined;
}

// ---------- 基本资料 ----------
const profileSchema = computed((): VbenFormSchema[] => [
  {
    component: 'Input',
    componentProps: {
      placeholder: $t('profile.basic.realNamePlaceholder'),
    },
    fieldName: 'realName',
    label: $t('profile.basic.realName'),
    rules: z.string().min(1, {
      message: $t('profile.validation.realNameRequired'),
    }),
  },
  {
    component: 'Input',
    componentProps: {
      placeholder: $t('profile.basic.emailPlaceholder'),
    },
    fieldName: 'email',
    label: $t('profile.basic.email'),
    rules: z
      .string()
      .email({ message: $t('auth.register.rules.email') })
      .or(z.literal(''))
      .optional(),
  },
  {
    component: 'Input',
    componentProps: {
      placeholder: $t('profile.basic.phonePlaceholder'),
    },
    fieldName: 'phone',
    label: $t('profile.basic.phone'),
  },
  {
    component: 'Input',
    componentProps: {
      placeholder: $t('profile.basic.avatarPlaceholder'),
    },
    fieldName: 'avatar',
    formItemClass: 'sm:col-span-2',
    label: $t('profile.basic.avatar'),
  },
]);

const [ProfileForm, profileFormApi] = useVbenForm({
  layout: 'vertical',
  schema: profileSchema.value,
  showDefaultActions: false,
  wrapperClass: 'lc-form-grid grid-cols-1 gap-x-4 sm:grid-cols-2',
});

watch(
  () => profileSchema.value,
  (schema) => {
    profileFormApi.setState({ schema });
  },
  { immediate: true },
);

// ---------- 修改密码 ----------
const passwordSchema = computed((): VbenFormSchema[] => [
  {
    component: 'VbenInputPassword',
    componentProps: {
      placeholder: $t('profile.password.oldPasswordPlaceholder'),
    },
    fieldName: 'oldPassword',
    label: $t('profile.password.oldPassword'),
    rules: z.string().min(1, {
      message: $t('profile.password.oldPasswordPlaceholder'),
    }),
  },
  {
    component: 'VbenInputPassword',
    componentProps: {
      placeholder: $t('profile.password.newPasswordPlaceholder'),
    },
    fieldName: 'newPassword',
    label: $t('profile.password.newPassword'),
    rules: z.string().min(6, {
      message: $t('profile.validation.passwordMin'),
    }),
  },
  {
    component: 'VbenInputPassword',
    componentProps: {
      placeholder: $t('profile.password.confirmPasswordPlaceholder'),
    },
    fieldName: 'confirmPassword',
    label: $t('profile.password.confirmPassword'),
    rules: z.string().min(1, {
      message: $t('profile.validation.passwordMismatch'),
    }),
  },
]);

const [PasswordForm, passwordFormApi] = useVbenForm({
  layout: 'vertical',
  schema: passwordSchema.value,
  showDefaultActions: false,
  wrapperClass: 'lc-form-grid grid-cols-1 gap-x-4',
});

watch(
  () => passwordSchema.value,
  (schema) => {
    passwordFormApi.setState({ schema });
  },
  { immediate: true },
);

async function loadProfile() {
  loading.value = true;
  try {
    const data = await getCurrentUserProfileApi();
    await profileFormApi.setValues(
      {
        avatar: data.avatar || '',
        email: data.email || '',
        phone: data.phone || '',
        realName: data.displayName || '',
      },
      false,
    );
  } catch {
    // 回填失败时保留用户信息中的昵称
    await profileFormApi.setValues(
      {
        realName: userStore.userInfo?.realName || '',
      },
      false,
    );
  } finally {
    loading.value = false;
  }
}

async function handleSaveProfile() {
  const values = await profileFormApi.validateAndSubmitForm();
  if (!values) {
    return;
  }
  savingProfile.value = true;
  try {
    const payload = {
      avatar: optionalText(values.avatar),
      email: optionalText(values.email),
      phone: optionalText(values.phone),
      realName: optionalText(values.realName),
    };
    const current = await updateProfileApi(payload);
    const userInfo = userStore.userInfo;
    if (userInfo) {
      userStore.setUserInfo({
        ...userInfo,
        avatar: payload.avatar || userInfo.avatar,
        realName:
          current?.displayName || payload.realName || userInfo.realName,
      });
    }
    message.success($t('profile.messages.profileSaved'));
  } finally {
    savingProfile.value = false;
  }
}

async function handleChangePassword() {
  const values = await passwordFormApi.validateAndSubmitForm();
  if (!values) {
    return;
  }
  const oldPassword = String(values.oldPassword ?? '');
  const newPassword = String(values.newPassword ?? '');
  const confirmPassword = String(values.confirmPassword ?? '');
  if (newPassword !== confirmPassword) {
    message.error($t('profile.validation.passwordMismatch'));
    return;
  }
  if (newPassword === oldPassword) {
    message.error($t('profile.validation.newPasswordSame'));
    return;
  }
  savingPassword.value = true;
  try {
    await changePasswordApi({ newPassword, oldPassword });
    message.success($t('profile.messages.passwordChanged'));
    await authStore.logout(false);
  } finally {
    savingPassword.value = false;
  }
}

onMounted(loadProfile);
</script>

<template>
  <Page>
    <div class="flex flex-col gap-3">
      <PageHeader
        :description="$t('profile.description')"
        :title="$t('profile.title')"
      >
        <template #icon>
          <UserRound class="size-5 text-primary" />
        </template>
      </PageHeader>

      <NSpin :show="loading" class="block w-full">
        <div class="grid gap-3 xl:grid-cols-2">
          <div class="flex flex-col gap-3">
            <!-- 账号只读信息 -->
            <section class="lc-surface px-5 py-4">
              <div class="text-sm font-semibold text-foreground">
                {{ $t('profile.account.title') }}
              </div>
              <p class="mt-1 text-xs leading-5 text-muted-foreground">
                {{ $t('profile.account.description') }}
              </p>
              <div class="mt-3 grid grid-cols-1 gap-2 sm:grid-cols-2">
                <div
                  class="rounded-lg border border-border/70 bg-muted/20 px-3 py-2.5"
                >
                  <div class="text-[10px] text-muted-foreground">
                    {{ $t('profile.account.username') }}
                  </div>
                  <div class="mt-1 text-sm font-medium text-foreground">
                    {{ username }}
                  </div>
                </div>
                <div
                  class="rounded-lg border border-border/70 bg-muted/20 px-3 py-2.5"
                >
                  <div class="text-[10px] text-muted-foreground">
                    {{ $t('profile.account.roles') }}
                  </div>
                  <div class="mt-1 flex flex-wrap gap-1">
                    <NTag
                      v-for="role in roles"
                      :key="role"
                      :bordered="false"
                      round
                      size="small"
                      type="primary"
                    >
                      {{ role }}
                    </NTag>
                    <span
                      v-if="roles.length === 0"
                      class="text-xs text-muted-foreground"
                    >
                      --
                    </span>
                  </div>
                </div>
                <div
                  class="rounded-lg border border-border/70 bg-muted/20 px-3 py-2.5 sm:col-span-2"
                >
                  <div class="text-[10px] text-muted-foreground">
                    {{ $t('profile.account.userId') }}
                  </div>
                  <div
                    class="mt-1 truncate text-xs font-medium text-foreground"
                  >
                    {{ userId }}
                  </div>
                </div>
              </div>
            </section>

            <!-- 基本资料 -->
            <section class="lc-surface px-5 py-4">
              <div class="text-sm font-semibold text-foreground">
                {{ $t('profile.basic.title') }}
              </div>
              <p class="mt-1 text-xs leading-5 text-muted-foreground">
                {{ $t('profile.basic.description') }}
              </p>
              <div class="mt-3">
                <ProfileForm />
              </div>
              <div class="mt-2 flex justify-end">
                <NButton
                  :loading="savingProfile"
                  type="primary"
                  @click="handleSaveProfile"
                >
                  {{ $t('profile.basic.save') }}
                </NButton>
              </div>
            </section>
          </div>

          <!-- 修改密码 -->
          <section class="lc-surface flex flex-col px-5 py-4">
            <div class="text-sm font-semibold text-foreground">
              {{ $t('profile.password.title') }}
            </div>
            <p class="mt-1 text-xs leading-5 text-muted-foreground">
              {{ $t('profile.password.description') }}
            </p>
            <div class="mt-3 flex-1">
              <PasswordForm />
            </div>
            <div class="mt-2 flex justify-end">
              <NButton
                :loading="savingPassword"
                type="primary"
                @click="handleChangePassword"
              >
                {{ $t('profile.password.submit') }}
              </NButton>
            </div>
          </section>
        </div>
      </NSpin>
    </div>
  </Page>
</template>
