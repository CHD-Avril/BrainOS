import type { RawResponse, TransportRequest } from '@/types/api'
import { ApiError } from './errors'

export interface RequestTransport {
  request(options: TransportRequest): Promise<RawResponse>
}

export const uniRequestTransport: RequestTransport = {
  request(options) {
    return new Promise((resolve, reject) => {
      uni.request({
        url: options.url,
        method: options.method as UniApp.RequestOptions['method'],
        header: options.headers,
        data: options.data as UniApp.RequestOptions['data'],
        timeout: options.timeoutMs,
        success: result => resolve({ statusCode: result.statusCode, data: result.data }),
        fail: error => reject(new ApiError(error.errMsg || '网络请求失败')),
      })
    })
  },
}
