import { useState } from 'react';
import './PortalMentor.css';
import { useAuth } from '../auth/authContext';
import PerfilMentor from './PerfilMentor';
import MisSesiones from './MisSesiones';

const SECCIONES = [
  { id: 'perfil', titulo: 'Mi perfil' },
  { id: 'sesiones', titulo: 'Mis sesiones' }
];

// Sitio del mentor: su perfil (HU-05) y sus sesiones con las notas (HU-20 y HU-21).
export default function PortalMentor() {
  const { logout } = useAuth();
  const [seccion, setSeccion] = useState('perfil');

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
            </button>
          ))}
        </nav>
        <button type="button" className="pm-salir" onClick={logout}>Cerrar sesión</button>
      </header>

      {seccion === 'perfil' && <PerfilMentor />}
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
