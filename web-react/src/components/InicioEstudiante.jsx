import { useEffect, useState } from 'react';
import './Inicio.css';
import { useAuth } from '../auth/authContext';

const trazo = { fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round', strokeLinejoin: 'round' };

const ACCESOS = [
  { id: 'sesiones', titulo: 'Mis sesiones', texto: 'Agenda, reprograma y entra a tu sala', icono: <><rect x="3.5" y="5" width="17" height="15" rx="2" /><path d="M8 3v4M16 3v4M3.5 10h17" /></> },
  { id: 'progreso', titulo: 'Mi progreso', texto: 'Metas y avance en tus cursos', icono: <><path d="M5 20V10M12 20V4M19 20v-7" /></> },
  { id: 'charlas', titulo: 'Charlas', texto: 'Aprende en vivo con otros estudiantes', icono: <><rect x="3" y="5" width="13" height="14" rx="2" /><path d="m16 10 5-3v10l-5-3" /></> },
  { id: 'foro', titulo: 'Foro', texto: 'Pregunta y ayuda por curso', icono: <><path d="M4 5h16v10H9l-5 4z" /><path d="M8 9h8M8 12h5" /></> }
];

const hora = (t) => String(t).slice(0, 5);
function fechaLarga(iso) {
  const [a, m, d] = iso.split('-').map(Number);
  const texto = new Date(a, m - 1, d).toLocaleDateString('es-PE', { weekday: 'long', day: 'numeric', month: 'long' });
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}
function saludo() {
  const h = new Date().getHours();
  return h < 12 ? 'Buenos días' : h < 19 ? 'Buenas tardes' : 'Buenas noches';
}

// Inicio del estudiante: saludo, la búsqueda de mentor, su próxima sesión, sus charlas y accesos rápidos a lo que más usa.
export default function InicioEstudiante({ nombre, pendientes, irA }) {
  const { authFetch } = useAuth();
  const [datos, setDatos] = useState(null);

  useEffect(() => {
    let activo = true;
    (async () => {
      let sesiones = [];
      let charlas = [];
      try {
        const [rs, rc] = await Promise.all([authFetch('/api/sesiones/historial?estado=PROGRAMADA'), authFetch('/api/charlas/mias')]);
        sesiones = rs.ok ? await rs.json() : [];
        charlas = rc.ok ? await rc.json() : [];
      } catch {
        // Sin conexión: el inicio se muestra igual, con las tarjetas vacías.
      }
      if (!activo) return;
      const proximas = [...sesiones].sort((a, b) => `${a.fecha}${a.horaInicio}`.localeCompare(`${b.fecha}${b.horaInicio}`));
      setDatos({ proxima: proximas[0] ?? null, total: proximas.length, charlas: charlas.filter((c) => c.vigente).slice(0, 2) });
    })();
    return () => { activo = false; };
  }, [authFetch]);

  return (
    <section className="pt-seccion ini">
      <p className="pt-eyebrow">Panel del estudiante</p>
      <h1>{saludo()}, {nombre}.</h1>
      <p className="pt-intro">Esto es lo que tienes hoy en TechLink, la plataforma de mentorías de Tecsup.</p>

      <div className="pt-tarjeta ini-hero">
        <div>
          <h2>Encuentra a tu mentor ideal</h2>
          <p>Dinos en qué cursos necesitas ayuda y cuándo tienes tiempo libre. Nuestra IA te propone los 3 mentores que mejor coinciden.</p>
        </div>
        <button type="button" className="bm-primario claro" onClick={() => irA('buscar')}>
          Buscar mentor <span aria-hidden="true">→</span>
        </button>
      </div>

      <div className="ini-grid">
        <article className="ini-card ini-proxima">
          <h2>Tu próxima sesión</h2>
          {!datos && <p className="ini-vacio">Cargando…</p>}
          {datos && !datos.proxima && (
            <>
              <p className="ini-vacio">Todavía no tienes sesiones programadas.</p>
              <button type="button" className="ini-enlace" onClick={() => irA('buscar')}>Agenda tu primera sesión →</button>
            </>
          )}
          {datos?.proxima && (
            <>
              <p className="ini-destacado">{fechaLarga(datos.proxima.fecha)}</p>
              <p className="ini-detalle">{hora(datos.proxima.horaInicio)} – {hora(datos.proxima.horaFin)} · {datos.proxima.curso || 'Mentoría'}</p>
              <p className="ini-detalle">Con <strong>{datos.proxima.contraparte}</strong></p>
              <button type="button" className="ini-enlace" onClick={() => irA('sesiones')}>
                Entrar a la sala o ver mis {datos.total} {datos.total === 1 ? 'sesión' : 'sesiones'} →
              </button>
            </>
          )}
        </article>

        <article className="ini-card">
          <h2>Notificaciones</h2>
          <p className="ini-numero">{pendientes}</p>
          <p className="ini-detalle">{pendientes === 0 ? 'Estás al día.' : pendientes === 1 ? 'aviso sin leer' : 'avisos sin leer'}</p>
          <button type="button" className="ini-enlace" onClick={() => irA('notificaciones')}>Ver notificaciones →</button>
        </article>

        <article className="ini-card">
          <h2>Tus charlas</h2>
          {!datos && <p className="ini-vacio">Cargando…</p>}
          {datos && datos.charlas.length === 0 && <p className="ini-vacio">No estás inscrito en charlas próximas.</p>}
          {datos?.charlas.map((c) => (
            <p key={c.id} className="ini-charla"><strong>{c.nombre}</strong><span>{fechaLarga(c.fecha)} · {hora(c.horaInicio)}</span></p>
          ))}
          <button type="button" className="ini-enlace" onClick={() => irA('charlas')}>Ver charlas →</button>
        </article>
      </div>

      <h2 className="ini-titulo">Accesos rápidos</h2>
      <div className="ini-accesos">
        {ACCESOS.map((a) => (
          <button key={a.id} type="button" className="ini-acceso" onClick={() => irA(a.id)}>
            <span className="ini-icono"><svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true" {...trazo}>{a.icono}</svg></span>
            <strong>{a.titulo}</strong>
            <span>{a.texto}</span>
          </button>
        ))}
      </div>
    </section>
  );
}
