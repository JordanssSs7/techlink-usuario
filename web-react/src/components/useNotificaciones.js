import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '../auth/authContext';

// Tiempo real: el servidor avisa al instante; el sondeo lento queda de respaldo (por ejemplo, para anuncios que crea el panel de administración).
const CADA_MS = 45000;
const EVENTO_NOTIFICACION = 'techlink:notificacion';

// HU-22: cantidad de notificaciones sin leer, para el indicador del menú. Se consulta al entrar y luego cada minuto;
// "refrescar" la vuelve a pedir y "poner" la fija (por ejemplo, al marcar todo como leído).
export default function useNotificacionesPendientes() {
  const { authFetch } = useAuth();
  const [pendientes, setPendientes] = useState(0);

  const refrescar = useCallback(async () => {
    try {
      const response = await authFetch('/api/notificaciones/resumen');
      if (!response.ok) return;
      const data = await response.json();
      setPendientes(data.noLeidas);
    } catch {
      // Sin conexión: se conserva el número anterior.
    }
  }, [authFetch]);

  useEffect(() => {
    let activo = true;
    const consultar = async () => {
      try {
        const response = await authFetch('/api/notificaciones/resumen');
        if (!response.ok) return;
        const data = await response.json();
        if (activo) setPendientes(data.noLeidas);
      } catch {
        // Sin conexión: se conserva el número anterior.
      }
    };
    consultar();
    const temporizador = setInterval(consultar, CADA_MS);
    window.addEventListener(EVENTO_NOTIFICACION, consultar);
    return () => { activo = false; clearInterval(temporizador); window.removeEventListener(EVENTO_NOTIFICACION, consultar); };
  }, [authFetch]);

  return { pendientes, refrescar, poner: setPendientes };
}
