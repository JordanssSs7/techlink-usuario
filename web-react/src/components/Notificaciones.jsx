import { useCallback, useEffect, useState } from 'react';
import './Comunidad.css';
import './Notificaciones.css';
import { useAuth } from '../auth/authContext';

const TIPOS = {
  SESION: 'Sesión',
  RECORDATORIO: 'Recordatorio',
  ANUNCIO: 'Anuncio',
  CHARLA: 'Charla',
  FORO: 'Foro',
  AMISTAD: 'Seguidores'
};

// Un dibujo por tipo de aviso, de una sola línea para que combinen con el resto de la aplicación.
const DIBUJOS = {
  SESION: <><circle cx="12" cy="12" r="9" /><path d="m8 12.5 3 3 5-6" /></>,
  RECORDATORIO: <><circle cx="12" cy="13" r="8" /><path d="M12 9v4l2.5 2M9 3h6" /></>,
  ANUNCIO: <><path d="M4 10v4h3l7 4V6L7 10H4Z" /><path d="M18 9a4 4 0 0 1 0 6" /></>,
  CHARLA: <><rect x="3" y="6" width="13" height="12" rx="3" /><path d="m16 11 5-3v8l-5-3" /></>,
  FORO: <><path d="M4 5h16v11H9l-5 4V5Z" /><path d="M8 9h8M8 12h5" /></>,
  AMISTAD: <><circle cx="9" cy="9" r="3.2" /><path d="M3 19c.6-3.2 3-5 6-5s5.4 1.8 6 5" /><path d="M16 6.2a3 3 0 0 1 0 5.6M18 14.4c1.7.6 2.7 2.1 3 4.6" /></>
};

function IconoTipo({ tipo, tam = 22 }) {
  return (
    <svg width={tam} height={tam} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {DIBUJOS[tipo] ?? DIBUJOS.ANUNCIO}
    </svg>
  );
}

function formatearFecha(texto) {
  if (!texto) return '';
  return new Date(texto).toLocaleString('es-PE', { dateStyle: 'medium', timeStyle: 'short' });
}

// "hace 5 min", "hace 2 h"… para lo reciente; la fecha completa para lo antiguo.
function relativo(texto, ahora) {
  if (!texto) return '';
  const min = Math.round((ahora - new Date(texto).getTime()) / 60000);
  if (Number.isNaN(min)) return '';
  if (min < 1) return 'ahora';
  if (min < 60) return `hace ${min} min`;
  const h = Math.round(min / 60);
  if (h < 24) return `hace ${h} h`;
  return formatearFecha(texto);
}

function grupoDe(texto, ahora) {
  const f = new Date(texto);
  const hoy = new Date(ahora);
  const dia = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const dif = Math.round((dia(hoy) - dia(f)) / 86400000);
  if (dif <= 0) return 'Hoy';
  if (dif === 1) return 'Ayer';
  return 'Anteriores';
}

// Los enlaces largos del texto (por ejemplo, el de la sala) se muestran como un botón corto en vez de la dirección completa.
function conEnlaces(texto) {
  return texto.split(/(https?:\/\/\S+)/g).map((parte, i) => {
    if (!/^https?:\/\//.test(parte)) return parte.replace(/\s*Enlace:\s*$/, '');
    const sala = /\/sala\//.test(parte);
    return (
      <a key={i} className="no-enlace" href={parte} target="_blank" rel="noopener noreferrer" onClick={(e) => e.stopPropagation()}>
        {sala ? 'Unirme a la sala' : 'Abrir enlace'}
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M7 17 17 7M9 7h8v8" /></svg>
      </a>
    );
  });
}

// HU-22: centro de notificaciones (confirmaciones, cambios, recordatorios y anuncios) con indicador de no leídas.
// HU-24: pestaña de preferencias para elegir qué avisos llegan a la aplicación y cuáles por correo.
// onCambio avisa al menú cuántas quedan sin leer.
export default function Notificaciones({ onCambio }) {
  const { authFetch } = useAuth();
  const [vista, setVista] = useState('todas'); // 'todas', 'sinleer' o 'preferencias'
  const [datos, setDatos] = useState(null);
  const [preferencias, setPreferencias] = useState(null);
  const [error, setError] = useState('');
  const [guardado, setGuardado] = useState('');
  const [ahora, setAhora] = useState(() => Date.now());
  const soloNoLeidas = vista === 'sinleer';

  useEffect(() => {
    const t = setInterval(() => setAhora(Date.now()), 60000);
    return () => clearInterval(t);
  }, []);

  const pedir = useCallback(async (url, opciones) => {
    const response = await authFetch(url, opciones);
    const data = await response.json().catch(() => null);
    if (!response.ok) throw new Error(data?.error || 'No se pudo completar la acción.');
    return data;
  }, [authFetch]);

  // Carga la lista cada vez que se cambia el filtro o llega un aviso nuevo en tiempo real.
  const [llegada, setLlegada] = useState(0);
  useEffect(() => {
    const alLlegar = () => setLlegada((n) => n + 1);
    window.addEventListener('techlink:notificacion', alLlegar);
    return () => window.removeEventListener('techlink:notificacion', alLlegar);
  }, []);
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
  }, [pedir, soloNoLeidas, onCambio, llegada]);

  // Las preferencias se piden la primera vez que se abre su pestaña.
  useEffect(() => {
    if (vista !== 'preferencias' || preferencias) return undefined;
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
  }, [pedir, vista, preferencias]);

  // Abre lo que el aviso señala (por ejemplo, la consulta del foro) y lo marca como leído.
  function abrir(notificacion) {
    const m = /^foro:(\d+)$/.exec(notificacion.enlace || '');
    if (m) window.dispatchEvent(new CustomEvent('techlink:ir', { detail: { seccion: 'foro', consultaId: Number(m[1]) } }));
    const sec = /^seccion:(\w+)$/.exec(notificacion.enlace || '');
    if (sec) window.dispatchEvent(new CustomEvent('techlink:ir', { detail: { seccion: sec[1] } }));
    const p = /^usuario:(\d+)$/.exec(notificacion.enlace || '');
    if (p) window.dispatchEvent(new CustomEvent('techlink:perfil', { detail: { idUsuario: Number(p[1]) } }));
    return leer(notificacion);
  }

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

  async function cambiarPreferencia(tipoPref, campo, valor) {
    const anteriores = preferencias;
    setPreferencias(preferencias.map((p) => (p.tipo === tipoPref ? { ...p, [campo]: valor } : p)));
    setGuardado('');
    try {
      const data = await pedir('/api/notificaciones/preferencias', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ preferencias: [{ tipo: tipoPref, [campo]: valor }] })
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
  // Los avisos se agrupan por día para que se lea como una línea de tiempo.
  const grupos = [];
  lista.forEach((n) => {
    const nombre = grupoDe(n.fecha, ahora);
    const ultimo = grupos[grupos.length - 1];
    if (ultimo && ultimo.nombre === nombre) ultimo.items.push(n);
    else grupos.push({ nombre, items: [n] });
  });
  let posicion = 0;
  const indice = { todas: 0, sinleer: 1, preferencias: 2 }[vista];
  const sinLeer = datos?.noLeidas ?? 0;

  return (
    <div className="co no-pagina">
      <div className="no-herramientas">
        <div className="no-seg" role="tablist" aria-label="Notificaciones" style={{ '--i': indice }}>
          <button type="button" role="tab" aria-selected={vista === 'todas'} className={vista === 'todas' ? 'activa' : ''} onClick={() => setVista('todas')}>Todas</button>
          <button type="button" role="tab" aria-selected={vista === 'sinleer'} className={vista === 'sinleer' ? 'activa' : ''} onClick={() => setVista('sinleer')}>
            Sin leer{sinLeer > 0 && <span className="no-insignia">{sinLeer}</span>}
          </button>
          <button type="button" role="tab" aria-selected={vista === 'preferencias'} className={vista === 'preferencias' ? 'activa' : ''} onClick={() => setVista('preferencias')}>Preferencias</button>
        </div>
        {vista !== 'preferencias' && (
          <button type="button" className="no-leer-todas" onClick={leerTodas} disabled={!datos || sinLeer === 0}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m3 12 5 5L20 6" /><path d="m11 16 1 1L22 7" opacity="0.55" /></svg>
            Marcar todas como leídas
          </button>
        )}
      </div>

      {error && <p className="co-error" role="alert">{error}</p>}

      {vista !== 'preferencias' && (
        <div className="no-contenido" key={vista}>
          {!datos && !error && <p className="co-vacio">Cargando…</p>}
          {datos && lista.length === 0 && (
            <div className="no-vacio">
              <svg width="120" height="96" viewBox="0 0 120 96" fill="none" aria-hidden="true">
                <ellipse cx="60" cy="52" rx="52" ry="36" fill="#eeeeea" />
                <path d="M40 46a20 20 0 0 1 40 0c0 18 8 22 8 22H32s8-4 8-22Z" stroke="#0b0b0c" strokeWidth="2.6" strokeLinejoin="round" fill="#fff" />
                <path d="M52 74a8 8 0 0 0 16 0" stroke="#0b0b0c" strokeWidth="2.6" strokeLinecap="round" />
                <path d="m53 46 5 5 10-11" stroke="#0b0b0c" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" />
                <path className="no-chispa" d="M96 20v10M91 25h10" stroke="#0b0b0c" strokeWidth="2" strokeLinecap="round" />
              </svg>
              <strong>{soloNoLeidas ? 'Estás al día' : 'Todavía no tienes notificaciones'}</strong>
              <p>{soloNoLeidas ? 'No tienes avisos sin leer.' : 'Aquí verás tus confirmaciones, recordatorios y anuncios.'}</p>
            </div>
          )}

          {grupos.map((g) => (
            <section key={g.nombre} className="no-grupo" aria-label={g.nombre}>
              <h3 className="no-grupo-titulo">{g.nombre}</h3>
              <ul className="no-lista">
                {g.items.map((n) => {
                  const i = posicion;
                  posicion += 1;
                  return (
                    <li key={n.id} className="no-entra" style={{ '--i': Math.min(i, 10) }}>
                      <button type="button" className={`no-item${n.leida ? '' : ' sin-leer'}`} onClick={() => abrir(n)}
                        aria-label={`${n.leida ? '' : 'Sin leer. '}${n.titulo}`}>
                        <span className="no-icono" aria-hidden="true"><IconoTipo tipo={n.tipo} /></span>
                        <span className="no-cuerpo">
                          <span className="no-tipo">{TIPOS[n.tipo] || n.tipo}</span>
                          <strong>{n.titulo}</strong>
                          <span className="no-mensaje">{conEnlaces(n.mensaje)}</span>
                        </span>
                        <span className="no-fecha" title={formatearFecha(n.fecha)}>{relativo(n.fecha, ahora)}</span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            </section>
          ))}
        </div>
      )}

      {vista === 'preferencias' && (
        <section className="co-tarjeta no-tarjeta no-entra">
          <h2 className="co-titulo-tarjeta">¿Cómo quieres recibir tus avisos?</h2>
          <p className="co-sub">Elige qué avisos quieres recibir: apaga los que no te interesen.</p>
          {!preferencias && !error && <p className="co-vacio">Cargando…</p>}
          <ul className="no-prefs">
            {(preferencias ?? []).map((p, i) => (
              <li key={p.tipo} className={`no-pref no-entra${p.correoDisponible ? '' : ' solo-app'}`} style={{ '--i': i }}>
                <div className="no-pref-texto">
                  <span className="no-icono chico" aria-hidden="true"><IconoTipo tipo={p.tipo} tam={18} /></span>
                  <div>
                    <strong>{p.nombre}</strong>
                    <p className="co-sub">{p.descripcion}</p>
                  </div>
                </div>
                <label className="no-interruptor">
                  <input type="checkbox" checked={p.enApp} onChange={(e) => cambiarPreferencia(p.tipo, 'enApp', e.target.checked)} />
                  <span className="no-palanca" aria-hidden="true" />
                  En la aplicación
                </label>
                {p.correoDisponible && (
                  <label className="no-interruptor">
                    <input type="checkbox" checked={p.correo} onChange={(e) => cambiarPreferencia(p.tipo, 'correo', e.target.checked)} />
                    <span className="no-palanca" aria-hidden="true" />
                    Por correo
                  </label>
                )}
              </li>
            ))}
          </ul>
          {guardado && <p className="no-guardado" role="status">{guardado}</p>}
        </section>
      )}
    </div>
  );
}
