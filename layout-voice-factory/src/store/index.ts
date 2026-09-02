import { createStore, Commit } from 'vuex'
import { GlobalDataProps, GlobalErrorProps, UserProps } from './types'
import axios, { AxiosRequestConfig } from 'axios'
import { setAuthToken } from '../api'
import { StorageHandler, storageType } from '../libs/storage'
const storageHandler = new StorageHandler()

const getStoredUser = () => {
  const rawUser = storageHandler.getItem(storageType, 'user')
  if (!rawUser) {
    return {} as UserProps
  }
  try {
    return JSON.parse(rawUser) as UserProps
  } catch (error) {
    storageHandler.remove(storageType, 'user')
    return {} as UserProps
  }
}

const asyncAndCommit = async (url: string, mutationName: string, commit: Commit,
  config: AxiosRequestConfig = { method: 'get' }, extraData?: any) => {
  const { data } = await axios(url, config)
  if (extraData) {
    commit(mutationName, { data, extraData })
  } else {
    commit(mutationName, data)
  }
  return data
}

const store = createStore<GlobalDataProps>({
  state: {
    error: { status: false },
    token: storageHandler.getItem(storageType, 'token') || '',
    loading: false,
    user: getStoredUser()
  },
  mutations: {
    // login (state) {
    //   state.user = { ...state.user, isLogin: true, name: 'marlon' }
    // },
    setLoading (state, status) {
      state.loading = status
    },
    setError (state, e: GlobalErrorProps) {
      state.error = e
    },
    setAuthState (state, payload: { token: string; user: UserProps }) {
      state.token = payload.token
      state.user = payload.user
      storageHandler.setItem(storageType, 'token', payload.token)
      storageHandler.setItem(storageType, 'user', JSON.stringify(payload.user))
      axios.defaults.headers.common.Authorization = `Bearer ${payload.token}`
      setAuthToken(payload.token)
    },
    logout (state) {
      state.token = ''
      state.user = {} as UserProps
      storageHandler.remove(storageType, 'token')
      storageHandler.remove(storageType, 'user')
      delete axios.defaults.headers.common.Authorization
      setAuthToken()
    }
  },
  actions: {
    // async fetchColumns ({ commit }) {
    //   const { data } = await axios.get('/api/columns')
    //   commit('fetchColumns', data)
    // },
    // 一步封装优化实现





  },
  getters: {
    isLoggedIn: (state) => Boolean(state.token && state.user?.isLogin)
  }
})

export default store
