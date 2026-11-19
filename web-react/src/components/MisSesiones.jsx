import { useCallback, useEffect, useRef, useState } from 'react';
import './MisSesiones.css';
import { useAuth } from '../auth/authContext';
import AgendarSesion from './AgendarSesion';
import Cargando from './Cargando';

const FILTROS = [
  { valor: '', texto: 'Todas' },
  { valor: 'PROGRAMADA', texto: 'Programadas' },
  { valor: 'COMPLETADA', texto: 'Completadas' },
  { valor: 'CANCELADA', texto: 'Canceladas' }
];
const ETIQUETA_ESTADO = { PROGRAMADA: 'Programada', COMPLETADA: 'Completada', CANCELADA: 'Cancelada' };
const MAX_NOTA = 2000;
const MAX_COMENTARIO = 500;

const hora = (t) => String(t).slice(0, 5);
const fechaLarga = (iso) => {
  const [a, m, d] = iso.split('-').map(Number);
  const texto = new Date(a, m - 1, d).toLocaleDateString('es-PE', { weekday: 'long', day: 'numeric', month: 'long' });
  return texto.charAt(0).toUpperCase() + texto.slice(1);
};

function Estrellas({ valor }) {
  return (
    <span className="ms-estrellas" role="img" aria-label={`${valor} de 5 estrellas`}>
      {[1, 2, 3, 4, 5].map((n) => <span key={n} className={n <= valor ? 'llena' : ''} aria-hidden="true">★</span>)}
    </span>
  );
}

// Ventana emergente dentro de la página (no usa los cuadros del navegador). Escape o tocar el fondo la cierra.
function Modal({ titulo, ancho = 'normal', onCerrar, children }) {
  const caja = useRef(null);

  useEffect(() => {
    const anterior = document.activeElement;
    caja.current?.focus();
    const escape = (e) => { if (e.key === 'Escape') onCerrar(); };
    document.addEventListener('keydown', escape);
    return () => {
      document.removeEventListener('keydown', escape);
      anterior?.focus?.();
    };
  }, [onCerrar]);

  return (
    <div className="ms-velo" onMouseDown={(e) => { if (e.target === e.currentTarget) onCerrar(); }}>
      <div className={`ms-modal ${ancho}`} role="dialog" aria-modal="true" aria-label={titulo} tabIndex={-1} ref={caja}>
        {children}
      </div>
    </div>
  );
}

// HU-20: historial de sesiones. Estudiante: calificar (HU-09), cancelar y reprogramar (HU-19), leer notas (HU-21).
// Mentor: escribir y editar las notas de cada sesión (HU-21).
export default function MisSesiones() {
  const { authFetch, usuario } = useAuth();
  const esMentor = usuario.rol === 'MENTOR';

  const [filtro, setFiltro] = useState('');
  const [sesiones, setSesiones] = useState(null);
  const [error, setError] = useState('');
  const [accion, setAccion] = useState(null); // { tipo: 'calificar' | 'cancelar' | 'reprogramar' | 'nota', sesion }
  const [aviso, setAviso] = useState('');

  // "version" sube cada vez que hay que volver a pedir el historial (por ejemplo, después de cancelar o calificar).
  const [version, setVersion] = useState(0);
  const cargar = useCallback(() => setVersion((v) => v + 1), []);

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const ruta = `/api/sesiones/historial${filtro ? `?estado=${filtro}` : ''}`;
        const response = await authFetch(ruta);
        const data = await response.json().catch(() => null);
        if (!response.ok) throw new Error(data?.error || '');
        if (!activo) return;
        setSesiones(data);
        setError('');
      } catch (e) {
        if (!activo) return;
        setError(e.message || 'No se pudo cargar el historial de sesiones.');
        setSesiones((actual) => actual ?? []);
      }
    })();
    return () => { activo = false; };
  }, [authFetch, filtro, version]);

  const cerrar = useCallback(() => setAccion(null), []);
  const terminar = (mensaje) => {
    setAccion(null);
    if (mensaje) setAviso(mensaje);
    cargar();
  };

  useEffect(() => {
    if (!aviso) return undefined;
    const t = setTimeout(() => setAviso(''), 5000);
    return () => clearTimeout(t);
  }, [aviso]);

  return (
    <section className="ms" aria-label="Mis sesiones">
      <div className="ms-filtros" role="tablist" aria-label="Filtrar por estado">
        {FILTROS.map((f) => (
          <button
            key={f.valor}
            type="button"
            role="tab"
            aria-selected={filtro === f.valor}
            className={`ms-filtro${filtro === f.valor ? ' activo' : ''}`}
            onClick={() => { setSesiones(null); setFiltro(f.valor); }}
          >
            {f.texto}
          </button>
        ))}
      </div>

      {aviso && <p className="ms-aviso" role="status">{aviso}</p>}
      {error && <p className="ms-error" role="alert">{error}</p>}

      {sesiones === null && <Cargando texto="Cargando tus sesiones…" />}
      {sesiones?.length === 0 && !error && (
        <p className="ms-vacio">
          {filtro
            ? 'No hay sesiones con ese estado.'
            : esMentor ? 'Todavía no tienes sesiones agendadas.' : 'Todavía no agendaste ninguna sesión. Busca un mentor para empezar.'}
        </p>
      )}

      <ul className="ms-lista">
        {sesiones?.map((s) => (
          <li key={s.id} className={`ms-tarjeta ${s.estado.toLowerCase()}`}>
            <div className="ms-fecha" aria-hidden="true">
              <span className="ms-dia">{Number(s.fecha.slice(8, 10))}</span>
              <span className="ms-mes">{new Date(Number(s.fecha.slice(0, 4)), Number(s.fecha.slice(5, 7)) - 1, 1)
                .toLocaleDateString('es-PE', { month: 'short' }).replace('.', '')}</span>
            </div>

            <div className="ms-cuerpo">
              <div className="ms-cab">
                <h3>{s.curso || 'Mentoría'}</h3>
                <span className={`ms-estado ${s.estado.toLowerCase()}`}>{ETIQUETA_ESTADO[s.estado]}</span>
              </div>
              <p className="ms-meta">{fechaLarga(s.fecha)} · {hora(s.horaInicio)} – {hora(s.horaFin)}</p>
              <p className="ms-meta">{esMentor ? 'Estudiante' : 'Mentor'}: <strong>{s.contraparte}</strong></p>

              {s.estrellas != null && (
                <p className="ms-calificacion">
                  <Estrellas valor={s.estrellas} />
                  {s.comentario && <span className="ms-comentario">“{s.comentario}”</span>}
                </p>
              )}

              {s.nota && (
                <details className="ms-nota">
                  <summary>{esMentor ? 'Tus notas de la sesión' : 'Notas del mentor'}</summary>
                  <p>{s.nota}</p>
                </details>
              )}

              <div className="ms-acciones">
                {s.estado === 'PROGRAMADA' && s.enlaceReunion && (
                  <a className="ms-boton primario" href={s.enlaceReunion} target="_blank" rel="noreferrer">Unirme a la reunión</a>
                )}
                {s.puedeCalificar && (
                  <button type="button" className="ms-boton primario" onClick={() => setAccion({ tipo: 'calificar', sesion: s })}>Calificar</button>
                )}
                {s.puedeModificar && (
                  <>
                    <button type="button" className="ms-boton" onClick={() => setAccion({ tipo: 'reprogramar', sesion: s })}>Reprogramar</button>
                    <button type="button" className="ms-boton peligro" onClick={() => setAccion({ tipo: 'cancelar', sesion: s })}>Cancelar</button>
                  </>
                )}
                {esMentor && s.puedeEditarNota && (
                  <button type="button" className="ms-boton" onClick={() => setAccion({ tipo: 'nota', sesion: s })}>
                    {s.nota ? 'Editar notas' : 'Agregar notas'}
                  </button>
                )}
              </div>
              {!esMentor && s.estado === 'PROGRAMADA' && !s.puedeModificar && (
                <p className="ms-pista">Faltan menos de 2 horas: ya no se puede cancelar ni reprogramar.</p>
              )}
            </div>
          </li>
        ))}
      </ul>

      {accion?.tipo === 'calificar' && (
        <Modal titulo="Calificar sesión" onCerrar={cerrar}>
          <FormCalificar sesion={accion.sesion} onCerrar={cerrar} onListo={() => terminar('¡Gracias por tu calificación!')} />
        </Modal>
      )}
      {accion?.tipo === 'cancelar' && (
        <Modal titulo="Cancelar sesión" onCerrar={cerrar}>
          <ConfirmarCancelacion sesion={accion.sesion} onCerrar={cerrar} onListo={() => terminar('La sesión se canceló y avisamos al mentor.')} />
        </Modal>
      )}
      {accion?.tipo === 'reprogramar' && (
        <Modal titulo="Reprogramar sesión" ancho="ancho" onCerrar={cerrar}>
          <AgendarSesion
            mentor={{ idMentor: accion.sesion.mentorId, nombres: accion.sesion.contraparte, apellidos: '' }}
            reprogramar={accion.sesion}
            onVolver={() => terminar()}
            onConfirmado={() => cargar()}
          />
        </Modal>
      )}
      {accion?.tipo === 'nota' && (
        <Modal titulo="Notas de la sesión" onCerrar={cerrar}>
          <FormNota sesion={accion.sesion} onCerrar={cerrar} onListo={() => terminar('Notas guardadas.')} />
        </Modal>
      )}
    </section>
  );
}

// HU-09: de 1 a 5 estrellas y un comentario opcional.
function FormCalificar({ sesion, onCerrar, onListo }) {
  const { authFetch } = useAuth();
  const [estrellas, setEstrellas] = useState(0);
  const [comentario, setComentario] = useState('');
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);

  async function enviar(e) {
    e.preventDefault();
    if (estrellas === 0) {
      setError('Elige de 1 a 5 estrellas.');
      return;
    }
    setEnviando(true);
    setError('');
    try {
      const response = await authFetch(`/api/sesiones/${sesion.id}/calificacion`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ estrellas, comentario: comentario.trim() || null })
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        setError(data.error || 'No se pudo guardar la calificación.');
        return;
      }
      onListo();
    } catch {
      setError('No se pudo conectar con el servidor.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form onSubmit={enviar}>
      <h2>Califica tu sesión</h2>
      <p className="ms-modal-sub">{sesion.curso || 'Mentoría'} con {sesion.contraparte} · {fechaLarga(sesion.fecha)}</p>

      <div className="ms-selector" role="radiogroup" aria-label="Estrellas">
        {[1, 2, 3, 4, 5].map((n) => (
          <button
            key={n}
            type="button"
            role="radio"
            aria-checked={estrellas === n}
            aria-label={`${n} ${n === 1 ? 'estrella' : 'estrellas'}`}
            className={n <= estrellas ? 'llena' : ''}
            onClick={() => { setEstrellas(n); setError(''); }}
          >★</button>
        ))}
      </div>

      <label htmlFor="ms-comentario">Comentario (opcional)</label>
      <textarea
        id="ms-comentario"
        rows={4}
        maxLength={MAX_COMENTARIO}
        value={comentario}
        placeholder="¿Cómo te ayudó el mentor?"
        onChange={(e) => setComentario(e.target.value)}
      />
      <p className="ms-contador">{comentario.length}/{MAX_COMENTARIO}</p>

      {error && <p className="ms-error" role="alert">{error}</p>}
      <div className="ms-modal-botones">
        <button type="button" className="ms-boton" onClick={onCerrar}>Ahora no</button>
        <button type="submit" className="ms-boton primario" disabled={enviando}>{enviando ? 'Enviando…' : 'Enviar calificación'}</button>
      </div>
    </form>
  );
}

// HU-19: confirmar la cancelación dentro de la página.
function ConfirmarCancelacion({ sesion, onCerrar, onListo }) {
  const { authFetch } = useAuth();
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);

  async function cancelar() {
    setEnviando(true);
    setError('');
    try {
      const response = await authFetch(`/api/sesiones/${sesion.id}/cancelar`, { method: 'PATCH' });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        setError(data.error || 'No se pudo cancelar la sesión.');
        return;
      }
      onListo();
    } catch {
      setError('No se pudo conectar con el servidor.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div>
      <h2>¿Cancelar esta sesión?</h2>
      <p className="ms-modal-sub">
        {fechaLarga(sesion.fecha)} · {hora(sesion.horaInicio)} – {hora(sesion.horaFin)} con {sesion.contraparte}.
        El horario del mentor quedará libre y le avisaremos por correo.
      </p>
      {error && <p className="ms-error" role="alert">{error}</p>}
      <div className="ms-modal-botones">
        <button type="button" className="ms-boton" onClick={onCerrar}>Mantener sesión</button>
        <button type="button" className="ms-boton peligro solido" onClick={cancelar} disabled={enviando}>
          {enviando ? 'Cancelando…' : 'Sí, cancelar'}
        </button>
      </div>
    </div>
  );
}

// HU-21: el mentor escribe los acuerdos y tareas pendientes; el estudiante los lee.
function FormNota({ sesion, onCerrar, onListo }) {
  const { authFetch } = useAuth();
  const [contenido, setContenido] = useState(sesion.nota || '');
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);

  async function guardar(e) {
    e.preventDefault();
    if (!contenido.trim()) {
      setError('Escribe la nota antes de guardarla.');
      return;
    }
    setEnviando(true);
    setError('');
    try {
      const response = await authFetch(`/api/sesiones/${sesion.id}/notas`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ contenido })
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        setError(data.error || 'No se pudo guardar la nota.');
        return;
      }
      onListo();
    } catch {
      setError('No se pudo conectar con el servidor.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form onSubmit={guardar}>
      <h2>Notas de la sesión</h2>
      <p className="ms-modal-sub">
        {sesion.curso || 'Mentoría'} con {sesion.contraparte} · {fechaLarga(sesion.fecha)}. El estudiante las verá en modo
        solo lectura. Puedes editarlas hasta 24 horas después de la sesión.
      </p>
      <label htmlFor="ms-nota">Acuerdos y tareas pendientes</label>
      <textarea
        id="ms-nota"
        rows={7}
        maxLength={MAX_NOTA}
        value={contenido}
        placeholder="Ej: Repasar JOIN. Tarea: ejercicios 3 y 4 para la próxima sesión."
        onChange={(e) => setContenido(e.target.value)}
      />
      <p className="ms-contador">{contenido.length}/{MAX_NOTA}</p>
      {error && <p className="ms-error" role="alert">{error}</p>}
      <div className="ms-modal-botones">
        <button type="button" className="ms-boton" onClick={onCerrar}>Cancelar</button>
        <button type="submit" className="ms-boton primario" disabled={enviando}>{enviando ? 'Guardando…' : 'Guardar notas'}</button>
      </div>
    </form>
  );
}
