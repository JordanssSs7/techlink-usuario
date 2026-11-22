import { useEffect, useState } from 'react';
import './Inicio.css';
import { useAuth } from '../auth/authContext';

const trazo = { fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round', strokeLinejoin: 'round' };

const ACCESOS = [
  { id: 'perfil', titulo: 'Mi perfil', texto: 'Cursos, fortalezas y horarios para que te encuentren', icono: <><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /></> },
  { id: 'calendario', titulo: 'Calendario', texto: 'Tus sesiones por semana o por día', icono: <><rect x="3.5" y="5" width="17" height="15" rx="2" /><path d="M8 3v4M16 3v4M3.5 10h17" /></> },
  { id: 'metas', titulo: 'Metas', texto: 'Acuerda metas con tus estudiantes', icono: <><circle cx="12" cy="12" r="8" /><circle cx="12" cy="12" r="4" /><circle cx="12" cy="12" r="0.8" /></> },
  { id: 'foro', titulo: 'Foro', texto: 'Responde dudas de los estudiantes', icono: <><path d="M4 5h16v10H9l-5 4z" /><path d="M8 9h8M8 12h5" /></> }
];

const hora = (t) => String(t).slice(0, 5);
const horas = (h) => `${Number.isInteger(h) ? h : String(Number(h.toFixed(2))).replace('.', ',')} h`;
function fechaLarga(iso) {
  const [a, m, d] = iso.split('-').map(Number);
  const texto = new Date(a, m - 1, d).toLocaleDateString('es-PE', { weekday: 'long', day: 'numeric', month: 'long' });
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}
function saludo() {
  const h = new Date().getHours();
  return h < 12 ? 'Buenos días' : h < 19 ? 'Buenas tardes' : 'Buenas noches';
}

// Inicio del mentor: saludo, su próxima sesión, sus números (horas y calificación), sus avisos y accesos rápidos.
export default function InicioMentor({ nombre, pendientes, irA }) {
  const { authFetch } = useAuth();
  const [datos, setDatos] = useState(null);

  useEffect(() => {
    let activo = true;
    (async () => {
      let sesiones = [];
      let resumen = null;
      try {
        const [rs, re] = await Promise.all([authFetch('/api/sesiones/historial?estado=PROGRAMADA'), authFetch('/api/mentor/estadisticas?meses=1')]);
        sesiones = rs.ok ? await rs.json() : [];
        resumen = re.ok ? (await re.json()).resumen : null;
      } catch {
        // Sin conexión: el inicio se muestra igual, con las tarjetas vacías.
      }
      if (!activo) return;
      const proximas = [...sesiones].sort((a, b) => `${a.fecha}${a.horaInicio}`.localeCompare(`${b.fecha}${b.horaInicio}`));
      setDatos({ proxima: proximas[0] ?? null, total: proximas.length, resumen });
    })();
    return () => { activo = false; };
  }, [authFetch]);

  const resumen = datos?.resumen;
  return (
    <section className="pt-seccion ini">
      <p className="pt-eyebrow">Panel del mentor</p>
      <h1>{saludo()}, {nombre}.</h1>
      <p className="pt-intro">Esto es lo que tienes hoy en TechLink: tus sesiones, tus estudiantes y tu avance como mentor.</p>

      <div className="pt-tarjeta ini-hero">
        <div>
          <h2>Tus estudiantes te esperan</h2>
          <p>Revisa tus próximas sesiones, entra a la sala virtual cuando llegue la hora y deja notas con los acuerdos para la siguiente.</p>
        </div>
        <button type="button" className="bm-primario claro" onClick={() => irA('sesiones')}>
          Ver mis sesiones <span aria-hidden="true">→</span>
        </button>
      </div>

      <div className="ini-grid">
        <article className="ini-card ini-proxima">
          <h2>Tu próxima sesión</h2>
          {!datos && <p className="ini-vacio">Cargando…</p>}
          {datos && !datos.proxima && (
            <>
              <p className="ini-vacio">Todavía no tienes sesiones programadas. Completa tu perfil y tus horarios para que los estudiantes puedan agendar contigo.</p>
              <button type="button" className="ini-enlace" onClick={() => irA('perfil')}>Ir a mi perfil →</button>
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
          <h2>Tu desempeño</h2>
          {!datos && <p className="ini-vacio">Cargando…</p>}
          {datos && !resumen && <p className="ini-vacio">Aún no hay datos.</p>}
          {resumen && (
            <>
              <p className="ini-numero">{horas(resumen.horasTotales)}</p>
              <p className="ini-detalle">
                {resumen.sesionesCompletadas} {resumen.sesionesCompletadas === 1 ? 'sesión completada' : 'sesiones completadas'}
                {resumen.promedio != null ? ` · ${String(resumen.promedio).replace('.', ',')} ★` : ''}
              </p>
            </>
          )}
          <button type="button" className="ini-enlace" onClick={() => irA('estadisticas')}>Ver estadísticas →</button>
        </article>

        <article className="ini-card">
          <h2>Notificaciones</h2>
          <p className="ini-numero">{pendientes}</p>
          <p className="ini-detalle">{pendientes === 0 ? 'Estás al día.' : pendientes === 1 ? 'aviso sin leer' : 'avisos sin leer'}</p>
          <button type="button" className="ini-enlace" onClick={() => irA('notificaciones')}>Ver notificaciones →</button>
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
