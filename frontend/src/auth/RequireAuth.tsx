import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'

export function RequireAuth({ children }: { children: ReactNode }) {
  const { member } = useAuth()
  const location = useLocation()

  if (!member) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return children
}
