import { createContext, useContext } from 'react';

export const AuthContext = createContext(null);

// Acceso a la sesión desde cualquier componente: const { usuario, login, logout } = useAuth();
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth debe usarse dentro de <AuthProvider>');
  return ctx;
}
