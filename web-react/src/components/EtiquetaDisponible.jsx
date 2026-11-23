import { useEnLinea } from '../eventos/eventosContext';

// El botoncito verde "Disponible" de quien tiene la sesión abierta ahora mismo (mentor o estudiante). Se actualiza en tiempo real.
export default function EtiquetaDisponible({ idUsuario }) {
  const enLinea = useEnLinea(idUsuario);
  return enLinea ? <span className="etiqueta-disponible">Disponible</span> : null;
}
