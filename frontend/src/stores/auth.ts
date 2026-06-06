import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { login as loginApi, me, register as registerApi, type RegisterPayload, type UserProfile } from '../api/auth'
import { getStoredToken, setStoredToken } from '../api/client'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getStoredToken())
  const user = ref<UserProfile | null>(null)

  const isAuthenticated = computed(() => Boolean(token.value))

  const applySession = (newToken: string, newUser: UserProfile) => {
    token.value = newToken
    user.value = newUser
    setStoredToken(newToken)
  }

  const login = async (usernameOrEmail: string, password: string) => {
    const res = await loginApi(usernameOrEmail, password)
    applySession(res.data.token, res.data.user)
  }

  const register = async (payload: RegisterPayload) => {
    const res = await registerApi(payload)
    applySession(res.data.token, res.data.user)
  }

  const fetchMe = async () => {
    if (!token.value) return
    const res = await me()
    user.value = res.data
  }

  const initFromStorage = async () => {
    token.value = getStoredToken()
    if (token.value && !user.value) {
      try {
        await fetchMe()
      } catch (err) {
        logout()
      }
    }
  }

  const logout = () => {
    token.value = null
    user.value = null
    setStoredToken(null)
  }

  return {
    token,
    user,
    isAuthenticated,
    login,
    register,
    fetchMe,
    initFromStorage,
    logout
  }
})
