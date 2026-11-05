import './LoginForm.css';
import './Dashboard.css';
import { useAuth } from '../auth/authContext';

const ROMANOS = ['I', 'II', 'III', 'IV', 'V', 'VI'];
const ROLES = { ESTUDIANTE: 'Estudiante', MENTOR: 'Mentor', ADMIN: 'Administrador' };

// Panel mínimo para tener adónde llegar tras el login.
// La redirección por rol y las rutas protegidas (tarea 3 de HU-02) lo reemplazarán.
export default function Dashboard() {
  const { usuario, logout } = useAuth();

  const primerNombre = (usuario.nombres || '').split(' ')[0];
  const apellidos = usuario.apellidos && usuario.apellidos !== '-' ? usuario.apellidos : '';
  const nombreCompleto = [usuario.nombres, apellidos].filter(Boolean).join(' ');
  const iniciales = [usuario.nombres, apellidos]
    .filter(Boolean)
    .map((p) => p.trim()[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();
  const rol = ROLES[usuario.rol] || usuario.rol;
  const ciclo = usuario.ciclo ? `${ROMANOS[usuario.ciclo - 1] || usuario.ciclo} Ciclo` : null;

  return (
    <div className="login-page dash">
      <header className="dash-top">
        <svg className="dash-logo" width="26" height="26" viewBox="0 0 24 24" aria-label="TechLink">
          <path d="M3 3h8l-3 9H3z" fill="currentColor" />
          <path d="M13 3h8v9h-5l-3 9H9z" fill="currentColor" />
        </svg>
        <button type="button" className="dash-salir" onClick={logout}>
          Cerrar sesión
        </button>
      </header>

      <main className="dash-main">
        <p className="dash-eyebrow">Panel de mentorías</p>
        <h1 className="dash-saludo">Hola, {primerNombre}.</h1>
        <p className="dash-intro">Te damos la bienvenida a TechLink.</p>

        <section className="dash-card" aria-label="Tu perfil">
          <div className="dash-perfil">
            <div className="dash-avatar" aria-hidden="true">{iniciales}</div>
            <div className="dash-perfil-texto">
              <p className="dash-nombre">{nombreCompleto}</p>
              <span className="dash-badge">{rol}</span>
            </div>
          </div>

          <dl className="dash-info">
            <div>
              <dt>Correo</dt>
              <dd>{usuario.correoInstitucional}</dd>
            </div>
            {ciclo && (
              <div>
                <dt>Ciclo</dt>
                <dd>{ciclo}</dd>
              </div>
            )}
          </dl>
        </section>

      </main>
    </div>
  );
}
