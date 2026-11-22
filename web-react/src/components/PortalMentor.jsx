import { useEffect, useState } from 'react';
import './PortalEstudiante.css';
import './PortalMentor.css';
import { useAuth } from '../auth/authContext';
import PerfilMentor from './PerfilMentor';
import MisSesiones from './MisSesiones';
import MiCuenta from './MiCuenta';
import Foro from './Foro';
import Calendario from './Calendario';
import MetasMentor from './MetasMentor';
import CentroAyuda from './CentroAyuda';
import Notificaciones from './Notificaciones';
import EstadisticasMentor from './EstadisticasMentor';
import InicioMentor from './InicioMentor';
import Avatar from './Avatar';
import { useFotoPropia } from './fotoPerfil';
import useNotificacionesPendientes from './useNotificaciones';

const trazo = { fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round', strokeLinejoin: 'round' };

// Mismas secciones de siempre, ahora en una barra lateral con íconos (igual que el portal del estudiante).
const SECCIONES = [
  { id: 'inicio', titulo: 'Inicio', icono: <><path d="M3 10.5 12 3l9 7.5" /><path d="M5 9.5V20h5v-6h4v6h5V9.5" /></> },
  { id: 'sesiones', titulo: 'Mis sesiones', icono: <><rect x="3.5" y="5" width="17" height="15" rx="2" /><path d="M8 3v4M16 3v4M3.5 10h17" /></> },
  { id: 'calendario', titulo: 'Calendario', icono: <><rect x="3.5" y="5" width="17" height="15" rx="2" /><path d="M8 3v4M16 3v4M3.5 10h17M8 14h2M12 14h2M16 14h.01M8 17h2" /></> },
  { id: 'metas', titulo: 'Metas', icono: <><circle cx="12" cy="12" r="8" /><circle cx="12" cy="12" r="4" /><circle cx="12" cy="12" r="0.8" /></> },
  { id: 'estadisticas', titulo: 'Estadísticas', icono: <><path d="M5 20V10M12 20V4M19 20v-7" /></> },
  { id: 'notificaciones', titulo: 'Notificaciones', icono: <><path d="M6 9a6 6 0 0 1 12 0c0 6 2 7.5 2 7.5H4S6 15 6 9" /><path d="M10 20a2 2 0 0 0 4 0" /></> },
  { id: 'foro', titulo: 'Foro', icono: <><path d="M4 5h16v10H9l-5 4z" /><path d="M8 9h8M8 12h5" /></> },
  { id: 'perfil', titulo: 'Mi perfil', icono: <><path d="m12 3 2.6 5.6 6.1.7-4.5 4.2 1.2 6L12 16.5 6.6 19.5l1.2-6L3.3 9.3l6.1-.7z" /></> },
  { id: 'cuenta', titulo: 'Mis datos', icono: <><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /></> },
  { id: 'ayuda', titulo: 'Ayuda', icono: <><circle cx="12" cy="12" r="9" /><path d="M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.7.4-1 .9-1 1.7M12 17h.01" /></> }
];

// Título, texto de apoyo y encabezado de cada sección (las que no son el inicio).
const ENCABEZADOS = {
  sesiones: ['Mentorías', 'Mis sesiones', 'Revisa tus sesiones con los estudiantes, entra a la sala virtual y deja notas con los acuerdos y tareas pendientes.'],
  calendario: ['Tu agenda', 'Calendario', 'Tus sesiones con los estudiantes, por semana o por día. Elige una para ver su detalle.'],
  metas: ['Seguimiento', 'Metas de mis estudiantes', 'Acuerda metas semanales con tus estudiantes y mira cuáles van cumpliendo entre una sesión y otra.'],
  estadisticas: ['Tu desempeño', 'Estadísticas', 'Tus sesiones, horas y calificación mes a mes, y tu constancia de mentoría en PDF.'],
  notificaciones: ['Tus avisos', 'Notificaciones', 'Confirmaciones, cambios de tus sesiones, recordatorios y anuncios. En Preferencias eliges qué recibir.'],
  foro: ['Comunidad', 'Foro de consultas', 'Responde las dudas de los estudiantes por curso: tu experiencia es la que más ayuda.'],
  perfil: ['Tu perfil de mentor', 'Mi perfil', 'Lo que registres aquí se usa para recomendarte a los estudiantes y para que agenden contigo.'],
  cuenta: ['Tu cuenta', 'Mis datos', 'Tu foto y tu descripción se ven en tu perfil público de mentor.'],
  ayuda: ['Soporte', 'Centro de ayuda', 'Respuestas a las preguntas más frecuentes sobre TechLink.']
};

function Logo({ size = 26 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true">
      <path d="M3 3h8l-3 9H3z" fill="currentColor" />
      <path d="M13 3h8v9h-5l-3 9H9z" fill="currentColor" />
    </svg>
  );
}

// Sitio del mentor: barra lateral con sus secciones (igual que el estudiante), un inicio con lo del día, y cada sección con su
// encabezado. En pantallas pequeñas la barra lateral se vuelve un menú que se abre con el botón ☰.
export default function PortalMentor() {
  const { usuario, logout } = useAuth();
  const fotoPropia = useFotoPropia();
  const [seccion, setSeccion] = useState('inicio');
  const [menuAbierto, setMenuAbierto] = useState(false);
  const { pendientes, poner: ponerPendientes } = useNotificacionesPendientes();

  const primerNombre = (usuario.nombres || '').split(' ')[0];
  const apellidos = usuario.apellidos && usuario.apellidos !== '-' ? usuario.apellidos : '';
  const nombreCompleto = [usuario.nombres, apellidos].filter(Boolean).join(' ');
  const iniciales = [usuario.nombres, apellidos].filter(Boolean).map((p) => p.trim()[0]).join('').slice(0, 2).toUpperCase();

  // Escape cierra el menú en celular.
  useEffect(() => {
    if (!menuAbierto) return undefined;
    const escape = (e) => { if (e.key === 'Escape') setMenuAbierto(false); };
    document.addEventListener('keydown', escape);
    return () => document.removeEventListener('keydown', escape);
  }, [menuAbierto]);

  const irA = (id) => {
    setSeccion(id);
    setMenuAbierto(false);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const encabezado = ENCABEZADOS[seccion];

  return (
    <div className={`pt pm${menuAbierto ? ' menu-abierto' : ''}`}>
      {/* Barra superior: solo aparece en pantallas pequeñas */}
      <div className="pt-movil">
        <button type="button" className="pt-marca" onClick={() => irA('inicio')}>
          <Logo size={22} /><span>TechLink</span>
        </button>
        <button
          type="button"
          className="pt-hamburguesa"
          aria-label={menuAbierto ? 'Cerrar menú' : 'Abrir menú'}
          aria-expanded={menuAbierto}
          aria-controls="pt-lateral"
          onClick={() => setMenuAbierto((v) => !v)}
        >
          <span /><span /><span />
        </button>
      </div>

      <aside id="pt-lateral" className="pt-lateral">
        <button type="button" className="pt-marca" onClick={() => irA('inicio')} aria-label="TechLink, ir al inicio">
          <Logo /><span>TechLink</span>
        </button>

        <nav className="pt-nav" aria-label="Secciones">
          <p className="pt-nav-tit">Menú del mentor</p>
          {SECCIONES.map((s) => (
            <button
              key={s.id}
              type="button"
              className={`pt-enlace${seccion === s.id ? ' activo' : ''}`}
              aria-current={seccion === s.id ? 'page' : undefined}
              onClick={() => irA(s.id)}
            >
              <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true" {...trazo}>{s.icono}</svg>
              {s.titulo}
              {s.id === 'notificaciones' && pendientes > 0 && (
                <span className="no-badge" aria-label={`${pendientes} sin leer`}>{pendientes}</span>
              )}
            </button>
          ))}
        </nav>

        <div className="pt-usuario">
          <div className="pt-usuario-fila">
            <Avatar className="pt-avatar" idUsuario={fotoPropia.idUsuario} tieneFoto={fotoPropia.tieneFoto}
              version={fotoPropia.version} iniciales={iniciales} tamano={40} />
            <div className="pt-usuario-txt">
              <p className="pt-usuario-nombre">{nombreCompleto}</p>
              <p className="pt-usuario-dato">Mentor</p>
            </div>
          </div>
          <button type="button" className="pt-salir" onClick={logout}>
            <svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true" {...trazo}>
              <path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3" /><path d="M10 17l-5-5 5-5" /><path d="M5 12h11" />
            </svg>
            Cerrar sesión
          </button>
        </div>
      </aside>

      {menuAbierto && <div className="pt-velo" onClick={() => setMenuAbierto(false)} aria-hidden="true" />}

      <div className="pt-zona">
        <div className="pt-fila">
          <div className="pt-principal">
            <div className="pt-cuerpo">
              {seccion === 'inicio' && <InicioMentor key="inicio" nombre={primerNombre} pendientes={pendientes} irA={irA} />}
              {encabezado && (
                <section key={seccion} className="pt-seccion">
                  <p className="pt-eyebrow">{encabezado[0]}</p>
                  <h1>{encabezado[1]}</h1>
                  <p className="pt-intro">{encabezado[2]}</p>
                  {seccion === 'sesiones' && <MisSesiones />}
                  {seccion === 'calendario' && <Calendario />}
                  {seccion === 'metas' && <MetasMentor />}
                  {seccion === 'estadisticas' && <EstadisticasMentor />}
                  {seccion === 'notificaciones' && <Notificaciones onCambio={ponerPendientes} />}
                  {seccion === 'foro' && <Foro />}
                  {seccion === 'perfil' && <PerfilMentor />}
                  {seccion === 'cuenta' && <MiCuenta />}
                  {seccion === 'ayuda' && <CentroAyuda />}
                </section>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
