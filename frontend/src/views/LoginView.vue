<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <template #header>
        <div class="auth-header">
          <h2>登录 FlashBrain</h2>
          <span>继续内化你的碎片知识</span>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="handleLogin">
        <el-form-item label="用户名或邮箱" prop="usernameOrEmail">
          <el-input v-model="form.usernameOrEmail" placeholder="请输入用户名或邮箱" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" autocomplete="current-password" />
        </el-form-item>
        <el-button type="primary" class="submit-btn" :loading="loading" @click="handleLogin">登录</el-button>
      </el-form>

      <div class="auth-footer">
        还没有账号？
        <RouterLink to="/register">立即注册</RouterLink>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  usernameOrEmail: '',
  password: ''
})

const rules: FormRules = {
  usernameOrEmail: [{ required: true, message: '请输入用户名或邮箱', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const errorMessage = (err: any) => err.response?.data?.message || '登录失败，请检查账号和密码'

const handleLogin = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  loading.value = true
  try {
    await authStore.login(form.usernameOrEmail, form.password)
    ElMessage.success('登录成功')
    router.push((route.query.redirect as string) || '/')
  } catch (err: any) {
    ElMessage.error(errorMessage(err))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eaf3ff 0%, #f7f8fb 45%, #eef5ff 100%);
}
.auth-card {
  width: 420px;
  border-radius: 14px;
}
.auth-header h2 {
  margin: 0 0 8px;
  color: #1f2d3d;
}
.auth-header span {
  color: #606266;
  font-size: 14px;
}
.submit-btn {
  width: 100%;
  margin-top: 6px;
}
.auth-footer {
  margin-top: 18px;
  text-align: center;
  color: #606266;
  font-size: 14px;
}
.auth-footer a {
  color: #409eff;
  text-decoration: none;
}
</style>
