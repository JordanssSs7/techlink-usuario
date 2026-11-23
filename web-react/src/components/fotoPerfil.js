import { useEffect, useState } from 'react';
import { useAuth } from '../auth/authContext';

// Aviso entre pantallas: cuando alguien cambia su foto, el avatar del menú se actualiza solo.
export const EVENTO_PERFIL = 'techlink:perfil-actualizado';
export const avisarPerfilActualizado = () => window.dispatchEvent(new Event(EVENTO_PERFIL));

// Aviso en tiempo real del servidor: alguien (cualquiera) cambió o quitó su foto. detail = { id, v } (v = null si la quitó).
export const EVENTO_FOTO = 'techlink:foto';

// Aviso en tiempo real: alguien cambió o quitó su portada. detail = { id, v }.
export const EVENTO_PORTADA = 'techlink:portada';

// La portada de un usuario como dirección local (blob). Igual que la foto: se vuelve a pedir sola cuando cambia.
export function usePortadaUrl(idUsuario, tienePortada, version) {
  const { authFetch } = useAuth();
  const [aviso, setAviso] = useState(null);
  const [estado, setEstado] = useState({ clave: null, url: null });

  useEffect(() => {
    const alAvisar = (e) => { if (e.detail?.id === idUsuario) setAviso(e.detail); };
    window.addEventListener(EVENTO_PORTADA, alAvisar);
    return () => window.removeEventListener(EVENTO_PORTADA, alAvisar);
  }, [idUsuario]);

  const avisada = aviso && aviso.id === idUsuario ? aviso : null;
  const efectiva = avisada ? avisada.v : version;
  const hayQuePedir = Boolean(idUsuario) && (avisada ? avisada.v != null : tienePortada !== false);
  const clave = `${idUsuario}|${efectiva ?? ''}`;

  useEffect(() => {
    if (!hayQuePedir) return undefined;
    let activo = true;
    (async () => {
      try {
        const response = await authFetch(`/api/usuarios/${idUsuario}/portada${efectiva ? `?v=${encodeURIComponent(efectiva)}` : ''}`);
        if (!response.ok) { if (activo) setEstado({ clave, url: null }); return; }
        const url = URL.createObjectURL(await response.blob());
        if (activo) setEstado({ clave, url });
      } catch { /* sin portada */ }
    })();
    return () => { activo = false; };
  }, [authFetch, idUsuario, clave, efectiva, hayQuePedir]);

  return hayQuePedir && estado.clave === clave ? estado.url : null;
}

// Las fotos ya bajadas (como dirección local) por usuario y versión, para no volver a pedirlas ni parpadear al volver a una pantalla.
const guardadas = new Map();

// HU-13: la foto de un usuario como una dirección local (blob) para mostrarla en un <img>.
// La foto se pide con el token, por eso no se puede poner directo en el src. Devuelve null si no tiene foto.
// Aunque la pantalla no sepa si tiene foto (tieneFoto sin definir), se intenta pedirla: si no hay, se quedan las iniciales.
// Cuando el servidor avisa que esa persona cambió su foto, se vuelve a pedir sola, sin recargar la página.
export function useFotoUrl(idUsuario, tieneFoto, version) {
  const { authFetch } = useAuth();
  const [aviso, setAviso] = useState(null);
  const [estado, setEstado] = useState({ clave: null, url: null });

  useEffect(() => {
    const alAvisar = (e) => { if (e.detail?.id === idUsuario) setAviso(e.detail); };
    window.addEventListener(EVENTO_FOTO, alAvisar);
    return () => window.removeEventListener(EVENTO_FOTO, alAvisar);
  }, [idUsuario]);

  const avisada = aviso && aviso.id === idUsuario ? aviso : null;
  const quitada = Boolean(avisada) && avisada.v == null;
  const efectiva = avisada ? avisada.v : version;
  const hayQuePedir = Boolean(idUsuario) && !quitada && (avisada ? true : tieneFoto !== false);
  const clave = `${idUsuario}|${efectiva ?? ''}`;

  useEffect(() => {
    if (!hayQuePedir) return undefined;
    if (guardadas.has(clave)) {
      const url = guardadas.get(clave);
      queueMicrotask(() => setEstado({ clave, url }));
      return undefined;
    }
    let activo = true;
    (async () => {
      try {
        // La versión en la dirección evita que el navegador muestre una foto vieja guardada en caché.
        const response = await authFetch(`/api/usuarios/${idUsuario}/foto${efectiva ? `?v=${encodeURIComponent(efectiva)}` : ''}`);
        if (!response.ok) {
          guardadas.set(clave, null);
          if (activo) setEstado({ clave, url: null });
          return;
        }
        const url = URL.createObjectURL(await response.blob());
        guardadas.set(clave, url);
        if (activo) setEstado({ clave, url });
      } catch { /* sin foto: se muestran las iniciales */ }
    })();
    return () => { activo = false; };
  }, [authFetch, idUsuario, clave, efectiva, hayQuePedir]);

  return hayQuePedir && estado.clave === clave ? estado.url : null;
}

// Los datos de la foto de quien inició sesión. Se vuelven a pedir cuando cambia su perfil.
export function useFotoPropia() {
  const { authFetch, usuario } = useAuth();
  const [datos, setDatos] = useState({ tieneFoto: false, version: null });
  const [recarga, setRecarga] = useState(0);

  useEffect(() => {
    const alCambiar = () => setRecarga((v) => v + 1);
    window.addEventListener(EVENTO_PERFIL, alCambiar);
    return () => window.removeEventListener(EVENTO_PERFIL, alCambiar);
  }, []);

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const response = await authFetch('/api/perfil');
        if (!response.ok) return;
        const perfil = await response.json();
        if (activo) setDatos({ tieneFoto: perfil.tieneFoto, version: perfil.fotoActualizada });
      } catch { /* se quedan las iniciales */ }
    })();
    return () => { activo = false; };
  }, [authFetch, recarga]);

  return { idUsuario: usuario?.id, ...datos };
}
