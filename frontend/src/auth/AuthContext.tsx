import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi } from '../api/auth'
import { setAuthToken, setTokenRefreshedHandler, setUnauthorizedHandler } from '../api/client'
import type { Member } from '../api/types'

interface StoredSession {
  token: string
  member: Member
}

interface AuthContextValue {
  member: Member | null
  login: (email: string, password: string) => Promise<void>
  register: (inviteToken: string, name: string, username: string, password: string) => Promise<void>
  logout: () => void
  updateMember: (member: Member) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

const STORAGE_KEY = 'movie-club-session'

function loadSession(): StoredSession | null {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as StoredSession
  } catch {
    return null
  }
}

const initialSession = loadSession()
setAuthToken(initialSession?.token ?? null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [member, setMember] = useState<Member | null>(initialSession?.member ?? null)

  const applySession = useCallback((session: StoredSession | null) => {
    if (session) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
      setAuthToken(session.token)
      setMember(session.member)
    } else {
      localStorage.removeItem(STORAGE_KEY)
      setAuthToken(null)
      setMember(null)
    }
  }, [])

  const handleTokenRefreshed = useCallback((token: string) => {
    const stored = loadSession()
    if (stored) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...stored, token }))
    }
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(() => applySession(null))
    setTokenRefreshedHandler(handleTokenRefreshed)
    return () => {
      setUnauthorizedHandler(null)
      setTokenRefreshedHandler(null)
    }
  }, [applySession, handleTokenRefreshed])

  const value = useMemo<AuthContextValue>(
    () => ({
      member,
      login: async (email, password) => {
        const response = await authApi.login(email, password)
        applySession(response)
      },
      register: async (inviteToken, name, username, password) => {
        const response = await authApi.register(inviteToken, name, username, password)
        applySession(response)
      },
      logout: () => applySession(null),
      updateMember: (updated) => {
        const stored = loadSession()
        if (stored) {
          applySession({ ...stored, member: updated })
        } else {
          setMember(updated)
        }
      },
    }),
    [member],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
