import { useState } from 'react';
import './PortalEstudiante.css';
import { useAuth } from '../auth/authContext';
import BuscarMentor from './BuscarMentor';

const ROMANOS = ['I', 'II', 'III', 'IV', 'V', 'VI'];

const SECCIONES = [
  { id: 'inicio', titulo: 'Inicio' },
  { id: 'buscar', titulo: 'Buscar mentor' }
];

// Sitio del estudiante tras el login: barra lateral con secciones y el contenido de la sección elegida.
export default function PortalEstudiante() {
  const { usuario, logout } = useAuth();
  const [seccion, setSeccion] = useState('inicio');

  const primerNombre = (usuario.nombres || '').split(' ')[0];
  const apellidos = usuario.apellidos && usuario.apellidos !== '-' ? usuario.apellidos : '';
  const nombreCompleto = [usuario.nombres, apellidos].filter(Boolean).join(' ');
  const ciclo = usuario.ciclo ? `${ROMANOS[usuario.ciclo - 1] || usuario.ciclo} ciclo` : null;

  return (
    <div className="pt">
      <aside className="pt-lateral">
        <div className="pt-marca">
          <svg width="26" height="26" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M3 3h8l-3 9H3z" fill="currentColor" />
            <path d="M13 3h8v9h-5l-3 9H9z" fill="currentColor" />
          </svg>
          TechLink
        </div>
        <nav aria-label="Secciones">
          {SECCIONES.map((s) => (
            <button
              key={s.id}
              type="button"
              className={`pt-enlace${seccion === s.id ? ' activo' : ''}`}
              aria-current={seccion === s.id ? 'page' : undefined}
              onClick={() => setSeccion(s.id)}
            >
              {s.titulo}
            </button>
          ))}
        </nav>
        <div className="pt-usuario">
          <p className="pt-usuario-nombre">{nombreCompleto}</p>
          {ciclo && <p className="pt-usuario-dato">{ciclo}</p>}
          <button type="button" className="pt-salir" onClick={logout}>Cerrar sesión</button>
        </div>
      </aside>

      <div className="pt-contenido">
        {seccion === 'inicio' && (
          <section>
            <p className="pt-eyebrow">Panel del estudiante</p>
            <h1>Hola, {primerNombre}.</h1>
            <p className="pt-intro">Te damos la bienvenida a TechLink, la plataforma de mentorías de Tecsup.</p>
            <div className="pt-tarjeta">
              <h2>Encuentra a tu mentor ideal</h2>
              <p>Dinos en qué cursos necesitas ayuda y cuándo tienes tiempo libre. Nuestra IA te propone los 3 mentores que mejor coinciden.</p>
              <button type="button" className="bm-primario" onClick={() => setSeccion('buscar')}>Buscar mentor</button>
            </div>
          </section>
        )}
        {seccion === 'buscar' && (
          <section>
            <p className="pt-eyebrow">Mentorías</p>
            <h1>Buscar mentor</h1>
            <BuscarMentor />
          </section>
        )}
      </div>
    </div>
  );
}
