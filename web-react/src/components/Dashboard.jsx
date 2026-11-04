import './LoginForm.css';
import { useAuth } from '../auth/authContext';

// Panel mínimo para tener adónde llegar tras el login.
// La redirección por rol y las rutas protegidas (tarea 3 de HU-02) lo reemplazarán.
export default function Dashboard() {
  const { usuario, logout } = useAuth();

  return (
    <div className="login-page" style={{ justifyContent: 'center', alignItems: 'center' }}>
      <div className="login-box" style={{ maxWidth: 320 }}>
        <svg className="login-logo" width="22" height="22" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M3 3h8l-3 9H3z" fill="currentColor" />
          <path d="M13 3h8v9h-5l-3 9H9z" fill="currentColor" />
        </svg>
        <h1 className="login-title">Hola, {usuario.nombres}</h1>
        <p className="login-subtitle">Panel principal de mentorías TechLink</p>

        <dl className="dash-datos">
          <dt>Correo</dt>
          <dd>{usuario.correoInstitucional}</dd>
          <dt>Rol</dt>
          <dd>{usuario.rol}</dd>
        </dl>

        <button type="button" className="login-submit" onClick={logout}>
          Cerrar sesión
        </button>
      </div>
    </div>
  );
}
