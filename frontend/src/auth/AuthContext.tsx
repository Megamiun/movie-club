import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import { authApi } from '../api/auth'
import { setAuthToken } from '../api/client'
import type { Member } from '../api/types'

interface StoredSession {
  token: string
  member: Member
}

interface AuthContextValue {
  member: Member | null
  login: (email: string, password: string) => Promise<void>
  register: (inviteToken: string, name: string, password: string) => Promise<void>
  logout: () => void
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

  const applySession = (session: StoredSession | null) => {
    if (session) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
      setAuthToken(session.token)
      setMember(session.member)
    } else {
      localStorage.removeItem(STORAGE_KEY)
      setAuthToken(null)
      setMember(null)
    }
  }

  const value = useMemo<AuthContextValue>(
    () => ({
      member,
      login: async (email, password) => {
        const response = await authApi.login(email, password)
        applySession(response)
      },
      register: async (inviteToken, name, password) => {
        const response = await authApi.register(inviteToken, name, password)
        applySession(response)
      },
      logout: () => applySession(null),
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
