import { useEffect, useState } from 'react';
import './PortalEstudiante.css';
import { useAuth } from '../auth/authContext';
import BuscarMentor from './BuscarMentor';
import MisSesiones from './MisSesiones';
import Foro from './Foro';
import Charlas from './Charlas';
import Calendario from './Calendario';
import CentroAyuda from './CentroAyuda';
import MentoresRanking from './MentoresRanking';
import MiCuenta from './MiCuenta';
import PostularMentor from './PostularMentor';
import Avatar from './Avatar';
import { useFotoPropia } from './fotoPerfil';
import AsistenteChat from './AsistenteChat';
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
  },
  {
    id: 'sesiones',
    titulo: 'Mis sesiones',
    icono: <><rect x="3.5" y="5" width="17" height="15" rx="2" /><path d="M8 3v4M16 3v4M3.5 10h17" /></>
  },
  {
    id: 'charlas',
    titulo: 'Charlas',
    icono: <><rect x="3" y="5" width="13" height="14" rx="2" /><path d="m16 10 5-3v10l-5-3" /></>
  },
  {
    id: 'calendario',
    titulo: 'Calendario',
    icono: <><rect x="3.5" y="5" width="17" height="15" rx="2" /><path d="M8 3v4M16 3v4M3.5 10h17M8 14h2M12 14h2M16 14h.01M8 17h2" /></>
  },
  {
    id: 'mentores',
    titulo: 'Mentores',
    icono: <><path d="M8 21h8M12 17v4" /><path d="M7 4h10v4a5 5 0 0 1-10 0z" /><path d="M7 6H4v1a3 3 0 0 0 3 3M17 6h3v1a3 3 0 0 1-3 3" /></>
  },
  {
    id: 'postular',
    titulo: 'Ser mentor',
    icono: <><path d="m12 3 2.6 5.6 6.1.7-4.5 4.2 1.2 6L12 16.5 6.6 19.5l1.2-6L3.3 9.3l6.1-.7z" /></>
  },
  {
    id: 'foro',
    titulo: 'Foro',
    icono: <><path d="M4 5h16v10H9l-5 4z" /><path d="M8 9h8M8 12h5" /></>
  },
  {
    id: 'ayuda',
    titulo: 'Ayuda',
    icono: <><circle cx="12" cy="12" r="9" /><path d="M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.7.4-1 .9-1 1.7M12 17h.01" /></>
  },
  {
    id: 'cuenta',
    titulo: 'Mi cuenta',
    icono: <><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /></>
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
  const fotoPropia = useFotoPropia();
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
            <Avatar className="pt-avatar" idUsuario={fotoPropia.idUsuario} tieneFoto={fotoPropia.tieneFoto}
              version={fotoPropia.version} iniciales={iniciales} tamano={40} />
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
          {seccion === 'mentores' && (
            <section key="mentores" className="pt-seccion">
              <p className="pt-eyebrow">Comunidad</p>
              <h1>Mentores</h1>
              <p className="pt-intro">El ranking de mentores por calificación y horas dictadas. Abre un perfil para ver sus cursos, comentarios e insignias.</p>
              <MentoresRanking />
            </section>
          )}
          {seccion === 'postular' && (
            <section key="postular" className="pt-seccion">
              <p className="pt-eyebrow">Acreditación</p>
              <h1>Ser mentor</h1>
              <p className="pt-intro">Si ya llevas varios ciclos, puedes postular para guiar a otros alumnos.</p>
              <PostularMentor />
            </section>
          )}
          {seccion === 'charlas' && (
            <section key="charlas" className="pt-seccion">
              <p className="pt-eyebrow">Comunidad</p>
              <h1>Charlas virtuales</h1>
              <p className="pt-intro">Te recomendamos charlas según tus intereses y tus cursos críticos. Inscríbete y la verás en tu calendario.</p>
              <Charlas />
            </section>
          )}
          {seccion === 'calendario' && (
            <section key="calendario" className="pt-seccion">
              <p className="pt-eyebrow">Tu agenda</p>
              <h1>Calendario</h1>
              <p className="pt-intro">Tus sesiones de mentoría y las charlas a las que te inscribiste, por semana o por día.</p>
              <Calendario />
            </section>
          )}
          {seccion === 'foro' && (
            <section key="foro" className="pt-seccion">
              <p className="pt-eyebrow">Comunidad</p>
              <h1>Foro de consultas</h1>
              <p className="pt-intro">Pregunta tus dudas por curso y ayuda a otros con lo que sabes. Quien pregunta puede marcar la respuesta que le sirvió.</p>
              <Foro />
            </section>
          )}
          {seccion === 'ayuda' && (
            <section key="ayuda" className="pt-seccion">
              <p className="pt-eyebrow">Soporte</p>
              <h1>Centro de ayuda</h1>
              <p className="pt-intro">Respuestas a las preguntas más frecuentes sobre TechLink.</p>
              <CentroAyuda />
            </section>
          )}
          {seccion === 'cuenta' && (
            <section key="cuenta" className="pt-seccion">
              <p className="pt-eyebrow">Tu perfil</p>
              <h1>Mi cuenta</h1>
              <p className="pt-intro">Así te ven los demás. Cuéntales quién eres y qué te interesa.</p>
              <MiCuenta />
            </section>
          )}
          {seccion === 'sesiones' && (
            <section key="sesiones" className="pt-seccion">
              <p className="pt-eyebrow">Mentorías</p>
              <h1>Mis sesiones</h1>
              <p className="pt-intro">Tu historial de sesiones: califica las que terminaron, y cancela o reprograma las próximas.</p>
              <MisSesiones />
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

      <AsistenteChat />
    </div>
  );
}
