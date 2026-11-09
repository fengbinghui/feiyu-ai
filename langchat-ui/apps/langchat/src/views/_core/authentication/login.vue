<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import { computed, markRaw } from 'vue';
import { useRoute } from 'vue-router';

import { AuthenticationLogin, SliderCaptcha, z } from '@vben/common-ui';
import { $t } from '@vben/locales';

import { useAuthStore } from '#/store';

defineOptions({ name: 'Login' });

const authStore = useAuthStore();
const route = useRoute();

/** 注册成功跳回登录页时可携带 username 预填 */
const defaultUsername = computed(() => {
  const username = route.query.username;
  return typeof username === 'string' && username.trim()
    ? username.trim()
    : 'admin';
});

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      component: 'VbenInput',
      componentProps: {
        placeholder: $t('auth.form.usernamePlaceholder'),
      },
      defaultValue: defaultUsername.value,
      fieldName: 'username',
      help: $t('auth.form.usernameHelp'),
      label: $t('authentication.username'),
      rules: z
        .string()
        .min(1, { message: $t('authentication.usernameTip') })
        .default(defaultUsername.value),
    },
    {
      component: 'VbenInputPassword',
      componentProps: {
        placeholder: $t('authentication.passwordTip'),
      },
      defaultValue: defaultUsername.value === 'admin' ? '123456' : '',
      fieldName: 'password',
      help: $t('auth.form.passwordHelp'),
      label: $t('authentication.password'),
      rules: z
        .string()
        .min(1, { message: $t('authentication.passwordTip') }),
    },
    {
      component: markRaw(SliderCaptcha),
      fieldName: 'captcha',
      rules: z.boolean().refine((value) => value, {
        message: $t('authentication.verifyRequiredTip'),
      }),
    },
  ];
});
</script>

<template>
  <AuthenticationLogin
    :form-schema="formSchema"
    :loading="authStore.loginLoading"
    :forget-password-path="'/auth/forget-password'"
    :register-path="'/auth/register'"
    :show-code-login="false"
    :show-forget-password="true"
    :show-qrcode-login="false"
    :show-register="true"
    :show-third-party-login="false"
    @submit="authStore.authLogin"
  />
</template>
