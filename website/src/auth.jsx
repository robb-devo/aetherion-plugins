import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api } from './lib/api.js'

const AuthContext = createContext(null)

/** Session lives in an HttpOnly cookie; this only mirrors who is signed in. */
export function AuthProvider({ children }) {
  const [state, setState] = useState({ status: 'loading', user: null })

  const refresh = useCallback(async () => {
    try {
      const { user } = await api.me()
      setState({ status: 'signed-in', user })
    } catch (error) {
      setState({ status: error?.status === 401 ? 'signed-out' : 'unknown', user: null })
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const login = useCallback(async (username, password) => {
    const { user } = await api.login(username, password)
    setState({ status: 'signed-in', user })
    return user
  }, [])

  const register = useCallback(async (username, password) => {
    const { user } = await api.register(username, password)
    setState({ status: 'signed-in', user })
    return user
  }, [])

  const logout = useCallback(async () => {
    try {
      await api.logout()
    } finally {
      setState({ status: 'signed-out', user: null })
    }
  }, [])

  /** Called when any request answers 401, e.g. the session expired in another tab. */
  const expire = useCallback(() => setState({ status: 'signed-out', user: null }), [])

  const value = useMemo(
    () => ({ ...state, login, register, logout, refresh, expire }),
    [state, login, register, logout, refresh, expire],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
