import LoginForm from './components/LoginForm';
import Dashboard from './components/Dashboard';
import ElegirCiclo from './components/ElegirCiclo';
import PerfilMentor from './components/PerfilMentor';
import PortalEstudiante from './components/PortalEstudiante';
import { useAuth } from './auth/authContext';

// Sin router por ahora: con sesión, el panel; sin sesión, el login.
// Un estudiante sin ciclo (cuenta nueva de Google) pasa primero por "Elige tu ciclo".
// Un mentor entra a su perfil (HU-05): fortalezas, cursos, áreas y disponibilidad.
function App() {
  const { estaAutenticado, usuario } = useAuth();
  const faltaCiclo = usuario?.rol === 'ESTUDIANTE' && usuario.ciclo == null;

  let pantalla = <LoginForm />;
  if (estaAutenticado) {
    if (usuario.rol === 'MENTOR') pantalla = <PerfilMentor />;
    else pantalla = faltaCiclo ? <ElegirCiclo /> : usuario.rol === 'ESTUDIANTE' ? <PortalEstudiante /> : <Dashboard />;
  }

  return <main>{pantalla}</main>;
}

export default App;
