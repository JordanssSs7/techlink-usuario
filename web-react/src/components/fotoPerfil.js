import { useEffect, useState } from 'react';
import { useAuth } from '../auth/authContext';

// Aviso entre pantallas: cuando alguien cambia su foto, el avatar del menú se actualiza solo.
export const EVENTO_PERFIL = 'techlink:perfil-actualizado';
export const avisarPerfilActualizado = () => window.dispatchEvent(new Event(EVENTO_PERFIL));

// HU-13: la foto de un usuario como una dirección local (blob) para mostrarla en un <img>.
// La foto se pide con el token, por eso no se puede poner directo en el src. Devuelve null si no tiene foto.
export function useFotoUrl(idUsuario, tieneFoto, version) {
  const { authFetch } = useAuth();
  const clave = `${idUsuario}|${version ?? ''}`;
  const [estado, setEstado] = useState({ clave: null, url: null });

  useEffect(() => {
    if (!tieneFoto || !idUsuario) return undefined;
    let activo = true;
    let objeto = null;
    (async () => {
      try {
        // La versión en la dirección evita que el navegador muestre una foto vieja guardada en caché.
        const response = await authFetch(`/api/usuarios/${idUsuario}/foto${version ? `?v=${encodeURIComponent(version)}` : ''}`);
        if (!response.ok) return;
        objeto = URL.createObjectURL(await response.blob());
        if (activo) setEstado({ clave, url: objeto });
        else URL.revokeObjectURL(objeto);
      } catch { /* sin foto: se muestran las iniciales */ }
    })();
    return () => {
      activo = false;
      if (objeto) URL.revokeObjectURL(objeto);
    };
  }, [authFetch, idUsuario, tieneFoto, version, clave]);

  return tieneFoto && estado.clave === clave ? estado.url : null;
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
