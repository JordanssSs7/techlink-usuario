import { useEffect, useState } from 'react';
import './PerfilUsuario.css';
import { useAuth } from '../auth/authContext';
import Avatar from './Avatar';
import PerfilUsuario from './PerfilUsuario';

const iniciales = (nombre) => nombre.split(' ').map((p) => p[0]).join('').slice(0, 2).toUpperCase();

// Lista de seguidores o de seguidos de una persona (tipo = 'seguidores' o 'siguiendo'). Tocar a alguien abre su perfil.
export default function SeguidoresModal({ idUsuario, tipo, onCerrar }) {
  const { authFetch } = useAuth();
  const [lista, setLista] = useState(null);
  const [viendo, setViendo] = useState(null);

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const response = await authFetch(`/api/usuarios/${idUsuario}/${tipo}`);
        if (response.ok && activo) setLista(await response.json());
        else if (activo) setLista([]);
      } catch {
        if (activo) setLista([]);
      }
    })();
    return () => { activo = false; };
  }, [authFetch, idUsuario, tipo]);

  return (
    <div className="pu-velo" onClick={onCerrar} role="presentation">
      <div className="pu-ventana pu-lista" role="dialog" aria-modal="true" aria-label={tipo === 'seguidores' ? 'Seguidores' : 'Siguiendo'} onClick={(e) => e.stopPropagation()}>
        <button type="button" className="pu-cerrar" aria-label="Cerrar" onClick={onCerrar}>✕</button>
        <h2>{tipo === 'seguidores' ? 'Seguidores' : 'Siguiendo'}</h2>
        {!lista && <p className="pu-cargando">Cargando…</p>}
        {lista?.length === 0 && <p className="pu-nota">{tipo === 'seguidores' ? 'Todavía nadie la sigue.' : 'Todavía no sigue a nadie.'}</p>}
        <ul>
          {lista?.map((p) => (
            <li key={p.idUsuario}>
              <button type="button" onClick={() => setViendo(p.idUsuario)}>
                <Avatar idUsuario={p.idUsuario} iniciales={iniciales(p.nombre)} tamano={40} insignia={p.rol === 'MENTOR'} mostrarEstado />
                <span><strong>{p.nombre}</strong><small>{p.rol === 'MENTOR' ? 'Mentor' : 'Estudiante'}{p.frase ? ` · ${p.frase}` : ''}</small></span>
              </button>
            </li>
          ))}
        </ul>
        {viendo && <PerfilUsuario idUsuario={viendo} onCerrar={() => setViendo(null)} />}
      </div>
    </div>
  );
}
