import LoginForm from './components/LoginForm';
import RegisterForm from './components/RegisterForm';
import Dashboard from './components/Dashboard';
import ElegirCiclo from './components/ElegirCiclo';
import { useAuth } from './auth/authContext';

// Sin router por ahora: /registro muestra el registro; con sesión, el panel; sin sesión, el login.
// Un estudiante sin ciclo (cuenta nueva de Google) pasa primero por "Elige tu ciclo".
function App() {
  const { estaAutenticado, usuario } = useAuth();
  const esRegistro = window.location.pathname === '/registro';
  const faltaCiclo = usuario?.rol === 'ESTUDIANTE' && usuario.ciclo == null;

  let pantalla = <LoginForm />;
  if (esRegistro) pantalla = <RegisterForm />;
  else if (estaAutenticado) pantalla = faltaCiclo ? <ElegirCiclo /> : <Dashboard />;

  return <main>{pantalla}</main>;
}

export default App;
