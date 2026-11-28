import { useEffect, useState } from 'react';
import './Cuenta.css';
import { useAuth } from '../auth/authContext';
import Avatar from './Avatar';
import AgendarSesion from './AgendarSesion';
import Cargando from './Cargando';
import ReportarModal from './ReportarModal';
import { usePortadaUrl } from './fotoPerfil';

// La portada del perfil público (si el mentor puso una).
function PortadaPublica({ idUsuario }) {
  const url = usePortadaUrl(idUsuario);
  if (!url) return null;
  return <div className="cu-portada publica" style={{ backgroundImage: `url(${url})` }} role="img" aria-label="Portada del perfil" />;
}

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
export function PerfilPublico({ idMentor, onVolver, onAgendar, acciones = null, puedeAgendar = true, etiquetaVolver = 'Volver a los mentores' }) {
  const { authFetch, usuario } = useAuth();
  const [perfil, setPerfil] = useState(null);
  const [error, setError] = useState('');
  const [reportando, setReportando] = useState(false);

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
    <div className="cu cu-perfil">
      <button type="button" className="cu-volver" onClick={onVolver}>{etiquetaVolver}</button>
      {error && <p className="cu-error" role="alert">{error}</p>}
      {!perfil && !error && <Cargando texto="Cargando perfil…" />}

      {perfil && (
        <>
          <PortadaPublica idUsuario={perfil.idUsuario} />

          <section className="pf-cabecera">
            <div className="pf-id">
              <Avatar idUsuario={perfil.idUsuario} tieneFoto={perfil.tieneFoto} iniciales={iniciales(perfil)} tamano={104} insignia mostrarEstado />
              <div className="pf-nombre">
                <h2>{nombreCompleto(perfil)}</h2>
                {perfil.especialidad && <p className="pf-esp">{perfil.especialidad}</p>}
                {perfil.frase && <p className="pf-frase"><span aria-hidden="true">💬</span>{perfil.frase}</p>}
                {acciones}
              </div>
            </div>

            <div className="pf-acciones">
              {puedeAgendar && <button type="button" className="pf-agendar" onClick={() => onAgendar(perfil)}>Agendar sesión</button>}
              {perfil.idUsuario !== usuario.id && (
                <button type="button" className="pf-reportar" onClick={() => setReportando(true)}>Reportar</button>
              )}
            </div>

            <dl className="pf-cifras">
              <div>
                <dt>Calificación</dt>
                <dd>{perfil.promedio == null ? <span className="pf-nuevo">Nuevo</span> : <><b aria-hidden="true">★</b>{Number(perfil.promedio).toFixed(1)}<small>({perfil.totalCalificaciones})</small></>}</dd>
              </div>
              <div><dt>Horas dictadas</dt><dd>{horas(perfil.horasDictadas)}</dd></div>
              <div><dt>Sesiones</dt><dd>{perfil.sesionesCompletadas}</dd></div>
            </dl>
          </section>

          <div className="pf-cuerpo">
            <div className="pf-col">
              {(perfil.descripcion || perfil.biografia) && (
                <section className="pf-tarjeta">
                  <h3>Sobre {perfil.nombres}</h3>
                  {perfil.descripcion && <p>{perfil.descripcion}</p>}
                  {perfil.biografia && <p>{perfil.biografia}</p>}
                </section>
              )}

              <section className="pf-tarjeta">
                <h3>Últimos comentarios</h3>
                {perfil.comentarios.length === 0
                  ? <p className="pf-vacio">Todavía no hay comentarios.</p>
                  : (
                    <ul className="pf-comentarios">
                      {perfil.comentarios.map((c, i) => (
                        <li key={i}>
                          <div className="pf-comentario-cab"><Estrellas valor={c.estrellas} /><span>{c.estudiante} · {fechaCorta(c.fecha)}</span></div>
                          <p>“{c.comentario}”</p>
                        </li>
                      ))}
                    </ul>
                  )}
              </section>
            </div>

            <aside className="pf-col pf-lateral">
              <section className="pf-tarjeta">
                <h3>Cursos que domina</h3>
                {perfil.cursos.length === 0
                  ? <p className="pf-vacio">Todavía no registró cursos.</p>
                  : <ul className="pf-chips oscuro">{perfil.cursos.map((c) => <li key={c}>{c}</li>)}</ul>}
                {perfil.fortalezas.length > 0 && (
                  <><h4>Fortalezas</h4><ul className="pf-chips">{perfil.fortalezas.map((f) => <li key={f}>{f}</li>)}</ul></>
                )}
                {perfil.areas.length > 0 && (
                  <><h4>Áreas</h4><ul className="pf-chips">{perfil.areas.map((a) => <li key={a}>{a}</li>)}</ul></>
                )}
              </section>

              {perfil.insignias.length > 0 && (
                <section className="pf-tarjeta">
                  <h3>Insignias</h3>
                  <ul className="pf-insignias">
                    {perfil.insignias.map((i) => <li key={i.codigo}><span aria-hidden="true">🏅</span><div><strong>{i.nombre}</strong><small>{i.descripcion}</small></div></li>)}
                  </ul>
                </section>
              )}
            </aside>
          </div>

          {reportando && (
            <ReportarModal
              tipo="USUARIO"
              idUsuario={perfil.idUsuario}
              titulo={`Reportar a ${nombreCompleto(perfil)}`}
              subtitulo="Cuéntanos qué pasó. Un administrador revisará tu reporte."
              onCerrar={() => setReportando(false)}
            />
          )}
        </>
      )}
    </div>
  );
}

const DIAS = [
  [1, 'Lunes'], [2, 'Martes'], [3, 'Miércoles'], [4, 'Jueves'], [5, 'Viernes'], [6, 'Sábado'], [7, 'Domingo']
];
const FRANJAS = [
  { id: 'manana', nombre: 'Mañana (7:00 a 12:00)', desde: '07:00', hasta: '12:00' },
  { id: 'tarde', nombre: 'Tarde (13:00 a 18:00)', desde: '13:00', hasta: '18:00' },
  { id: 'noche', nombre: 'Noche (18:00 a 22:00)', desde: '18:00', hasta: '22:00' }
];
const NOTAS = [['3', '3 o más'], ['4', '4 o más'], ['4.5', '4,5 o más']];
const SIN_FILTROS = { cursoId: '', dia: '', franja: '', minima: '' };

// HU-15: ranking de mentores por promedio de estrellas y horas dictadas, con sus insignias.
// HU-16: se puede buscar y filtrar por curso, horario y calificación mínima (los filtros se combinan); sin resultados se
// explica y se sugiere qué filtro ampliar. Desde aquí se abre el perfil público (HU-14) y se puede agendar.
export default function MentoresRanking() {
  const { authFetch } = useAuth();
  const [cursos, setCursos] = useState([]);
  const [filtros, setFiltros] = useState(SIN_FILTROS);
  const [busqueda, setBusqueda] = useState(null);
  const [error, setError] = useState('');
  const [verId, setVerId] = useState(null);
  const [agendando, setAgendando] = useState(null);

  // Parámetros de la búsqueda según los filtros elegidos; el texto sirve también para saber si la respuesta es la actual.
  const consulta = (() => {
    const params = new URLSearchParams();
    if (filtros.cursoId) params.set('cursoId', filtros.cursoId);
    if (filtros.dia) params.set('dia', filtros.dia);
    const franja = FRANJAS.find((f) => f.id === filtros.franja);
    if (franja) {
      params.set('desde', franja.desde);
      params.set('hasta', franja.hasta);
    }
    if (filtros.minima) params.set('calificacionMinima', filtros.minima);
    return params.toString();
  })();
  const mentores = busqueda?.consulta === consulta ? busqueda.datos.mentores : null;
  const hayFiltros = Object.values(filtros).some(Boolean);

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const response = await authFetch('/api/cursos');
        const data = await response.json().catch(() => null);
        if (response.ok && activo) setCursos(data);
      } catch {
        // Sin el catálogo igual se puede buscar por horario y calificación.
      }
    })();
    return () => { activo = false; };
  }, [authFetch]);

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const response = await authFetch(`/api/mentores/buscar${consulta ? `?${consulta}` : ''}`);
        const data = await response.json().catch(() => null);
        if (!response.ok) throw new Error(data?.error || '');
        if (!activo) return;
        setBusqueda({ consulta, datos: data });
        setError('');
      } catch (e) {
        if (activo) setError(e.message || 'No se pudo cargar la lista de mentores.');
      }
    })();
    return () => { activo = false; };
  }, [authFetch, consulta]);

  const cambiar = (campo) => (e) => setFiltros((f) => ({ ...f, [campo]: e.target.value }));
  // Una sugerencia quita el filtro que dejaba sin resultados (o todos).
  const ampliar = (quitar) => setFiltros((f) => {
    if (quitar === 'todos') return SIN_FILTROS;
    if (quitar === 'curso') return { ...f, cursoId: '' };
    if (quitar === 'horario') return { ...f, dia: '', franja: '' };
    return { ...f, minima: '' };
  });

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

  const resultado = busqueda?.consulta === consulta ? busqueda.datos : null;

  return (
    <div className="cu">
      <form className="cu-filtros" onSubmit={(e) => e.preventDefault()} aria-label="Buscar mentores">
        <label>
          Curso
          <select value={filtros.cursoId} onChange={cambiar('cursoId')}>
            <option value="">Todos los cursos</option>
            {cursos.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
          </select>
        </label>
        <label>
          Día
          <select value={filtros.dia} onChange={cambiar('dia')}>
            <option value="">Cualquier día</option>
            {DIAS.map(([valor, nombre]) => <option key={valor} value={valor}>{nombre}</option>)}
          </select>
        </label>
        <label>
          Horario
          <select value={filtros.franja} onChange={cambiar('franja')}>
            <option value="">Cualquier hora</option>
            {FRANJAS.map((f) => <option key={f.id} value={f.id}>{f.nombre}</option>)}
          </select>
        </label>
        <label>
          Calificación mínima
          <select value={filtros.minima} onChange={cambiar('minima')}>
            <option value="">Cualquiera</option>
            {NOTAS.map(([valor, nombre]) => <option key={valor} value={valor}>{nombre} ★</option>)}
          </select>
        </label>
        {hayFiltros && <button type="button" className="cu-boton" onClick={() => setFiltros(SIN_FILTROS)}>Limpiar filtros</button>}
      </form>

      {error && <p className="cu-error" role="alert">{error}</p>}
      {!mentores && !error && <Cargando texto="Buscando mentores…" />}
      {resultado && mentores.length > 0 && hayFiltros && (
        <p className="cu-sub" aria-live="polite">{mentores.length} {mentores.length === 1 ? 'mentor coincide' : 'mentores coinciden'} con tu búsqueda</p>
      )}
      {resultado && mentores.length === 0 && (
        <div className="cu-sin-resultados" role="status">
          <p className="cu-vacio">{resultado.mensaje}</p>
          {resultado.sugerencias.length > 0 && (
            <>
              <p className="cu-sub">Prueba ampliando la búsqueda:</p>
              <ul className="cu-sugerencias">
                {resultado.sugerencias.map((sug) => (
                  <li key={sug.quitar}>
                    <button type="button" className="cu-boton" onClick={() => ampliar(sug.quitar)}>{sug.texto}</button>
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      )}

      <ol className="cu-ranking">
        {mentores?.map((m) => (
          <li key={m.idMentor} className={`cu-mentor${m.posicion <= 3 ? ' podio' : ''}`}>
            <span className="cu-puesto" aria-label={`Puesto ${m.posicion}`}>{String(m.posicion).padStart(2, '0')}</span>
            <Avatar idUsuario={m.idUsuario} tieneFoto={m.tieneFoto} iniciales={iniciales(m)} tamano={60} insignia mostrarEstado />
            <div className="cu-mentor-texto">
              <h3 className="cu-nombre">{nombreCompleto(m)}</h3>
              {m.especialidad && <p className="cu-sub">{m.especialidad}</p>}
              <Insignias lista={m.insignias} />
              {m.cursos.length > 0 && (
                <ul className="cu-cursos-chips" aria-label="Cursos">
                  {m.cursos.slice(0, 3).map((curso) => <li key={curso}>{curso}</li>)}
                  {m.cursos.length > 3 && <li className="mas">+{m.cursos.length - 3}</li>}
                </ul>
              )}
            </div>
            <div className="cu-mentor-cifras">
              <Calificacion promedio={m.promedio} total={m.totalCalificaciones} />
              <p className="cu-datos"><span><strong>{horas(m.horasDictadas)}</strong> dictadas</span></p>
            </div>
            <button type="button" className="cu-boton" onClick={() => setVerId(m.idMentor)}>Ver perfil</button>
          </li>
        ))}
      </ol>
    </div>
  );
}
