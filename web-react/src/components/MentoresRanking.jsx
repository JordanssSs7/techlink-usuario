import { useEffect, useState } from 'react';
import './Cuenta.css';
import { useAuth } from '../auth/authContext';
import Avatar from './Avatar';
import AgendarSesion from './AgendarSesion';
import Cargando from './Cargando';

const iniciales = (m) => [m.nombres, m.apellidos].filter(Boolean).map((x) => x.trim()[0]).join('').slice(0, 2).toUpperCase();
const nombreCompleto = (m) => [m.nombres, m.apellidos].filter(Boolean).join(' ');
const fechaCorta = (iso) => (iso ? new Date(`${iso}T00:00:00`).toLocaleDateString('es-PE', { day: 'numeric', month: 'short', year: 'numeric' }) : '');
const horas = (h) => `${Number.isInteger(h) ? h : h.toFixed(1)} h`;

function Estrellas({ valor }) {
  const redondeado = Math.round(valor ?? 0);
  return (
    <span className="cu-estrellas" role="img" aria-label={valor == null ? 'Sin calificaciones' : `${valor} de 5 estrellas`}>
      {[1, 2, 3, 4, 5].map((n) => <span key={n} className={n <= redondeado ? 'llena' : ''} aria-hidden="true">★</span>)}
    </span>
  );
}

function Calificacion({ promedio, total }) {
  if (promedio == null) return <span className="cu-sincal">Aún sin calificaciones</span>;
  return (
    <span className="cu-cal">
      <Estrellas valor={promedio} />
      <strong>{Number(promedio).toFixed(1)}</strong>
      <span className="cu-sub">({total})</span>
    </span>
  );
}

function Insignias({ lista }) {
  if (!lista?.length) return null;
  return (
    <ul className="cu-insignias" aria-label="Insignias">
      {lista.map((i) => (
        <li key={i.codigo} title={i.descripcion}>
          <span aria-hidden="true">🏅</span> {i.nombre}
        </li>
      ))}
    </ul>
  );
}

// HU-14: perfil público de un mentor (cursos dominados, promedio de estrellas, últimos comentarios e insignias).
function PerfilPublico({ idMentor, onVolver, onAgendar }) {
  const { authFetch } = useAuth();
  const [perfil, setPerfil] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const response = await authFetch(`/api/mentores/${idMentor}/perfil-publico`);
        const data = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(data.error || '');
        if (activo) setPerfil(data);
      } catch (e) {
        if (activo) setError(e.message || 'No se pudo cargar el perfil del mentor.');
      }
    })();
    return () => { activo = false; };
  }, [authFetch, idMentor]);

  return (
    <div className="cu">
      <button type="button" className="cu-volver" onClick={onVolver}>← Volver a los mentores</button>
      {error && <p className="cu-error" role="alert">{error}</p>}
      {!perfil && !error && <Cargando texto="Cargando perfil…" />}

      {perfil && (
        <>
          <section className="cu-tarjeta cu-cabecera">
            <Avatar idUsuario={perfil.idUsuario} tieneFoto={perfil.tieneFoto} iniciales={iniciales(perfil)} tamano={96} />
            <div className="cu-cabecera-texto">
              <h2 className="cu-nombre">{nombreCompleto(perfil)}</h2>
              {perfil.especialidad && <p className="cu-sub">{perfil.especialidad}</p>}
              <Calificacion promedio={perfil.promedio} total={perfil.totalCalificaciones} />
              <p className="cu-datos">
                <span><strong>{horas(perfil.horasDictadas)}</strong> dictadas</span>
                <span><strong>{perfil.sesionesCompletadas}</strong> sesiones</span>
              </p>
              <Insignias lista={perfil.insignias} />
            </div>
            <button type="button" className="cu-boton primario" onClick={() => onAgendar(perfil)}>Agendar sesión</button>
          </section>

          {(perfil.descripcion || perfil.biografia) && (
            <section className="cu-tarjeta">
              <h3 className="cu-titulo-tarjeta">Sobre {perfil.nombres}</h3>
              {perfil.descripcion && <p>{perfil.descripcion}</p>}
              {perfil.biografia && <p>{perfil.biografia}</p>}
            </section>
          )}

          <section className="cu-tarjeta">
            <h3 className="cu-titulo-tarjeta">Cursos que domina</h3>
            {perfil.cursos.length === 0
              ? <p className="cu-sub">Todavía no registró cursos.</p>
              : <ul className="cu-chips fijos">{perfil.cursos.map((c) => <li key={c}>{c}</li>)}</ul>}
            {(perfil.fortalezas.length > 0 || perfil.areas.length > 0) && (
              <>
                {perfil.fortalezas.length > 0 && (
                  <><h4 className="cu-subtitulo">Fortalezas</h4><ul className="cu-chips fijos suave">{perfil.fortalezas.map((f) => <li key={f}>{f}</li>)}</ul></>
                )}
                {perfil.areas.length > 0 && (
                  <><h4 className="cu-subtitulo">Áreas</h4><ul className="cu-chips fijos suave">{perfil.areas.map((a) => <li key={a}>{a}</li>)}</ul></>
                )}
              </>
            )}
          </section>

          {perfil.insignias.length > 0 && (
            <section className="cu-tarjeta">
              <h3 className="cu-titulo-tarjeta">Insignias</h3>
              <ul className="cu-lista-insignias">
                {perfil.insignias.map((i) => <li key={i.codigo}><strong>🏅 {i.nombre}</strong><span className="cu-sub">{i.descripcion}</span></li>)}
              </ul>
            </section>
          )}

          <section className="cu-tarjeta">
            <h3 className="cu-titulo-tarjeta">Últimos comentarios</h3>
            {perfil.comentarios.length === 0
              ? <p className="cu-sub">Todavía no hay comentarios.</p>
              : (
                <ul className="cu-comentarios">
                  {perfil.comentarios.map((c, i) => (
                    <li key={i}>
                      <div className="cu-comentario-cab"><Estrellas valor={c.estrellas} /><span className="cu-sub">{c.estudiante} · {fechaCorta(c.fecha)}</span></div>
                      <p>“{c.comentario}”</p>
                    </li>
                  ))}
                </ul>
              )}
          </section>
        </>
      )}
    </div>
  );
}

// HU-15: ranking de mentores por promedio de estrellas y horas dictadas, con sus insignias.
// Desde aquí se abre el perfil público (HU-14) y se puede agendar.
export default function MentoresRanking() {
  const { authFetch } = useAuth();
  const [mentores, setMentores] = useState(null);
  const [error, setError] = useState('');
  const [verId, setVerId] = useState(null);
  const [agendando, setAgendando] = useState(null);

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const response = await authFetch('/api/mentores/ranking');
        const data = await response.json().catch(() => null);
        if (!response.ok) throw new Error(data?.error || '');
        if (activo) setMentores(data);
      } catch (e) {
        if (activo) setError(e.message || 'No se pudo cargar el ranking de mentores.');
      }
    })();
    return () => { activo = false; };
  }, [authFetch]);

  if (agendando) {
    return (
      <div className="cu">
        <section className="bm" aria-label="Agendar sesión">
          <AgendarSesion mentor={agendando} onVolver={() => setAgendando(null)} />
        </section>
      </div>
    );
  }

  if (verId) {
    return (
      <PerfilPublico
        idMentor={verId}
        onVolver={() => setVerId(null)}
        onAgendar={(p) => setAgendando({ idMentor: p.idMentor, nombres: p.nombres, apellidos: p.apellidos })}
      />
    );
  }

  return (
    <div className="cu">
      {error && <p className="cu-error" role="alert">{error}</p>}
      {!mentores && !error && <Cargando texto="Cargando mentores…" />}
      {mentores?.length === 0 && <p className="cu-vacio">Todavía no hay mentores registrados.</p>}

      <ol className="cu-ranking">
        {mentores?.map((m) => (
          <li key={m.idMentor} className={`cu-mentor${m.posicion <= 3 ? ' podio' : ''}`}>
            <span className="cu-puesto" aria-label={`Puesto ${m.posicion}`}>{m.posicion}</span>
            <Avatar idUsuario={m.idUsuario} tieneFoto={m.tieneFoto} iniciales={iniciales(m)} tamano={56} />
            <div className="cu-mentor-texto">
              <h3 className="cu-nombre">{nombreCompleto(m)}</h3>
              {m.especialidad && <p className="cu-sub">{m.especialidad}</p>}
              <Calificacion promedio={m.promedio} total={m.totalCalificaciones} />
              <p className="cu-datos"><span><strong>{horas(m.horasDictadas)}</strong> dictadas</span></p>
              <Insignias lista={m.insignias} />
              {m.cursos.length > 0 && <p className="cu-cursos-linea">{m.cursos.slice(0, 3).join(' · ')}{m.cursos.length > 3 ? ` · +${m.cursos.length - 3}` : ''}</p>}
            </div>
            <button type="button" className="cu-boton" onClick={() => setVerId(m.idMentor)}>Ver perfil</button>
          </li>
        ))}
      </ol>
    </div>
  );
}
