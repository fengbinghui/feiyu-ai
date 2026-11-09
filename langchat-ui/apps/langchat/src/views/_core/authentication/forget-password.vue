<script lang="ts" setup>
import { useRouter } from 'vue-router';

import { CircleHelp } from '@vben/icons';
import { $t } from '@vben/locales';

import { NButton } from 'naive-ui';

defineOptions({ name: 'ForgetPassword' });

const router = useRouter();

function goLogin() {
  void router.push('/auth/login');
}

function goRegister() {
  void router.push('/auth/register');
}
</script>

<template>
  <div class="flex flex-col items-center py-8 text-center">
    <div
      class="flex size-12 items-center justify-center rounded-xl border border-primary/25 bg-primary/8 text-primary"
    >
      <CircleHelp class="size-6" />
    </div>
    <h1 class="mt-4 text-xl font-semibold text-foreground">
      {{ $t('auth.forgetPassword.title') }}
    </h1>
    <p class="mt-1 text-sm text-muted-foreground">
      {{ $t('auth.forgetPassword.subTitle') }}
    </p>

    <div
      class="mt-6 w-full max-w-md rounded-lg border border-border/80 bg-muted/20 px-4 py-3 text-left"
    >
      <div class="text-[13px] font-semibold text-foreground">
        {{ $t('auth.forgetPassword.notEnabledTitle') }}
      </div>
      <p class="mt-1.5 text-xs leading-6 text-muted-foreground">
        {{ $t('auth.forgetPassword.notEnabledDescription') }}
      </p>
      <ul class="mt-2 list-disc space-y-1 pl-4 text-xs leading-6 text-muted-foreground">
        <li>{{ $t('auth.forgetPassword.tipAdmin') }}</li>
        <li>{{ $t('auth.forgetPassword.tipSelf') }}</li>
      </ul>
    </div>

    <div class="mt-6 flex w-full max-w-md flex-col gap-2 sm:flex-row">
      <NButton class="sm:flex-1" type="primary" @click="goLogin">
        {{ $t('auth.forgetPassword.backToLogin') }}
      </NButton>
      <NButton class="sm:flex-1" secondary @click="goRegister">
        {{ $t('auth.forgetPassword.goRegister') }}
      </NButton>
    </div>
  </div>
</template>
