import { useEffect, useState } from 'react';
import './PortalEstudiante.css';
import { useAuth } from '../auth/authContext';
import BuscarMentor from './BuscarMentor';
import imgMentoria from '../assets/buscar-mentor.webp';

const ROMANOS = ['I', 'II', 'III', 'IV', 'V', 'VI'];

const trazo = { fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round', strokeLinejoin: 'round' };

const SECCIONES = [
  {
    id: 'inicio',
    titulo: 'Inicio',
    icono: <><path d="M3 10.5 12 3l9 7.5" /><path d="M5 9.5V20h5v-6h4v6h5V9.5" /></>
  },
  {
    id: 'buscar',
    titulo: 'Buscar mentor',
    icono: <><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></>
  }
];

function Logo({ size = 26 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true">
      <path d="M3 3h8l-3 9H3z" fill="currentColor" />
      <path d="M13 3h8v9h-5l-3 9H9z" fill="currentColor" />
    </svg>
  );
}

// Sitio del estudiante tras el login: barra lateral con las secciones, contenido y footer.
// En pantallas pequeñas la barra lateral se vuelve un menú que se abre con el botón ☰.
export default function PortalEstudiante() {
  const { usuario, logout } = useAuth();
  const [seccion, setSeccion] = useState('inicio');
  const [menuAbierto, setMenuAbierto] = useState(false);

  const primerNombre = (usuario.nombres || '').split(' ')[0];
  const apellidos = usuario.apellidos && usuario.apellidos !== '-' ? usuario.apellidos : '';
  const nombreCompleto = [usuario.nombres, apellidos].filter(Boolean).join(' ');
  const iniciales = [usuario.nombres, apellidos].filter(Boolean).map((p) => p.trim()[0]).join('').slice(0, 2).toUpperCase();
  const ciclo = usuario.ciclo ? `${ROMANOS[usuario.ciclo - 1] || usuario.ciclo} ciclo` : null;

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

  return (
    <div className={`pt${menuAbierto ? ' menu-abierto' : ''}`}>
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
          <p className="pt-nav-tit">Menú</p>
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
            </button>
          ))}
        </nav>

        <div className="pt-usuario">
          <div className="pt-usuario-fila">
            <span className="pt-avatar" aria-hidden="true">{iniciales}</span>
            <div className="pt-usuario-txt">
              <p className="pt-usuario-nombre">{nombreCompleto}</p>
              <p className="pt-usuario-dato">{ciclo || usuario.correoInstitucional}</p>
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
          {seccion === 'inicio' && (
            <section key="inicio" className="pt-seccion">
              <p className="pt-eyebrow">Panel del estudiante</p>
              <h1>Hola, {primerNombre}.</h1>
              <p className="pt-intro">Te damos la bienvenida a TechLink, la plataforma de mentorías de Tecsup.</p>
              <div className="pt-tarjeta">
                <div>
                  <h2>Encuentra a tu mentor ideal</h2>
                  <p>Dinos en qué cursos necesitas ayuda y cuándo tienes tiempo libre. Nuestra IA te propone los 3 mentores que mejor coinciden.</p>
                </div>
                <button type="button" className="bm-primario claro" onClick={() => irA('buscar')}>
                  Buscar mentor <span aria-hidden="true">→</span>
                </button>
              </div>
            </section>
          )}
          {seccion === 'buscar' && (
            <section key="buscar" className="pt-seccion pt-buscar">
              {/* En pantallas angostas la imagen se muestra aquí como franja; en anchas va en el lado derecho. */}
              <img className="pt-buscar-franja" src={imgMentoria} alt="" aria-hidden="true" />
              <p className="pt-eyebrow">Mentorías</p>
              <h1>Buscar mentor</h1>
              <BuscarMentor />
            </section>
          )}
        </div>

      </div>

      {/* Lado derecho de Buscar mentor: la imagen ocupa toda la altura, sin marco */}
      {seccion === 'buscar' && (
        <figure className="pt-arte">
          <img src={imgMentoria} alt="Estudiante en una videollamada con su mentor desde la biblioteca" />
          <figcaption className="pt-arte-cap">
            <span className="pt-hero-badge">
              <span className="pt-hero-punto" aria-hidden="true" />
              Mentorías 1 a 1 en línea
            </span>
            <p>Conecta con un estudiante de ciclos superiores que domina los cursos donde necesitas apoyo.</p>
          </figcaption>
        </figure>
      )}
      </div>

      {/* El footer va fuera de la fila contenido + imagen, para ocupar todo el ancho debajo de ambos */}
      <footer className="pt-footer">
        <div className="pt-footer-arriba">
          <div className="pt-footer-info">
            <div className="pt-footer-marca"><Logo size={22} /><span>TechLink</span></div>
            <p className="pt-footer-desc">Plataforma de mentorías entre estudiantes de Tecsup.</p>
          </div>
          <nav className="pt-footer-nav" aria-label="Secciones (pie de página)">
            {SECCIONES.map((s) => (
              <button key={s.id} type="button" onClick={() => irA(s.id)}>{s.titulo}</button>
            ))}
          </nav>
        </div>
        <p className="pt-footer-copy">© {new Date().getFullYear()} TechLink · Mentorías Tecsup</p>
      </footer>
      </div>
    </div>
  );
}
