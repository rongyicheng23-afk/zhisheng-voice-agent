import http from '@/api/index'

export interface LoginResponse {
  code: number
  message: string
  data: string
}

export interface UserInfoResponse {
  code: number
  message: string
  data: {
    id: number
    username: string
    nickname?: string
    email?: string
    userPic?: string
  }
}

export interface CommonResponse {
  code: number
  message: string
  data: null
}

export const register = (username: string, password: string, nickname?: string, email?: string) => {
  const params = new URLSearchParams()
  params.append('username', username)
  params.append('password', password)
  if (nickname) params.append('nickname', nickname)
  if (email) params.append('email', email)
  return http.post<any, CommonResponse>('/user/register', params, {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    }
  })
}

export const login = (username: string, password: string) => {
  const params = new URLSearchParams()
  params.append('username', username)
  params.append('password', password)
  return http.post<any, LoginResponse>('/user/login', params, {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    }
  })
}

export const getUserInfo = () => {
  return http.get<any, UserInfoResponse>('/user/userInfo')
}
