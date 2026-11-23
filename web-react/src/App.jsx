import { useEffect } from 'react';
import LoginForm from './components/LoginForm';
import Dashboard from './components/Dashboard';
import ElegirCiclo from './components/ElegirCiclo';
import IrAlPanel from './components/IrAlPanel';
import PortalMentor from './components/PortalMentor';
import PortalEstudiante from './components/PortalEstudiante';
import VerificarConstancia from './components/VerificarConstancia';
import SalaInvitado from './components/SalaInvitado';
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

  // HU-27: el código QR de una constancia abre /verificar/<código>, una página pública (sin iniciar sesión).
  const verificar = window.location.pathname.match(/^\/verificar\/([A-Za-z0-9]{4,20})\/?$/);
  if (verificar) return <VerificarConstancia codigo={verificar[1]} />;

  // El invitado de una charla entra con su enlace secreto, sin cuenta: /sala/invitado/<enlace>.
  const invitado = window.location.pathname.match(/^\/sala\/invitado\/([A-Za-z0-9_-]{16,64})\/?$/);
  if (invitado) return <SalaInvitado token={invitado[1]} />;

  let pantalla = <LoginForm />;
  if (estaAutenticado && !saliendo) {
    if (usuario.rol === 'MENTOR') pantalla = <PortalMentor />;
    else if (usuario.rol === 'ADMIN') pantalla = <IrAlPanel />;
    else pantalla = faltaCiclo ? <ElegirCiclo /> : usuario.rol === 'ESTUDIANTE' ? <PortalEstudiante /> : <Dashboard />;
  }

  return <main>{pantalla}</main>;
}

export default App;
