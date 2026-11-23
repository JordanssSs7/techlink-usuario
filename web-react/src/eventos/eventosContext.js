import { createContext, useContext } from 'react';

// HU-22 y perfil: lo que el servidor avisa en tiempo real a quien tiene la sesión abierta (quién está en línea, avisos nuevos, fotos
// que cambian). El proveedor (EventosProvider) llena este contexto; sin él, nadie aparece en línea.
export const EventosContext = createContext({ enLinea: new Set(), conectado: false });

// ¿Está conectada ahora mismo esa persona?
export function useEnLinea(idUsuario) {
  const { enLinea } = useContext(EventosContext);
  return idUsuario != null && enLinea.has(idUsuario);
}
