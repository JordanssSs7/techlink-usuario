import { useCallback, useMemo, useState } from 'react';
import { AuthContext } from './authContext';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';
const STORAGE_KEY = 'techlink_session';

// Lee la sesión guardada; si no existe o está dañada, no hay sesión.
function leerSesion() {
  try {
    const guardada = JSON.parse(localStorage.getItem(STORAGE_KEY));
    if (guardada?.token && guardada?.usuario) return guardada;
  } catch {
    // localStorage bloqueado o JSON inválido
  }
  return null;
}

function guardarSesion(sesion) {
  try {
    if (sesion) localStorage.setItem(STORAGE_KEY, JSON.stringify(sesion));
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    // sin persistencia: la sesión vive solo en memoria
  }
}

export default function AuthProvider({ children }) {
  const [sesion, setSesion] = useState(leerSesion);

  // Recibe la respuesta del backend: { token, usuario: { id, nombres, apellidos, correoInstitucional, rol } }
  const login = useCallback((data) => {
    const nueva = { token: data.token, usuario: data.usuario };
    guardarSesion(nueva);
    setSesion(nueva);
  }, []);

  const logout = useCallback(() => {
    guardarSesion(null);
    setSesion(null);
  }, []);

  // Actualiza datos del usuario en la sesión (por ejemplo, el ciclo elegido tras el primer ingreso).
  const actualizarUsuario = useCallback((cambios) => {
    setSesion((actual) => {
      if (!actual) return actual;
      const nueva = { ...actual, usuario: { ...actual.usuario, ...cambios } };
      guardarSesion(nueva);
      return nueva;
    });
  }, []);

  // fetch con el token. Si el backend responde 401 (token vencido o inválido), cierra la sesión.
  const authFetch = useCallback(async (ruta, opciones = {}) => {
    const response = await fetch(`${API_URL}${ruta}`, {
      ...opciones,
      headers: {
        'Content-Type': 'application/json',
        ...opciones.headers,
        ...(sesion?.token ? { Authorization: `Bearer ${sesion.token}` } : {})
      }
    });
    if (response.status === 401) logout();
    return response;
  }, [sesion, logout]);

  const value = useMemo(() => ({
    usuario: sesion?.usuario ?? null,
    token: sesion?.token ?? null,
    estaAutenticado: !!sesion,
    login,
    logout,
    actualizarUsuario,
    authFetch
  }), [sesion, login, logout, actualizarUsuario, authFetch]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
