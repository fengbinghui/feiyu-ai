<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { AuthenticationRegister, z } from '@vben/common-ui';
import { CircleAlert } from '@vben/icons';
import { $t } from '@vben/locales';

import { NButton, NSpin } from 'naive-ui';

import { message } from '#/adapter/naive';
import { registerApi, registerEnabledApi } from '#/api/core/auth';

defineOptions({ name: 'Register' });

const router = useRouter();

const checking = ref(true);
const registerEnabled = ref(true);
const submitting = ref(false);

const usernameRule = z
  .string()
  .min(3, { message: $t('auth.register.rules.username') })
  .max(32, { message: $t('auth.register.rules.username') })
  .regex(/^[a-zA-Z0-9_]+$/, {
    message: $t('auth.register.rules.username'),
  });

const formSchema = computed((): VbenFormSchema[] => [
  {
    component: 'VbenInput',
    componentProps: {
      placeholder: $t('auth.register.usernamePlaceholder'),
    },
    fieldName: 'username',
    label: $t('authentication.username'),
    rules: usernameRule,
  },
  {
    component: 'VbenInputPassword',
    componentProps: {
      placeholder: $t('auth.register.passwordPlaceholder'),
    },
    fieldName: 'password',
    label: $t('authentication.password'),
    rules: z.string().min(6, {
      message: $t('auth.register.rules.password'),
    }),
  },
  {
    component: 'VbenInputPassword',
    componentProps: {
      placeholder: $t('auth.register.confirmPasswordPlaceholder'),
    },
    fieldName: 'confirmPassword',
    label: $t('authentication.confirmPassword'),
    rules: z.string().min(1, {
      message: $t('auth.register.rules.passwordMismatch'),
    }),
  },
  {
    component: 'VbenInput',
    componentProps: {
      placeholder: $t('auth.register.realNamePlaceholder'),
    },
    fieldName: 'realName',
    label: $t('profile.basic.realName'),
  },
  {
    component: 'VbenInput',
    componentProps: {
      placeholder: $t('auth.register.emailPlaceholder'),
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
    component: 'VbenInput',
    componentProps: {
      placeholder: $t('auth.register.phonePlaceholder'),
    },
    fieldName: 'phone',
    label: $t('profile.basic.phone'),
  },
]);

function optionalText(value: unknown) {
  const text = String(value ?? '').trim();
  return text || undefined;
}

async function handleSubmit(values: Record<string, any>) {
  if (String(values.password ?? '') !== String(values.confirmPassword ?? '')) {
    message.error($t('auth.register.rules.passwordMismatch'));
    return;
  }
  submitting.value = true;
  try {
    const username = String(values.username ?? '').trim();
    await registerApi({
      email: optionalText(values.email),
      password: String(values.password ?? ''),
      phone: optionalText(values.phone),
      realName: optionalText(values.realName),
      username,
    });
    message.success($t('auth.register.success'));
    await router.push({ path: '/auth/login', query: { username } });
  } finally {
    submitting.value = false;
  }
}

function goLogin() {
  void router.push('/auth/login');
}

onMounted(async () => {
  registerEnabled.value = await registerEnabledApi();
  checking.value = false;
});
</script>

<template>
  <div>
    <div v-if="checking" class="flex items-center justify-center py-20">
      <NSpin size="medium" />
    </div>

    <!-- 注册未开放 -->
    <div
      v-else-if="!registerEnabled"
      class="flex flex-col items-center py-14 text-center"
    >
      <div
        class="flex size-12 items-center justify-center rounded-xl border border-warning/30 bg-warning/10 text-warning"
      >
        <CircleAlert class="size-6" />
      </div>
      <h1 class="mt-4 text-xl font-semibold text-foreground">
        {{ $t('auth.register.disabledTitle') }}
      </h1>
      <p class="mt-2 max-w-sm text-sm leading-6 text-muted-foreground">
        {{ $t('auth.register.disabledDescription') }}
      </p>
      <NButton class="mt-6 w-40" type="primary" @click="goLogin">
        {{ $t('auth.register.backToLogin') }}
      </NButton>
    </div>

    <!-- 注册表单 -->
    <AuthenticationRegister
      v-else
      :form-schema="formSchema"
      :loading="submitting"
      :login-path="'/auth/login'"
      :sub-title="$t('auth.register.subTitle')"
      :submit-button-text="$t('auth.register.submit')"
      :title="$t('auth.register.title')"
      @submit="handleSubmit"
    />
  </div>
</template>
