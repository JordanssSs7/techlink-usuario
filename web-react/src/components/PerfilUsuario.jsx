import { useCallback, useEffect, useState } from 'react';
import './Cuenta.css';
import './PerfilUsuario.css';
import { useAuth } from '../auth/authContext';
import Avatar from './Avatar';
import AgendarSesion from './AgendarSesion';
import Cargando from './Cargando';
import { PerfilPublico } from './MentoresRanking';
import { usePortadaUrl } from './fotoPerfil';
import SeguidoresModal from './SeguidoresModal';
import EtiquetaDisponible from './EtiquetaDisponible';

const iniciales = (p) => [p.nombres, p.apellidos].filter(Boolean).map((x) => x.trim()[0]).join('').slice(0, 2).toUpperCase();

// Seguidores, siguiendo y el botón de seguir: va dentro de la tarjeta de cabecera del perfil.
function BarraSeguir({ perfil, ocupado, onSeguir, onVerLista }) {
  return (
    <div className="pg-seguir">
      <p className="pg-conteo">
        <button type="button" onClick={() => onVerLista('seguidores')}><strong>{perfil.seguidores}</strong> {perfil.seguidores === 1 ? 'seguidor' : 'seguidores'}</button>
        {' · '}
        <button type="button" onClick={() => onVerLista('siguiendo')}><strong>{perfil.siguiendoTotal}</strong> siguiendo</button>
      </p>
      {!perfil.esYo && (
        <button type="button" className={`cu-boton${perfil.siguiendo ? '' : ' primario'}`} disabled={ocupado} onClick={onSeguir}>
          {perfil.siguiendo ? 'Siguiendo · Dejar de seguir' : (perfil.meSigue ? 'Seguir también' : 'Seguir')}
        </button>
      )}
    </div>
  );
}

// El perfil de otra persona, en pantalla grande y con el mismo formato que el perfil de un mentor del ranking: cabecera con foto, estado,
// seguidores y botón de seguir, y debajo su descripción, intereses y, si es mentor, sus cursos, calificaciones e insignias.
export default function PerfilUsuario({ idUsuario, onCerrar, onCambio }) {
  const { authFetch, usuario } = useAuth();
  const [perfil, setPerfil] = useState(null);
  const [error, setError] = useState('');
  const [ocupado, setOcupado] = useState(false);
  const [lista, setLista] = useState(null); // 'seguidores' o 'siguiendo'
  const [agendando, setAgendando] = useState(null);
  const portada = usePortadaUrl(idUsuario);

  const cargar = useCallback(async () => {
    try {
      const response = await authFetch(`/api/usuarios/${idUsuario}/perfil-social`);
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.error || '');
      setPerfil(data);
      setError('');
    } catch (e) {
      setError(e.message || 'No se pudo cargar el perfil.');
    }
  }, [authFetch, idUsuario]);

  useEffect(() => {
    // Se pide después de montar; el estado se actualiza al llegar la respuesta.
    const temporizador = setTimeout(cargar, 0);
    return () => clearTimeout(temporizador);
  }, [cargar]);

  useEffect(() => {
    const escape = (e) => { if (e.key === 'Escape' && !lista) onCerrar(); };
    document.addEventListener('keydown', escape);
    return () => document.removeEventListener('keydown', escape);
  }, [onCerrar, lista]);

  async function alternarSeguir() {
    setOcupado(true);
    try {
      const response = await authFetch(`/api/usuarios/${idUsuario}/seguir`, { method: perfil.siguiendo ? 'DELETE' : 'POST' });
      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        setError(data.error || 'No se pudo completar la acción.');
        return;
      }
      await cargar();
      onCambio?.();
    } catch {
      setError('No se pudo conectar con el servidor.');
    } finally {
      setOcupado(false);
    }
  }

  const barra = perfil && (
    <BarraSeguir perfil={perfil} ocupado={ocupado} onSeguir={alternarSeguir} onVerLista={setLista} />
  );
  const nombre = perfil ? `${perfil.nombres} ${perfil.apellidos}`.trim() : '';

  return (
    <div className="pg-velo" role="dialog" aria-modal="true" aria-label={perfil ? `Perfil de ${nombre}` : 'Perfil'}>
      <div className="pg-contenedor">
        {agendando ? (
          <AgendarSesion mentor={agendando} onVolver={() => setAgendando(null)} />
        ) : (
          <>
            {error && <p className="cu-error" role="alert">{error}</p>}
            {!perfil && !error && <Cargando texto="Cargando perfil…" />}
            {perfil && perfil.idMentor && (
              <PerfilPublico
                idMentor={perfil.idMentor}
                onVolver={onCerrar}
                etiquetaVolver="Volver"
                acciones={barra}
                puedeAgendar={usuario.rol === 'ESTUDIANTE' && !perfil.esYo}
                onAgendar={(p) => setAgendando({ idMentor: p.idMentor, nombres: p.nombres, apellidos: p.apellidos })}
              />
            )}
            {perfil && !perfil.idMentor && (
              <div className="cu">
                <button type="button" className="cu-volver" onClick={onCerrar}>Volver</button>
                {portada && <div className="cu-portada publica" style={{ backgroundImage: `url(${portada})` }} role="img" aria-label="Portada del perfil" />}
                <section className="cu-tarjeta cu-cabecera">
                  <Avatar idUsuario={perfil.idUsuario} iniciales={iniciales(perfil)} tamano={96} mostrarEstado />
                  <div className="cu-cabecera-texto">
                    <h2 className="cu-nombre">{nombre}</h2>
                    {perfil.apodo && <p className="cu-sub">{perfil.apodo}</p>}
                    <p className="cu-sub">Estudiante <EtiquetaDisponible idUsuario={perfil.idUsuario} /></p>
                    {perfil.frase && <p className="cu-estado fijo"><span aria-hidden="true">💬</span>{perfil.frase}</p>}
                    {barra}
                  </div>
                </section>
                {perfil.descripcion && (
                  <section className="cu-tarjeta">
                    <h3 className="cu-titulo-tarjeta">Sobre {perfil.nombres}</h3>
                    <p>{perfil.descripcion}</p>
                  </section>
                )}
                {perfil.intereses.length > 0 && (
                  <section className="cu-tarjeta">
                    <h3 className="cu-titulo-tarjeta">Intereses</h3>
                    <ul className="cu-chips fijos">{perfil.intereses.map((i) => <li key={i}>{i}</li>)}</ul>
                  </section>
                )}
                {!perfil.descripcion && perfil.intereses.length === 0 && (
                  <section className="cu-tarjeta"><p className="cu-sub">Todavía no completó su perfil.</p></section>
                )}
              </div>
            )}
          </>
        )}
      </div>
      {lista && <SeguidoresModal idUsuario={idUsuario} tipo={lista} onCerrar={() => setLista(null)} />}
    </div>
  );
}
