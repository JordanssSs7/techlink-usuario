import LoginForm from './components/LoginForm';
import Dashboard from './components/Dashboard';
import ElegirCiclo from './components/ElegirCiclo';
import { useAuth } from './auth/authContext';

// Sin router por ahora: con sesión, el panel; sin sesión, el login.
// Un estudiante sin ciclo (cuenta nueva de Google) pasa primero por "Elige tu ciclo".
function App() {
  const { estaAutenticado, usuario } = useAuth();
  const faltaCiclo = usuario?.rol === 'ESTUDIANTE' && usuario.ciclo == null;

  let pantalla = <LoginForm />;
  if (estaAutenticado) pantalla = faltaCiclo ? <ElegirCiclo /> : <Dashboard />;

  return <main>{pantalla}</main>;
}

export default App;
