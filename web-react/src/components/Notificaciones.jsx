import { useCallback, useEffect, useState } from 'react';
import './Comunidad.css';
import './Notificaciones.css';
import { useAuth } from '../auth/authContext';

const TIPOS = {
  SESION: 'Sesión',
  RECORDATORIO: 'Recordatorio',
  ANUNCIO: 'Anuncio',
  CHARLA: 'Charla'
};

function formatearFecha(texto) {
  if (!texto) return '';
  return new Date(texto).toLocaleString('es-PE', { dateStyle: 'medium', timeStyle: 'short' });
}

// Convierte los enlaces del texto (por ejemplo, el de la reunión) en enlaces en los que se puede hacer clic.
function conEnlaces(texto) {
  return texto.split(/(https?:\/\/\S+)/g).map((parte, i) =>
    /^https?:\/\//.test(parte)
      ? <a key={i} href={parte} target="_blank" rel="noopener noreferrer" onClick={(e) => e.stopPropagation()}>{parte}</a>
      : parte
  );
}

// HU-22: centro de notificaciones (confirmaciones, cambios, recordatorios y anuncios) con indicador de no leídas.
// HU-24: pestaña de preferencias para elegir qué avisos llegan a la aplicación y cuáles por correo.
// onCambio avisa al menú cuántas quedan sin leer.
export default function Notificaciones({ onCambio }) {
  const { authFetch } = useAuth();
  const [pestana, setPestana] = useState('avisos');
  const [soloNoLeidas, setSoloNoLeidas] = useState(false);
  const [datos, setDatos] = useState(null);
  const [preferencias, setPreferencias] = useState(null);
  const [error, setError] = useState('');
  const [guardado, setGuardado] = useState('');

  const pedir = useCallback(async (url, opciones) => {
    const response = await authFetch(url, opciones);
    const data = await response.json().catch(() => null);
    if (!response.ok) throw new Error(data?.error || 'No se pudo completar la acción.');
    return data;
  }, [authFetch]);

  // Carga la lista cada vez que se cambia el filtro.
  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const data = await pedir(`/api/notificaciones?soloNoLeidas=${soloNoLeidas}&limite=100`);
        if (!activo) return;
        setDatos(data);
        setError('');
        onCambio?.(data.noLeidas);
      } catch (e) {
        if (activo) setError(e.message);
      }
    })();
    return () => { activo = false; };
  }, [pedir, soloNoLeidas, onCambio]);

  // Las preferencias se piden la primera vez que se abre su pestaña.
  useEffect(() => {
    if (pestana !== 'preferencias' || preferencias) return undefined;
    let activo = true;
    (async () => {
      try {
        const data = await pedir('/api/notificaciones/preferencias');
        if (!activo) return;
        setPreferencias(data);
        setError('');
      } catch (e) {
        if (activo) setError(e.message);
      }
    })();
    return () => { activo = false; };
  }, [pedir, pestana, preferencias]);

  async function leer(notificacion) {
    if (notificacion.leida) return;
    try {
      await pedir(`/api/notificaciones/${notificacion.id}/leida`, { method: 'POST' });
      const restantes = datos.noLeidas - 1;
      setDatos({
        noLeidas: restantes,
        notificaciones: soloNoLeidas
          ? datos.notificaciones.filter((n) => n.id !== notificacion.id)
          : datos.notificaciones.map((n) => (n.id === notificacion.id ? { ...n, leida: true } : n))
      });
      onCambio?.(restantes);
    } catch (e) {
      setError(e.message);
    }
  }

  async function leerTodas() {
    try {
      await pedir('/api/notificaciones/leer-todas', { method: 'POST' });
      setDatos({
        noLeidas: 0,
        notificaciones: soloNoLeidas ? [] : datos.notificaciones.map((n) => ({ ...n, leida: true }))
      });
      onCambio?.(0);
    } catch (e) {
      setError(e.message);
    }
  }

  async function cambiarPreferencia(tipo, campo, valor) {
    const anteriores = preferencias;
    setPreferencias(preferencias.map((p) => (p.tipo === tipo ? { ...p, [campo]: valor } : p)));
    setGuardado('');
    try {
      const data = await pedir('/api/notificaciones/preferencias', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ preferencias: [{ tipo, [campo]: valor }] })
      });
      setPreferencias(data);
      setError('');
      setGuardado('Preferencias guardadas.');
    } catch (e) {
      setPreferencias(anteriores);
      setError(e.message);
    }
  }

  const lista = datos?.notificaciones ?? [];

  return (
    <div className="co">
      <div className="no-pestanas" role="tablist" aria-label="Notificaciones">
        <button type="button" role="tab" aria-selected={pestana === 'avisos'} className={pestana === 'avisos' ? 'activa' : ''}
          onClick={() => setPestana('avisos')}>
          Avisos{datos?.noLeidas > 0 && <span className="no-insignia">{datos.noLeidas}</span>}
        </button>
        <button type="button" role="tab" aria-selected={pestana === 'preferencias'} className={pestana === 'preferencias' ? 'activa' : ''}
          onClick={() => setPestana('preferencias')}>
          Preferencias
        </button>
      </div>

      {error && <p className="co-error" role="alert">{error}</p>}

      {pestana === 'avisos' && (
        <>
          <div className="no-barra">
            <div className="no-filtros">
              <button type="button" className={!soloNoLeidas ? 'activa' : ''} onClick={() => setSoloNoLeidas(false)}>Todas</button>
              <button type="button" className={soloNoLeidas ? 'activa' : ''} onClick={() => setSoloNoLeidas(true)}>Sin leer</button>
            </div>
            <button type="button" className="co-boton" onClick={leerTodas} disabled={!datos || datos.noLeidas === 0}>
              Marcar todas como leídas
            </button>
          </div>

          {!datos && !error && <p className="co-vacio">Cargando…</p>}
          {datos && lista.length === 0 && (
            <p className="co-vacio">{soloNoLeidas ? 'No tienes avisos sin leer.' : 'Todavía no tienes notificaciones. Aquí verás tus confirmaciones, recordatorios y anuncios.'}</p>
          )}

          <ul className="no-lista">
            {lista.map((n) => (
              <li key={n.id}>
                <button type="button" className={`no-item${n.leida ? '' : ' sin-leer'}`} onClick={() => leer(n)}
                  aria-label={`${n.leida ? '' : 'Sin leer. '}${n.titulo}`}>
                  <span className="no-punto" aria-hidden="true" />
                  <span className="no-cuerpo">
                    <span className="no-fila">
                      <strong>{n.titulo}</strong>
                      <span className="no-tipo">{TIPOS[n.tipo] || n.tipo}</span>
                    </span>
                    <span className="no-mensaje">{conEnlaces(n.mensaje)}</span>
                    <span className="no-fecha">{formatearFecha(n.fecha)}</span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </>
      )}

      {pestana === 'preferencias' && (
        <section className="co-tarjeta">
          <h2 className="co-titulo-tarjeta">¿Cómo quieres recibir tus avisos?</h2>
          <p className="co-sub">Elige, para cada tipo, si lo ves en la aplicación y si te llega un correo.</p>
          {!preferencias && !error && <p className="co-vacio">Cargando…</p>}
          <ul className="no-prefs">
            {(preferencias ?? []).map((p) => (
              <li key={p.tipo} className="no-pref">
                <div>
                  <strong>{p.nombre}</strong>
                  <p className="co-sub">{p.descripcion}</p>
                </div>
                <label className="no-interruptor">
                  <input type="checkbox" checked={p.enApp} onChange={(e) => cambiarPreferencia(p.tipo, 'enApp', e.target.checked)} />
                  En la aplicación
                </label>
                <label className="no-interruptor">
                  <input type="checkbox" checked={p.correo} disabled={!p.correoDisponible}
                    onChange={(e) => cambiarPreferencia(p.tipo, 'correo', e.target.checked)} />
                  {p.correoDisponible ? 'Por correo' : 'Por correo (no disponible)'}
                </label>
              </li>
            ))}
          </ul>
          {guardado && <p className="co-sub" role="status">{guardado}</p>}
        </section>
      )}
    </div>
  );
}
