import apiClient from './client'

export interface UserProfile {
  id: number
  username: string
  email?: string | null
  displayName?: string | null
}

export interface AuthResponse {
  token: string
  tokenType: string
  expiresIn: number
  user: UserProfile
}

export interface RegisterPayload {
  username: string
  email?: string
  password: string
  displayName?: string
}

export const login = (usernameOrEmail: string, password: string) => {
  return apiClient.post<AuthResponse>('/auth/login', { usernameOrEmail, password })
}

export const register = (payload: RegisterPayload) => {
  return apiClient.post<AuthResponse>('/auth/register', payload)
}

export const me = () => {
  return apiClient.get<UserProfile>('/auth/me')
}
