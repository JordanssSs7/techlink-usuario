// Los enlaces de las salas son /sala/sesion/<id> y /sala/charla/<id>. Al abrirlos (aunque haya que iniciar sesión primero) la aplicación lleva a «Mis sesiones»
// con la sala de esa sesión abierta. La dirección se guarda al arrancar y se toma una sola vez al entrar al portal.
const CLAVE = 'techlink:sala-pendiente';

export function guardarSalaDeLaDireccion() {
  const m = window.location.pathname.match(/^\/sala\/(sesion|charla)\/(\d+)\/?$/);
  if (!m) return;
  try { sessionStorage.setItem(CLAVE, `${m[1]}:${m[2]}`); } catch { /* sin almacenamiento: se abre desde Mis sesiones */ }
  window.history.replaceState(null, '', '/');
}

export function tomarSalaPendiente() {
  try {
    const id = sessionStorage.getItem(CLAVE);
    sessionStorage.removeItem(CLAVE);
    if (!id) return null;
    const [tipo, numero] = id.split(':');
    return { tipo, id: Number(numero) };
  } catch {
    return null;
  }
}
