const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

// Modo de prueba SOLO en desarrollo: simula el backend mientras no existan /login y /google.
// Se activa con VITE_MOCK_AUTH=true en .env.local. La contraseña "incorrecta" simula un 401.
export const MOCK_AUTH = import.meta.env.DEV && import.meta.env.VITE_MOCK_AUTH === 'true';

// ciclo = null simula una cuenta recién creada con Google, que aún debe elegir su ciclo.
function usuarioDemo(correo, ciclo) {
  return {
    ok: true,
    status: 200,
    data: {
      token: 'token-de-prueba',
      usuario: { id: 0, nombres: 'Usuario', apellidos: 'Demo', correoInstitucional: correo, rol: 'ESTUDIANTE', ciclo }
    }
  };
}

async function post(ruta, cuerpo) {
  const response = await fetch(`${API_URL}${ruta}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(cuerpo)
  });
  const data = await response.json().catch(() => ({}));
  return { ok: response.ok, status: response.status, data };
}

// Devuelve { ok, status, data }. Lanza si no hay conexión con el servidor.
export async function loginRequest(correoInstitucional, password) {
  if (MOCK_AUTH) {
    if (password === 'incorrecta') {
      return { ok: false, status: 401, data: { error: 'Correo o contraseña incorrectos' } };
    }
    return usuarioDemo(correoInstitucional, 1);
  }
  return post('/api/auth/login', { correoInstitucional, password });
}

export async function googleRequest(code) {
  if (MOCK_AUTH) return usuarioDemo('demo.google@tecsup.edu.pe', null);
  return post('/api/auth/google', { code });
}
