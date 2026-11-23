import { useEffect, useState } from 'react';
import './PortalEstudiante.css';
import { useAuth } from '../auth/authContext';
import InicioEstudiante from './InicioEstudiante';
import BuscarMentor from './BuscarMentor';
import MisSesiones from './MisSesiones';
import Foro from './Foro';
import { tomarSalaPendiente } from '../enlaceSala';
import PerfilDesdeAviso from './PerfilDesdeAviso';
import Charlas from './Charlas';
import Seguimiento from './Seguimiento';
import Calendario from './Calendario';
import CentroAyuda from './CentroAyuda';
import Notificaciones from './Notificaciones';
import useNotificacionesPendientes from './useNotificaciones';
import MentoresRanking from './MentoresRanking';
import MiCuenta from './MiCuenta';
import PostularMentor from './PostularMentor';
import Avatar from './Avatar';
import { useFotoPropia } from './fotoPerfil';
import AsistenteChat from './AsistenteChat';

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
    id: 'progreso',
    titulo: 'Mi progreso',
    icono: <><path d="M3 20h18" /><path d="M6 20v-6M11 20V9M16 20v-9M21 20V4" /></>
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
    id: 'notificaciones',
    titulo: 'Notificaciones',
    icono: <><path d="M6 9a6 6 0 0 1 12 0c0 6 2 7.5 2 7.5H4S6 15 6 9" /><path d="M10 20a2 2 0 0 0 4 0" /></>
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

// El menú se agrupa por tema para que no sea una lista larga: lo de cada día arriba, y lo de la cuenta abajo.
const GRUPOS = [
  { titulo: '', ids: ['inicio'] },
  { titulo: 'Mentorías', ids: ['buscar', 'sesiones', 'calendario'] },
  { titulo: 'Mi avance', ids: ['progreso', 'charlas'] },
  { titulo: 'Comunidad', ids: ['foro', 'postular'] }
];
const INFERIOR = ['notificaciones', 'ayuda', 'cuenta'];
const porId = (id) => SECCIONES.find((x) => x.id === id);

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
  const [salaPendiente] = useState(() => tomarSalaPendiente()); // sala que abrió un enlace de sesión o de charla
  const salaInicial = salaPendiente?.tipo === 'sesion' ? salaPendiente.id : null;
  const charlaInicial = salaPendiente?.tipo === 'charla' ? salaPendiente.id : null;
  const [seccion, setSeccion] = useState(salaInicial ? 'sesiones' : charlaInicial ? 'charlas' : 'inicio');
  const [vistaBuscar, setVistaBuscar] = useState('buscar'); // dentro de «Buscar mentor»: 'buscar' o 'ranking'
  const [irConsulta, setIrConsulta] = useState(null); // consulta del foro que abrió un aviso

  // Un aviso (por ejemplo, «reaccionaron a tu respuesta») lleva directo a su consulta del foro.
  useEffect(() => {
    const ir = (e) => {
      setSeccion(e.detail.seccion);
      setIrConsulta(e.detail.consultaId ?? null);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    };
    window.addEventListener('techlink:ir', ir);
    return () => window.removeEventListener('techlink:ir', ir);
  }, []);
  const [menuAbierto, setMenuAbierto] = useState(false);
  const { pendientes, poner: ponerPendientes } = useNotificacionesPendientes();

  const primerNombre = (usuario.nombres || '').split(' ')[0];
  const apellidos = usuario.apellidos && usuario.apellidos !== '-' ? usuario.apellidos : '';
  const iniciales = [usuario.nombres, apellidos].filter(Boolean).map((p) => p.trim()[0]).join('').slice(0, 2).toUpperCase();

  // Escape cierra el menú en celular.
  useEffect(() => {
    if (!menuAbierto) return undefined;
    const escape = (e) => { if (e.key === 'Escape') setMenuAbierto(false); };
    document.addEventListener('keydown', escape);
    return () => document.removeEventListener('keydown', escape);
  }, [menuAbierto]);

  const irA = (id) => {
    // «Mentores» ahora vive dentro de «Buscar mentor», en la pestaña del ranking.
    if (id === 'mentores') {
      setVistaBuscar('ranking');
      id = 'buscar';
    }
    setSeccion(id);
    setMenuAbierto(false);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  // Un enlace del menú; el de notificaciones lleva el número de avisos sin leer.
  const enlace = (s) => (
    <button
      key={s.id}
      type="button"
      className={`pt-enlace${seccion === s.id ? ' activo' : ''}`}
      aria-current={seccion === s.id ? 'page' : undefined}
      onClick={() => irA(s.id)}
    >
      <svg width="19" height="19" viewBox="0 0 24 24" aria-hidden="true" {...trazo}>{s.icono}</svg>
      {s.titulo}
      {s.id === 'notificaciones' && pendientes > 0 && (
        <span className="no-badge" aria-label={`${pendientes} sin leer`}>{pendientes}</span>
      )}
    </button>
  );

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
          {GRUPOS.map((g) => (
            <div key={g.titulo || 'inicio'} className="pt-grupo">
              {g.titulo && <p className="pt-nav-tit">{g.titulo}</p>}
              {g.ids.map((id) => enlace(porId(id)))}
            </div>
          ))}
        </nav>

        <div className="pt-inferior">
          {INFERIOR.map((id) => enlace(porId(id)))}
          <button type="button" className="pt-salir" onClick={logout}>
            <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true" {...trazo}>
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
        {/* Barra superior (solo en pantallas anchas): avisos y acceso a la cuenta */}
        <div className="pt-barra">
          <button type="button" className="pt-campana" onClick={() => irA('notificaciones')}
            aria-label={pendientes > 0 ? `Notificaciones, ${pendientes} sin leer` : 'Notificaciones'}>
            <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true" {...trazo}>
              <path d="M6 9a6 6 0 0 1 12 0c0 6 2 7.5 2 7.5H4S6 15 6 9" /><path d="M10 20a2 2 0 0 0 4 0" />
            </svg>
            {pendientes > 0 && <span className="pt-campana-punto" aria-hidden="true">{pendientes > 9 ? '9+' : pendientes}</span>}
          </button>
          <button type="button" className="pt-perfil" onClick={() => irA('cuenta')} aria-label="Mi cuenta">
            <Avatar idUsuario={fotoPropia.idUsuario} tieneFoto={fotoPropia.tieneFoto} version={fotoPropia.version} iniciales={iniciales} tamano={34} />
            <span>{primerNombre}</span>
          </button>
        </div>
        <div className="pt-cuerpo">
          {seccion === 'inicio' && (
            <section key="inicio" className="pt-seccion">
              <InicioEstudiante primerNombre={primerNombre} irA={irA} />
            </section>
          )}
          {seccion === 'buscar' && (
            <section key="buscar" className="pt-seccion pt-buscar">
              <p className="pt-eyebrow">Mentorías</p>
              <h1>Buscar <span>mentor</span></h1>
              <div className="pt-pestanas" role="tablist" aria-label="Cómo buscar mentores">
                <button type="button" role="tab" aria-selected={vistaBuscar === 'buscar'} className={vistaBuscar === 'buscar' ? 'activa' : ''}
                  onClick={() => setVistaBuscar('buscar')}>Buscar por curso y horario</button>
                <button type="button" role="tab" aria-selected={vistaBuscar === 'ranking'} className={vistaBuscar === 'ranking' ? 'activa' : ''}
                  onClick={() => setVistaBuscar('ranking')}>Ranking de mentores</button>
              </div>
              {vistaBuscar === 'buscar' ? <BuscarMentor /> : <MentoresRanking />}
            </section>
          )}
          {seccion === 'postular' && (
            <section key="postular" className="pt-seccion">
              <p className="pt-eyebrow">Acreditación</p>
              <h1>Ser <span>mentor</span></h1>
              <p className="pt-intro">Si ya llevas varios ciclos, puedes postular para guiar a otros alumnos.</p>
              <PostularMentor />
            </section>
          )}
          {seccion === 'progreso' && (
            <section key="progreso" className="pt-seccion">
              <p className="pt-eyebrow">Tu avance</p>
              <h1>Mi <span>progreso</span></h1>
              <p className="pt-intro">Marca las metas de la semana que acuerdas con tu mentor y mira cómo evolucionan tus horas de asesoría y tu rendimiento.</p>
              <Seguimiento />
            </section>
          )}
          {seccion === 'charlas' && (
            <section key="charlas" className="pt-seccion">
              <p className="pt-eyebrow">Comunidad</p>
              <h1>Charlas <span>virtuales</span></h1>
              <p className="pt-intro">Te recomendamos charlas según tus intereses y tus cursos críticos. Inscríbete y la verás en tu calendario.</p>
              <Charlas salaInicial={charlaInicial} />
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
              <h1>Foro de <span>consultas</span></h1>
              <p className="pt-intro">Pregunta tus dudas por curso y ayuda a otros con lo que sabes. Quien pregunta puede marcar la respuesta que le sirvió.</p>
              <Foro key={irConsulta ?? 'lista'} consultaInicial={irConsulta} />
            </section>
          )}
          {seccion === 'notificaciones' && (
            <section key="notificaciones" className="pt-seccion">
              <p className="pt-eyebrow">Tus avisos</p>
              <h1>Notificaciones</h1>
              <p className="pt-intro">Confirmaciones, cambios de tus sesiones, recordatorios y anuncios. En Preferencias eliges qué recibir.</p>
              <Notificaciones onCambio={ponerPendientes} />
            </section>
          )}
          {seccion === 'ayuda' && (
            <section key="ayuda" className="pt-seccion">
              <p className="pt-eyebrow">Soporte</p>
              <h1>Centro de <span>ayuda</span></h1>
              <p className="pt-intro">Respuestas a las preguntas más frecuentes sobre TechLink.</p>
              <CentroAyuda />
            </section>
          )}
          {seccion === 'cuenta' && (
            <section key="cuenta" className="pt-seccion">
              <p className="pt-eyebrow">Tu perfil</p>
              <h1>Mi <span>cuenta</span></h1>
              <p className="pt-intro">Así te ven los demás. Cuéntales quién eres y qué te interesa.</p>
              <MiCuenta />
            </section>
          )}
          {seccion === 'sesiones' && (
            <section key="sesiones" className="pt-seccion">
              <p className="pt-eyebrow">Mentorías</p>
              <h1>Mis <span>sesiones</span></h1>
              <p className="pt-intro">Tu historial de sesiones: califica las que terminaron, y cancela o reprograma las próximas.</p>
              <MisSesiones salaInicial={salaInicial} />
            </section>
          )}
        </div>

      </div>
      </div>

      </div>

      <PerfilDesdeAviso />
      <AsistenteChat contexto={seccion} rol="ESTUDIANTE" />
    </div>
  );
}
