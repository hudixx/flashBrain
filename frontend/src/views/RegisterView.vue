<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <template #header>
        <div class="auth-header">
          <h2>注册 FlashBrain</h2>
          <span>创建你的个人知识空间</span>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="handleRegister">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="3-64 个字符" autocomplete="username" />
        </el-form-item>
        <el-form-item label="邮箱（可选）" prop="email">
          <el-input v-model="form.email" placeholder="用于后续识别账号" autocomplete="email" />
        </el-form-item>
        <el-form-item label="显示名称（可选）" prop="displayName">
          <el-input v-model="form.displayName" placeholder="例如：小明" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 8 位" autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password placeholder="再次输入密码" autocomplete="new-password" />
        </el-form-item>
        <el-button type="primary" class="submit-btn" :loading="loading" @click="handleRegister">注册并进入</el-button>
      </el-form>

      <div class="auth-footer">
        已有账号？
        <RouterLink to="/login">返回登录</RouterLink>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  email: '',
  displayName: '',
  password: '',
  confirmPassword: ''
})

const validateConfirmPassword = (_rule: any, value: string, callback: (error?: Error) => void) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
    return
  }
  callback()
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 64, message: '用户名长度必须在 3 到 64 个字符之间', trigger: 'blur' }
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  displayName: [{ max: 64, message: '显示名称不能超过 64 个字符', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 100, message: '密码长度必须在 8 到 100 个字符之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const errorMessage = (err: any) => err.response?.data?.message || '注册失败，请稍后重试'

const handleRegister = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  loading.value = true
  try {
    await authStore.register({
      username: form.username,
      email: form.email || undefined,
      displayName: form.displayName || undefined,
      password: form.password
    })
    ElMessage.success('注册成功')
    router.push('/')
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
  width: 440px;
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
