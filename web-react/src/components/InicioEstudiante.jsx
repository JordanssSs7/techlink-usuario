import { useEffect, useState } from 'react';
import './Inicio.css';
import { useAuth } from '../auth/authContext';
import Avatar from './Avatar';
import EstrellasNota from './EstrellasNota';
import EtiquetaDisponible from './EtiquetaDisponible';

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
const DIAS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];
const LETRAS = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];

const aFecha = (fecha, hora) => {
  const [a, m, d] = fecha.split('-').map(Number);
  const [h, min] = String(hora).split(':').map(Number);
  return new Date(a, m - 1, d, h, min);
};
const aIso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const hora = (t) => String(t).slice(0, 5);
const reducirMovimiento = () => typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

// "Hoy", "Mañana" o "jueves 26 nov".
function diaLegible(inicio, ahora) {
  const base = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const dias = Math.round((base(inicio) - base(ahora)) / 86400000);
  if (dias === 0) return 'Hoy';
  if (dias === 1) return 'Mañana';
  return `${DIAS[inicio.getDay()].replace(/^./, (c) => c.toUpperCase())} ${inicio.getDate()} ${MESES[inicio.getMonth()]}`;
}

// Cuánto falta, en palabras sencillas (se recalcula cada medio minuto).
function faltan(inicio, fin, ahora) {
  if (ahora >= inicio && ahora < fin) return 'En curso ahora';
  const min = Math.round((inicio - ahora) / 60000);
  if (min < 60) return `En ${Math.max(min, 1)} min`;
  const h = Math.round(min / 60);
  if (h < 24) return `En ${h} ${h === 1 ? 'hora' : 'horas'}`;
  const d = Math.round(h / 24);
  return `En ${d} ${d === 1 ? 'día' : 'días'}`;
}

function hace(iso, ahora) {
  const min = Math.max(0, Math.round((ahora - new Date(iso)) / 60000));
  if (Number.isNaN(min)) return '';
  if (min < 1) return 'ahora';
  if (min < 60) return `hace ${min} min`;
  const h = Math.round(min / 60);
  if (h < 24) return `hace ${h} h`;
  const d = Math.round(h / 24);
  return `hace ${d} ${d === 1 ? 'día' : 'días'}`;
}

const iniciales = (m) => [m.nombres, m.apellidos].filter((x) => x && x !== '-').map((x) => x.trim()[0]).join('').slice(0, 2).toUpperCase();
const horasTexto = (h) => `${Number.isInteger(h) ? h : h.toFixed(1)} h`;

async function pedir(authFetch, ruta) {
  const response = await authFetch(ruta);
  const data = await response.json().catch(() => null);
  if (!response.ok) throw new Error(data?.error || 'error');
  return data;
}

// La hora actual, que se actualiza sola cada medio minuto: así la cuenta regresiva de la sesión está viva.
function useAhora() {
  const [ahora, setAhora] = useState(() => new Date());
  useEffect(() => {
    const t = setInterval(() => setAhora(new Date()), 30000);
    return () => clearInterval(t);
  }, []);
  return ahora;
}

// Un número que sube desde 0 hasta su valor al aparecer.
function Numero({ valor }) {
  const [mostrado, setMostrado] = useState(() => (reducirMovimiento() ? valor : 0));
  useEffect(() => {
    if (reducirMovimiento()) return undefined;
    let cuadro;
    const inicio = performance.now();
    const paso = (t) => {
      const avance = Math.min(1, (t - inicio) / 700);
      setMostrado(Math.round(valor * (1 - (1 - avance) ** 3)));
      if (avance < 1) cuadro = requestAnimationFrame(paso);
    };
    cuadro = requestAnimationFrame(paso);
    return () => cancelAnimationFrame(cuadro);
  }, [valor]);
  return <>{reducirMovimiento() ? valor : mostrado}</>;
}

function Esqueleto({ lineas = 2 }) {
  return (
    <div className="in-esqueleto" aria-hidden="true">
      {Array.from({ length: lineas }, (_, i) => <span key={i} style={{ width: `${88 - i * 24}%` }} />)}
    </div>
  );
}

// Los siete días de la semana, con una marca en los que tienen algo: una sesión, una meta que vence o una charla.
function SemanaTira({ ahora, sesiones, metas, charlas, irA }) {
  const lunes = new Date(ahora.getFullYear(), ahora.getMonth(), ahora.getDate() - ((ahora.getDay() + 6) % 7));
  const dias = Array.from({ length: 7 }, (_, i) => new Date(lunes.getFullYear(), lunes.getMonth(), lunes.getDate() + i));
  const hoy = aIso(ahora);
  const cuenta = (lista, campo, d) => lista.filter((x) => x[campo] === aIso(d)).length;
  const hayAlgo = dias.some((d) => cuenta(sesiones, 'fecha', d) + cuenta(metas, 'fechaEntrega', d) + cuenta(charlas, 'fecha', d) > 0);

  return (
    <>
      <ol className="in-semana" aria-label="Esta semana">
        {dias.map((d, i) => {
          const s = cuenta(sesiones, 'fecha', d);
          const m = cuenta(metas, 'fechaEntrega', d);
          const c = cuenta(charlas, 'fecha', d);
          const partes = [s && `${s} ${s === 1 ? 'sesión' : 'sesiones'}`, m && `${m} ${m === 1 ? 'meta' : 'metas'}`, c && `${c} ${c === 1 ? 'charla' : 'charlas'}`].filter(Boolean);
          const esHoy = aIso(d) === hoy;
          return (
            <li key={aIso(d)}>
              <button type="button" className={`in-dia${esHoy ? ' hoy' : ''}${partes.length ? ' con-algo' : ''}`} style={{ '--i': i }} onClick={() => irA('calendario')}
                title={partes.length ? `${DIAS[d.getDay()]} ${d.getDate()}: ${partes.join(', ')}` : `${DIAS[d.getDay()]} ${d.getDate()}: libre`}
                aria-label={`${DIAS[d.getDay()]} ${d.getDate()}${esHoy ? ' (hoy)' : ''}: ${partes.length ? partes.join(', ') : 'sin actividades'}`}>
                <span className="in-dia-letra">{LETRAS[i]}</span>
                <span className="in-dia-num">{d.getDate()}</span>
                <span className="in-marcas" aria-hidden="true">
                  {s > 0 && <i className="m-sesion" />}
                  {m > 0 && <i className="m-meta" />}
                  {c > 0 && <i className="m-charla" />}
                </span>
              </button>
            </li>
          );
        })}
      </ol>
      {hayAlgo && (
        <p className="in-leyenda" aria-hidden="true">
          <span><i className="m-sesion" /> Sesión</span>
          <span><i className="m-meta" /> Meta</span>
          <span><i className="m-charla" /> Charla</span>
        </p>
      )}
    </>
  );
}

// Las horas de asesoría de las últimas semanas, en barras pequeñas que crecen al aparecer.
function Barritas({ periodos }) {
  const maximo = Math.max(1, ...periodos.map((p) => p.horas));
  return (
    <ol className="in-barritas" aria-label="Horas de asesoría por semana">
      {periodos.map((p, i) => (
        <li key={p.desde} title={`${p.etiqueta}: ${horasTexto(p.horas)}`}>
          <span className={i === periodos.length - 1 ? 'actual' : ''} style={{ height: `${Math.max(6, (p.horas / maximo) * 100)}%`, '--i': i }} />
        </li>
      ))}
    </ol>
  );
}

const trazo = { width: 20, height: 20, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 2, strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': true };
const IconoCalendario = () => <svg {...trazo}><rect x="3" y="5" width="18" height="16" rx="3" /><path d="M8 3v4M16 3v4M3 10h18" /></svg>;
const IconoBarras = () => <svg {...trazo}><path d="M6 20v-7M12 20V5M18 20v-10" /></svg>;
const IconoEstrella = () => <svg {...trazo}><path d="m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z" /></svg>;

// Bloques que suben como escalera: "avanza". Se arman con cuadros, como el logo, sin ilustraciones.
const ESCALON = [1, 2, 2, 3, 4, 4, 5];
function Escalera() {
  return (
    <div className="in-escalera" aria-hidden="true">
      {ESCALON.map((alto, c) => (
        <div key={c} className="in-col">
          {Array.from({ length: alto }, (_, f) => (
            <i key={f} style={{ '--d': `${c * 70 + f * 40}ms` }} className={f === alto - 1 && c === ESCALON.length - 1 ? 'cima' : ''} />
          ))}
        </div>
      ))}
    </div>
  );
}

// Inicio del estudiante: lo que necesita ver al entrar, sin saturar. Una acción principal (la próxima sesión o buscar mentor),
// su semana en un vistazo, unos mentores para explorar y sus últimos avisos. Se mueve con suavidad: los bloques entran uno tras
// otro, los números suben, las barras crecen y la cuenta regresiva de la sesión se actualiza sola.
export default function InicioEstudiante({ primerNombre, irA }) {
  const { authFetch } = useAuth();
  const ahora = useAhora();
  const [sesiones, setSesiones] = useState(null);
  const [metas, setMetas] = useState(null);
  const [mentores, setMentores] = useState(null);
  const [avisos, setAvisos] = useState(null);
  const [charlas, setCharlas] = useState([]);
  const [evolucion, setEvolucion] = useState(null);

  // Cada bloque se carga por separado: si uno falla, los demás se siguen viendo.
  useEffect(() => {
    let activo = true;
    const cargar = (ruta, guardar) => pedir(authFetch, ruta)
      .then((d) => activo && guardar(d))
      .catch(() => activo && guardar(false));
    cargar('/api/sesiones/historial?estado=PROGRAMADA', setSesiones);
    cargar('/api/metas', setMetas);
    cargar('/api/mentores/ranking', setMentores);
    cargar('/api/notificaciones?limite=3', setAvisos);
    // Estos dos solo adornan: si fallan, simplemente no se muestran.
    pedir(authFetch, '/api/charlas/mias').then((d) => activo && setCharlas(d.filter((c) => c.vigente))).catch(() => {});
    pedir(authFetch, '/api/evolucion?agrupar=semana&periodos=8').then((d) => activo && setEvolucion(d)).catch(() => {});
    return () => { activo = false; };
  }, [authFetch]);

  const proximas = Array.isArray(sesiones)
    ? sesiones.map((s) => ({ ...s, inicio: aFecha(s.fecha, s.horaInicio), fin: aFecha(s.fecha, s.horaFin) }))
      .filter((s) => s.fin > ahora).sort((a, b) => a.inicio - b.inicio)
    : [];
  const siguiente = proximas[0];

  let subtitulo = 'Esto es lo que tienes hoy en TechLink.';
  if (sesiones !== null && sesiones !== false) {
    subtitulo = siguiente
      ? `Tu próxima sesión es ${diaLegible(siguiente.inicio, ahora).toLowerCase()} a las ${hora(siguiente.horaInicio)}.`
      : 'Elige un mentor y agenda tu primera sesión cuando quieras.';
  }

  // Se puede entrar a la reunión desde 15 minutos antes hasta que termina.
  const pronto = siguiente && ahora >= new Date(siguiente.inicio.getTime() - 15 * 60000);
  const puedeUnirse = pronto && siguiente.enlaceReunion;
  const horasTotales = evolucion?.resumen?.totalHoras ?? 0;

  const progreso = metas && metas.semana ? metas.semana.porcentaje : null;

  return (
    <div className="in">
      <header className="in-cab" style={{ '--i': 0 }}>
        <p className="in-eyebrow">Panel del estudiante</p>
        <h1>Hola, <span>{primerNombre}</span>.</h1>
        <p className="in-sub">{subtitulo}</p>
      </header>

      {/* Banner: una invitación a buscar mentor, con bloques que suben como una escalera */}
      <section className="in-hero" style={{ '--i': 1 }} aria-label="Bienvenida">
        <div className="in-hero-texto">
          <p className="in-eyebrow claro">TechLink · Mentorías</p>
          <p className="in-hero-lema">Aprende. <span>Conecta.</span> <em>Avanza.</em></p>
          <p>Encuentra el mentor ideal y lleva tu aprendizaje al siguiente nivel.</p>
          <button type="button" className="in-boton claro" onClick={() => irA('buscar')}>Buscar mentor <span aria-hidden="true" className="in-flecha-boton">→</span></button>
        </div>
        <Escalera />
      </section>

      <div className="in-fila-superior">
        <div className="in-tarjetas" style={{ '--i': 2 }}>
          <button type="button" className="in-dato" onClick={() => irA('sesiones')}>
            <span className="in-dato-icono"><IconoCalendario /></span>
            <span><strong>{sesiones ? <Numero valor={proximas.length} /> : '–'}</strong><small>Próximas sesiones</small></span>
          </button>
          <button type="button" className="in-dato" onClick={() => irA('progreso')}>
            <span className="in-dato-icono"><IconoBarras /></span>
            <span><strong>{evolucion ? <><Numero valor={Math.round(horasTotales)} /> h</> : '–'}</strong><small>Horas de asesoría</small></span>
          </button>
          <button type="button" className="in-dato" onClick={() => irA('progreso')}>
            <span className="in-dato-icono"><IconoEstrella /></span>
            <span><strong>{progreso !== null ? <><Numero valor={progreso} />%</> : '–'}</strong><small>Metas de la semana</small></span>
          </button>
        </div>

        {/* Próxima sesión */}
        <section className="in-sesion-tarjeta" style={{ '--i': 3 }} aria-label="Tu próxima sesión">
          <div className="in-bloque-cab">
            <h2>Próxima sesión</h2>
            {proximas.length > 1 && <button type="button" className="in-enlace" onClick={() => irA('sesiones')}>Ver todas →</button>}
          </div>
          {sesiones === null && <Esqueleto lineas={2} />}
          {sesiones === false && <p className="in-vacio">No pudimos cargar tus sesiones.</p>}
          {sesiones && !siguiente && (
            <div className="in-sesion-vacia">
              <p>Todavía no tienes sesiones. Elige un mentor y agenda la primera.</p>
              <button type="button" className="in-boton claro" onClick={() => irA('buscar')}>Buscar mentor</button>
            </div>
          )}
          {siguiente && (
            <div className="in-sesion-fila">
              <div className="in-sesion-info">
                <strong>{siguiente.curso || 'Mentoría'}</strong>
                <small>con {siguiente.contraparte}</small>
                <small className={`in-cuenta${pronto ? ' pronto' : ''}`}>{pronto && <i className="in-vivo" aria-hidden="true" />}{faltan(siguiente.inicio, siguiente.fin, ahora)} · {diaLegible(siguiente.inicio, ahora)} {hora(siguiente.horaInicio)}</small>
              </div>
              {puedeUnirse
                ? <a className="in-boton acento" href={siguiente.enlaceReunion} target="_blank" rel="noreferrer">Unirme</a>
                : <button type="button" className="in-boton acento" onClick={() => irA('sesiones')}>Ver sesión</button>}
            </div>
          )}
        </section>
      </div>

      {/* Mentores para explorar */}
      <section className="in-mentores" style={{ '--i': 4 }} aria-label="Mentores recomendados">
        <div className="in-bloque-cab">
          <h2>Mentores <span>recomendados</span></h2>
          <button type="button" className="in-enlace" onClick={() => irA('mentores')}>Ver más →</button>
        </div>
        {mentores === null && <Esqueleto lineas={3} />}
        {mentores === false && <p className="in-vacio">No pudimos cargar los mentores.</p>}
        {Array.isArray(mentores) && mentores.length === 0 && <p className="in-vacio">Todavía no hay mentores para mostrar.</p>}
        {Array.isArray(mentores) && mentores.length > 0 && (
          <ul className="in-mentor-cartas">
            {mentores.slice(0, 3).map((m, i) => (
              <li key={m.idMentor} style={{ '--i': i }}>
                <div className="in-mentor-carta">
                  <Avatar idUsuario={m.idUsuario} tieneFoto={m.tieneFoto} iniciales={iniciales(m)} tamano={64} insignia mostrarEstado />
                  <div className="in-mentor-datos">
                    <strong>{m.nombres} {m.apellidos !== '-' ? m.apellidos : ''}</strong>
                    <small>{m.especialidad || 'Mentor'}</small>
                    <EstrellasNota valor={m.promedio} total={m.totalCalificaciones} />
                    <EtiquetaDisponible idUsuario={m.idUsuario} />
                    <button type="button" className="in-ver-perfil" onClick={() => irA('mentores')}>Ver perfil →</button>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <div className="in-rejilla in-rejilla-pie">
        {/* La semana en un vistazo */}
        <section className="in-bloque" style={{ '--i': 5 }} aria-label="Tu semana">
          <h2>Tu semana</h2>
          {metas === null && <Esqueleto />}
          {metas === false && <p className="in-vacio">No pudimos cargar tus metas.</p>}
          {metas && (
            <>
              <SemanaTira ahora={ahora} sesiones={proximas} metas={metas.metas ?? []} charlas={charlas} irA={irA} />
              {metas.semana.total === 0 ? (
                <>
                  <p className="in-texto">Sin metas esta semana. Una pequeña meta ayuda a avanzar entre sesión y sesión.</p>
                  <button type="button" className="in-boton secundario" onClick={() => irA('progreso')}>Agregar una meta</button>
                </>
              ) : (
                <>
                  <p className="in-cifra"><Numero valor={metas.semana.completadas} /><span> de {metas.semana.total} {metas.semana.total === 1 ? 'meta' : 'metas'}</span></p>
                  <div className="in-barra" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={metas.semana.porcentaje} aria-label="Progreso de la semana">
                    <span style={{ width: `${metas.semana.porcentaje}%` }} />
                  </div>
                  {metas.atrasadas.length > 0 && (
                    <p className="in-texto">{metas.atrasadas.length} {metas.atrasadas.length === 1 ? 'meta atrasada' : 'metas atrasadas'} de semanas anteriores.</p>
                  )}
                  <button type="button" className="in-enlace" onClick={() => irA('progreso')}>Ver mis metas</button>
                </>
              )}
              {horasTotales > 0 && (
                <div className="in-horas">
                  <div className="in-horas-cab"><span>Horas de asesoría · 8 semanas</span><strong>{horasTexto(horasTotales)}</strong></div>
                  <Barritas periodos={evolucion.periodos} />
                </div>
              )}
            </>
          )}
        </section>

        {/* Últimos avisos */}
        <section className="in-bloque" style={{ '--i': 6 }} aria-label="Avisos recientes">
          <div className="in-bloque-cab">
            <h2>Avisos</h2>
            {avisos && avisos.notificaciones?.length > 0 && <button type="button" className="in-enlace" onClick={() => irA('notificaciones')}>Ver todos</button>}
          </div>
          {avisos === null && <Esqueleto lineas={3} />}
          {avisos === false && <p className="in-vacio">No pudimos cargar tus avisos.</p>}
          {avisos && (!avisos.notificaciones || avisos.notificaciones.length === 0) && <p className="in-vacio">No tienes avisos por ahora.</p>}
          {avisos && avisos.notificaciones?.length > 0 && (
            <ul className="in-lista">
              {avisos.notificaciones.map((n, i) => (
                <li key={n.id} className={`in-aviso${n.leida ? '' : ' nuevo'}`} style={{ '--i': i }}>
                  <span className="in-punto" aria-label={n.leida ? 'leído' : 'sin leer'} />
                  <span className="in-fila-texto">
                    <strong>{n.titulo}</strong>
                    <small>{hace(n.fecha, ahora)}</small>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  );
}
