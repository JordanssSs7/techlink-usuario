import { useEffect, useMemo, useState } from 'react';
import './Directorio.css';
import { useAuth } from '../auth/authContext';
import AgendarSesion from './AgendarSesion';
import Avatar from './Avatar';
import Cargando from './Cargando';
import Desplegable from './Desplegable';
import IconoCategoria from './IconoCategoria';
import { CATEGORIAS, categoriaDe } from './cursosCategorias';
import { PerfilPublico } from './MentoresRanking';

const DIAS = [[1, 'Lunes'], [2, 'Martes'], [3, 'Miércoles'], [4, 'Jueves'], [5, 'Viernes'], [6, 'Sábado'], [7, 'Domingo']];
const FRANJAS = [
  { id: 'manana', nombre: 'Mañana (7:00 a 12:00)', desde: '07:00', hasta: '12:00' },
  { id: 'tarde', nombre: 'Tarde (13:00 a 18:00)', desde: '13:00', hasta: '18:00' },
  { id: 'noche', nombre: 'Noche (18:00 a 22:00)', desde: '18:00', hasta: '22:00' }
];
const NOTAS = [['3', '3 estrellas o más'], ['4', '4 estrellas o más'], ['4.5', '4,5 estrellas o más']];
const SIN_FILTROS = { cursoId: '', dia: '', franja: '', minima: '' };
const MAX_CURSOS = 2;

const iniciales = (m) => [m.nombres, m.apellidos].filter(Boolean).map((x) => x.trim()[0]).join('').slice(0, 2).toUpperCase();
const nombreCompleto = (m) => [m.nombres, m.apellidos].filter(Boolean).join(' ');
const normal = (t) => String(t ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

// «Buscar mentor» en una sola pantalla: buscador, categorías, una barra de filtros y la lista de mentores.
// Los filtros de curso, día, horario y calificación los resuelve el servidor (HU-16); el texto, la categoría y el orden se aplican aquí.
export default function DirectorioMentores() {
  const { authFetch } = useAuth();
  const [cursos, setCursos] = useState([]);
  const [filtros, setFiltros] = useState(SIN_FILTROS);
  const [texto, setTexto] = useState('');
  const [categoria, setCategoria] = useState('todos');
  const [orden, setOrden] = useState('relevantes');
  const [busqueda, setBusqueda] = useState(null);
  const [error, setError] = useState('');
  const [verId, setVerId] = useState(null);
  const [agendando, setAgendando] = useState(null);

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
  const resultado = busqueda?.consulta === consulta ? busqueda.datos : null;
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

  const todos = resultado?.mentores ?? null;

  // Categorías que tienen al menos un mentor (por los cursos que dominan).
  const categoriasConMentores = useMemo(() => {
    const c = new Set();
    (todos ?? []).forEach((m) => m.cursos.forEach((curso) => c.add(categoriaDe(curso))));
    return c;
  }, [todos]);

  const visibles = useMemo(() => {
    if (!todos) return null;
    const t = normal(texto.trim());
    let lista = todos.filter((m) => {
      if (categoria !== 'todos' && !m.cursos.some((c) => categoriaDe(c) === categoria)) return false;
      if (!t) return true;
      return normal([nombreCompleto(m), m.especialidad, ...m.cursos].join(' ')).includes(t);
    });
    if (orden === 'calificacion') lista = [...lista].sort((a, b) => (b.promedio ?? -1) - (a.promedio ?? -1) || b.totalCalificaciones - a.totalCalificaciones);
    if (orden === 'horas') lista = [...lista].sort((a, b) => b.horasDictadas - a.horasDictadas);
    return lista;
  }, [todos, texto, categoria, orden]);

  const cambiar = (campo) => (e) => setFiltros((f) => ({ ...f, [campo]: e.target.value }));
  const limpiar = () => { setFiltros(SIN_FILTROS); setTexto(''); setCategoria('todos'); };
  const ampliar = (quitar) => setFiltros((f) => {
    if (quitar === 'todos') return SIN_FILTROS;
    if (quitar === 'curso') return { ...f, cursoId: '' };
    if (quitar === 'horario') return { ...f, dia: '', franja: '' };
    return { ...f, minima: '' };
  });
  const hayAlgo = hayFiltros || texto || categoria !== 'todos';

  function agendar(m) {
    const curso = filtros.cursoId ? cursos.find((c) => String(c.id) === String(filtros.cursoId))?.nombre : null;
    setAgendando({ idMentor: m.idMentor, nombres: m.nombres, apellidos: m.apellidos, especialidad: m.especialidad, cursosEnComun: curso ? [curso] : undefined });
  }

  if (agendando) {
    return (
      <section className="bm" aria-label="Agendar sesión">
        <AgendarSesion mentor={agendando} onVolver={() => setAgendando(null)} />
      </section>
    );
  }
  if (verId) {
    return (
      <PerfilPublico idMentor={verId} onVolver={() => setVerId(null)} onAgendar={(p) => agendar(p)} />
    );
  }

  return (
    <section className="dm" aria-label="Buscar mentor">
      <form className="dm-buscador" role="search" onSubmit={(e) => e.preventDefault()}>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
        <input type="search" value={texto} onChange={(e) => setTexto(e.target.value)} placeholder="Buscar por mentor, curso o palabra clave…" aria-label="Buscar mentores" />
        {texto && <button type="button" className="dm-borrar" onClick={() => setTexto('')} aria-label="Borrar búsqueda">×</button>}
      </form>

      <div className="dm-categorias" role="tablist" aria-label="Categorías">
        {CATEGORIAS.filter((c) => c.id === 'todos' || categoriasConMentores.has(c.id)).map((c) => (
          <button key={c.id} type="button" role="tab" aria-selected={categoria === c.id} className={`dm-cat${categoria === c.id ? ' activa' : ''}`}
            onClick={() => setCategoria(c.id)}>
            <IconoCategoria id={c.id} size={16} />
            {c.nombre}
          </button>
        ))}
      </div>

      <div className="dm-barra">
        <div className="dm-filtros" role="group" aria-label="Filtros">
          <Desplegable value={filtros.cursoId} onChange={cambiar('cursoId')} aria-label="Curso" className="dm-sel ancho">
            <option value="">Todos los cursos</option>
            {cursos.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
          </Desplegable>
          <Desplegable value={filtros.dia} onChange={cambiar('dia')} aria-label="Día" className="dm-sel">
            <option value="">Cualquier día</option>
            {DIAS.map(([valor, nombre]) => <option key={valor} value={String(valor)}>{nombre}</option>)}
          </Desplegable>
          <Desplegable value={filtros.franja} onChange={cambiar('franja')} aria-label="Horario" className="dm-sel ancho">
            <option value="">Cualquier horario</option>
            {FRANJAS.map((f) => <option key={f.id} value={f.id}>{f.nombre}</option>)}
          </Desplegable>
          <Desplegable value={filtros.minima} onChange={cambiar('minima')} aria-label="Calificación" className="dm-sel ancho">
            <option value="">Cualquier calificación</option>
            {NOTAS.map(([valor, nombre]) => <option key={valor} value={valor}>{nombre}</option>)}
          </Desplegable>
          {hayAlgo && (
            <button type="button" className="dm-limpiar" onClick={limpiar}>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" /></svg>
              Limpiar
            </button>
          )}
        </div>
        <div className="dm-orden">
          {visibles && <span>{visibles.length} {visibles.length === 1 ? 'mentor' : 'mentores'}</span>}
          <Desplegable value={orden} onChange={(e) => setOrden(e.target.value)} aria-label="Ordenar por" className="dm-sel">
            <option value="relevantes">Más relevantes</option>
            <option value="calificacion">Mejor calificados</option>
            <option value="horas">Más horas dictadas</option>
          </Desplegable>
        </div>
      </div>

      {error && <p className="cu-error" role="alert">{error}</p>}
      {!visibles && !error && <Cargando texto="Buscando mentores…" />}

      {visibles && visibles.length === 0 && (
        <div className="dm-vacio" role="status">
          <svg width="64" height="64" viewBox="0 0 64 64" fill="none" aria-hidden="true">
            <circle cx="28" cy="28" r="16" stroke="currentColor" strokeWidth="2.4" />
            <path d="m40 40 14 14" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" />
            <path d="M21 28h14" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" />
          </svg>
          <strong>{resultado?.mensaje || 'Ningún mentor coincide con tu búsqueda.'}</strong>
          {resultado?.sugerencias?.length > 0 && (
            <ul>
              {resultado.sugerencias.map((sug) => (
                <li key={sug.quitar}><button type="button" onClick={() => ampliar(sug.quitar)}>{sug.texto}</button></li>
              ))}
            </ul>
          )}
        </div>
      )}

      <ol className="dm-lista" key={`${consulta}|${categoria}|${orden}|${texto}`}>
        {visibles?.map((m, i) => (
          <li key={m.idMentor} className="dm-mentor" style={{ '--i': Math.min(i, 8) }} onClick={() => setVerId(m.idMentor)}>
            <div className="dm-avatar">
              <Avatar idUsuario={m.idUsuario} tieneFoto={m.tieneFoto} iniciales={iniciales(m)} tamano={64} insignia mostrarEstado />
            </div>
            <div className="dm-info">
              <h3>{nombreCompleto(m)}</h3>
              <p className="dm-linea">
                {m.promedio == null
                  ? <span className="dm-nuevo">Nuevo</span>
                  : <span className="dm-nota"><b aria-hidden="true">★</b><strong>{Number(m.promedio).toFixed(1)}</strong><small>({m.totalCalificaciones})</small></span>}
                {m.especialidad && <span className="dm-esp">{m.especialidad}</span>}
              </p>
              {m.cursos.length > 0 && (
                <ul className="dm-chips" aria-label="Cursos">
                  {m.cursos.slice(0, MAX_CURSOS).map((curso) => <li key={curso}>{curso}</li>)}
                  {m.cursos.length > MAX_CURSOS && <li className="mas">+{m.cursos.length - MAX_CURSOS}</li>}
                </ul>
              )}
            </div>
            <div className="dm-acciones">
              <button type="button" className="dm-agendar" onClick={(e) => { e.stopPropagation(); agendar(m); }}>Agendar</button>
              <button type="button" className="dm-ir" onClick={(e) => { e.stopPropagation(); setVerId(m.idMentor); }} aria-label={`Ver el perfil de ${nombreCompleto(m)}`}>
                <span>Ver perfil</span>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6" /></svg>
              </button>
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}
