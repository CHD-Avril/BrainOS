import axios from 'axios'
import { loadSession } from '@/features/auth/session'

const http = axios.create({
  baseURL: '/api/v1',
})

http.interceptors.request.use((config) => {
  const accessToken = loadSession()?.accessToken
  if (accessToken) config.headers.set('Authorization', `Bearer ${accessToken}`)
  return config
})

export default http
