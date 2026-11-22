import { useState } from 'react';
import './PortalMentor.css';
import { useAuth } from '../auth/authContext';
import PerfilMentor from './PerfilMentor';
import MisSesiones from './MisSesiones';
import MiCuenta from './MiCuenta';
import Foro from './Foro';
import Calendario from './Calendario';
import CentroAyuda from './CentroAyuda';
import Notificaciones from './Notificaciones';
import EstadisticasMentor from './EstadisticasMentor';
import useNotificacionesPendientes from './useNotificaciones';

const SECCIONES = [
  { id: 'perfil', titulo: 'Mi perfil' },
  { id: 'cuenta', titulo: 'Mis datos' },
  { id: 'sesiones', titulo: 'Mis sesiones' },
  { id: 'calendario', titulo: 'Calendario' },
  { id: 'estadisticas', titulo: 'Estadísticas' },
  { id: 'notificaciones', titulo: 'Notificaciones' },
  { id: 'foro', titulo: 'Foro' },
  { id: 'ayuda', titulo: 'Ayuda' }
];

// Sitio del mentor: su perfil (HU-05) y sus sesiones con las notas (HU-20 y HU-21).
export default function PortalMentor() {
  const { logout } = useAuth();
  const [seccion, setSeccion] = useState('perfil');
  const { pendientes, poner: ponerPendientes } = useNotificacionesPendientes();

  return (
    <div className="pm">
      <header className="pm-barra">
        <div className="pm-marca">
          <svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M3 3h8l-3 9H3z" fill="currentColor" />
            <path d="M13 3h8v9h-5l-3 9H9z" fill="currentColor" />
          </svg>
          TechLink
        </div>
        <nav className="pm-nav" aria-label="Secciones">
          {SECCIONES.map((s) => (
            <button
              key={s.id}
              type="button"
              className={`pm-enlace${seccion === s.id ? ' activo' : ''}`}
              aria-current={seccion === s.id ? 'page' : undefined}
              onClick={() => setSeccion(s.id)}
            >
              {s.titulo}
              {s.id === 'notificaciones' && pendientes > 0 && (
                <span className="no-badge" aria-label={`${pendientes} sin leer`}>{pendientes}</span>
              )}
            </button>
          ))}
        </nav>
        <button type="button" className="pm-salir" onClick={logout}>Cerrar sesión</button>
      </header>

      {seccion === 'perfil' && <PerfilMentor />}
      {seccion === 'cuenta' && (
        <main className="pm-contenido">
          <p className="pm-eyebrow">Tu perfil</p>
          <h1>Mis datos</h1>
          <p className="pm-intro">Tu foto y tu descripción se ven en tu perfil público de mentor.</p>
          <MiCuenta />
        </main>
      )}
      {seccion === 'calendario' && (
        <main className="pm-contenido">
          <p className="pm-eyebrow">Tu agenda</p>
          <h1>Calendario</h1>
          <p className="pm-intro">Tus sesiones con los estudiantes, por semana o por día. Elige una para ver su detalle y el enlace de la reunión.</p>
          <Calendario />
        </main>
      )}
      {seccion === 'estadisticas' && (
        <main className="pm-contenido">
          <p className="pm-eyebrow">Tu desempeño</p>
          <h1>Estadísticas</h1>
          <p className="pm-intro">Tus sesiones, horas y calificación mes a mes, y tu constancia de horas de mentoría en PDF.</p>
          <EstadisticasMentor />
        </main>
      )}
      {seccion === 'foro' && (
        <main className="pm-contenido">
          <p className="pm-eyebrow">Comunidad</p>
          <h1>Foro de consultas</h1>
          <p className="pm-intro">Responde las dudas de los estudiantes por curso: tu experiencia es la que más ayuda.</p>
          <Foro />
        </main>
      )}
      {seccion === 'notificaciones' && (
        <main className="pm-contenido">
          <p className="pm-eyebrow">Tus avisos</p>
          <h1>Notificaciones</h1>
          <p className="pm-intro">Confirmaciones, cambios de tus sesiones, recordatorios y anuncios. En Preferencias eliges qué recibir.</p>
          <Notificaciones onCambio={ponerPendientes} />
        </main>
      )}
      {seccion === 'ayuda' && (
        <main className="pm-contenido">
          <p className="pm-eyebrow">Soporte</p>
          <h1>Centro de ayuda</h1>
          <p className="pm-intro">Respuestas a las preguntas más frecuentes sobre TechLink.</p>
          <CentroAyuda />
        </main>
      )}
      {seccion === 'sesiones' && (
        <main className="pm-contenido">
          <p className="pm-eyebrow">Mentorías</p>
          <h1>Mis sesiones</h1>
          <p className="pm-intro">Revisa tus sesiones con los estudiantes y deja notas con los acuerdos y tareas pendientes.</p>
          <MisSesiones />
        </main>
      )}
    </div>
  );
}
