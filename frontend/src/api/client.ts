import axios from 'axios'

const TOKEN_KEY = 'flashbrain.auth.token'

const apiClient = axios.create({
  baseURL: '/api'
})

export const getStoredToken = () => window.localStorage.getItem(TOKEN_KEY)

export const setStoredToken = (token: string | null) => {
  if (token) {
    window.localStorage.setItem(TOKEN_KEY, token)
  } else {
    window.localStorage.removeItem(TOKEN_KEY)
  }
}

apiClient.interceptors.request.use((config) => {
  const token = getStoredToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

apiClient.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401 && window.location.pathname !== '/login') {
      setStoredToken(null)
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default apiClient
