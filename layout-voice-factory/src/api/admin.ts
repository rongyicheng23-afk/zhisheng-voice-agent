import http from '@/api'

export type AdminProfile = { roles: string[], canManageKnowledge: boolean, canObserveSystem: boolean, canManageUsers: boolean }
export type ManagedUser = { id: number, username: string, nickname: string, roles: string[] }
type BackendResponse<T> = { code: number, msg: string, data: T }

export const getAdminProfile = () => http.get<any, BackendResponse<AdminProfile>>('/api/admin/me')
export const getManagedUsers = () => http.get<any, BackendResponse<ManagedUser[]>>('/api/admin/users')
export const updateManagedUserRoles = (userId: number, roleCodes: string[]) =>
  http.put<any, BackendResponse<{ roles: string[] }>>(`/api/admin/users/${userId}/roles`, { roleCodes })
