import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '../auth/authContext';

const CADA_MS = 60000;

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
    return () => { activo = false; clearInterval(temporizador); };
  }, [authFetch]);

  return { pendientes, refrescar, poner: setPendientes };
}
