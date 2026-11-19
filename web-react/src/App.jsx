import { useEffect } from 'react';
import LoginForm from './components/LoginForm';
import Dashboard from './components/Dashboard';
import ElegirCiclo from './components/ElegirCiclo';
import IrAlPanel from './components/IrAlPanel';
import PortalMentor from './components/PortalMentor';
import PortalEstudiante from './components/PortalEstudiante';
import { useAuth } from './auth/authContext';

// Sin router por ahora: con sesión, el panel; sin sesión, el login.
// Un estudiante sin ciclo (cuenta nueva de Google) pasa primero por "Elige tu ciclo".
// Un mentor entra a su perfil (HU-05): fortalezas, cursos, áreas y disponibilidad.
// Un administrador pasa directo a su panel de administración (otra aplicación).
function App() {
  const { estaAutenticado, usuario, logout } = useAuth();
  const faltaCiclo = usuario?.rol === 'ESTUDIANTE' && usuario.ciclo == null;

  // El panel de administración cierra aquí la sesión al salir (/?salir): las dos aplicaciones guardan su sesión por separado.
  const saliendo = new URLSearchParams(window.location.search).has('salir');
  useEffect(() => {
    if (saliendo) {
      logout();
      window.history.replaceState(null, '', window.location.pathname);
    }
  }, [saliendo, logout]);

  let pantalla = <LoginForm />;
  if (estaAutenticado && !saliendo) {
    if (usuario.rol === 'MENTOR') pantalla = <PortalMentor />;
    else if (usuario.rol === 'ADMIN') pantalla = <IrAlPanel />;
    else pantalla = faltaCiclo ? <ElegirCiclo /> : usuario.rol === 'ESTUDIANTE' ? <PortalEstudiante /> : <Dashboard />;
  }

  return <main>{pantalla}</main>;
}

export default App;
