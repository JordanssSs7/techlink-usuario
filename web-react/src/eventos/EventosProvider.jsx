import { useEffect, useMemo, useState } from 'react';
import { useAuth } from '../auth/authContext';
import { EventosContext } from './eventosContext';
import { EVENTO_FOTO, EVENTO_PORTADA } from '../components/fotoPerfil';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

// Aviso en el navegador de que llegó una notificación nueva (la campanita y la lista de avisos se actualizan solas).
export const EVENTO_NOTIFICACION = 'techlink:notificacion';

// Tiempo real: mientras haya sesión, mantiene abierta una conexión con el servidor (/ws/eventos) que avisa
//  - quién está en línea (puntito verde "Disponible"),
//  - cuando llega un aviso nuevo,
//  - cuando alguien cambia o quita su foto.
// Si la conexión se cae, se reintenta sola con esperas cada vez más largas.
export default function EventosProvider({ children }) {
  const { estaAutenticado, authFetch } = useAuth();
  const [enLinea, setEnLinea] = useState(() => new Set());
  const [conectado, setConectado] = useState(false);

  useEffect(() => {
    if (!estaAutenticado) return undefined;
    let cerrado = false;
    let ws = null;
    let temporizador = null;
    let intentos = 0;

    const conectar = async () => {
      try {
        const response = await authFetch('/api/eventos/ticket', { method: 'POST' });
        if (!response.ok) throw new Error('sin ticket');
        const { ticket } = await response.json();
        if (cerrado) return;
        ws = new WebSocket(`${API_URL.replace(/^http/, 'ws')}/ws/eventos?ticket=${encodeURIComponent(ticket)}`);
        ws.onopen = () => { intentos = 0; setConectado(true); };
        ws.onmessage = (mensaje) => {
          let dato;
          try { dato = JSON.parse(mensaje.data); } catch { return; }
          if (dato.t === 'presencia-inicial') {
            setEnLinea(new Set(dato.enLinea));
          } else if (dato.t === 'presencia') {
            setEnLinea((anterior) => {
              const siguiente = new Set(anterior);
              if (dato.enLinea) siguiente.add(dato.id); else siguiente.delete(dato.id);
              return siguiente;
            });
          } else if (dato.t === 'notificacion') {
            window.dispatchEvent(new Event(EVENTO_NOTIFICACION));
          } else if (dato.t === 'portada') {
            window.dispatchEvent(new CustomEvent(EVENTO_PORTADA, { detail: { id: dato.id, v: dato.v } }));
          } else if (dato.t === 'foto') {
            window.dispatchEvent(new CustomEvent(EVENTO_FOTO, { detail: { id: dato.id, v: dato.v } }));
          }
        };
        ws.onclose = () => {
          setConectado(false);
          setEnLinea(new Set());
          if (cerrado) return;
          intentos += 1;
          temporizador = setTimeout(conectar, Math.min(30000, 1000 * 2 ** Math.min(intentos, 5)));
        };
      } catch {
        if (cerrado) return;
        intentos += 1;
        temporizador = setTimeout(conectar, Math.min(30000, 1000 * 2 ** Math.min(intentos, 5)));
      }
    };
    conectar();

    return () => {
      cerrado = true;
      clearTimeout(temporizador);
      if (ws) ws.close();
    };
  }, [estaAutenticado, authFetch]);

  const valor = useMemo(() => ({ enLinea, conectado }), [enLinea, conectado]);
  return <EventosContext.Provider value={valor}>{children}</EventosContext.Provider>;
}
