import { useEffect } from 'react';
import './LoginForm.css';
import './Dashboard.css';
import { useAuth } from '../auth/authContext';

// Panel de administración (React + Django). Es otra aplicación, en otro puerto, por eso se le pasa la sesión
// en el fragmento de la URL (#sesion=...): el fragmento no viaja al servidor y el panel lo borra al leerlo.
const URL_PANEL_ADMIN = import.meta.env.VITE_ADMIN_URL || 'http://localhost:5174';

// Los administradores no usan esta aplicación: al iniciar sesión pasan directo a su panel.
export default function IrAlPanel() {
  const { usuario, token, logout } = useAuth();
  const urlPanel = `${URL_PANEL_ADMIN}/#sesion=${encodeURIComponent(JSON.stringify({ token, usuario }))}`;

  useEffect(() => {
    window.location.replace(urlPanel);
  }, [urlPanel]);

  return (
    <div className="login-page dash">
      <main className="dash-main">
        <p className="dash-eyebrow">Panel de administración</p>
        <h1 className="dash-saludo">Abriendo el panel...</h1>
        <p className="dash-intro">Si no se abre solo, usa el botón. El panel debe estar encendido en {URL_PANEL_ADMIN}.</p>
        <a className="login-submit" href={urlPanel} style={{ display: 'block', textAlign: 'center', textDecoration: 'none' }}>
          Abrir el panel de administración
        </a>
        <p className="login-signup">
          <button type="button" className="login-link" onClick={logout}>
            Cerrar sesión
          </button>
        </p>
      </main>
    </div>
  );
}
