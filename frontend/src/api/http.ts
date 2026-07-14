import axios from 'axios'
import { loadSession } from '@/features/auth/session'

const http = axios.create({
  baseURL: '/api/v1',
})

http.interceptors.request.use((config) => {
  const requestUrl = new URL(http.getUri(config), window.location.origin)
  const isApiRequest = requestUrl.origin === window.location.origin
    && (requestUrl.pathname === '/api/v1' || requestUrl.pathname.startsWith('/api/v1/'))

  if (isApiRequest) {
    const accessToken = loadSession()?.accessToken
    if (accessToken) config.headers.set('Authorization', `Bearer ${accessToken}`)
  }
  return config
})

export default http
