import LoginForm from './components/LoginForm';
import RegisterForm from './components/RegisterForm';
import Dashboard from './components/Dashboard';
import { useAuth } from './auth/authContext';

// Sin router por ahora: /registro muestra el registro; si hay sesión, el panel; si no, el login
function App() {
  const { estaAutenticado } = useAuth();
  const esRegistro = window.location.pathname === '/registro';

  let pantalla = <LoginForm />;
  if (esRegistro) pantalla = <RegisterForm />;
  else if (estaAutenticado) pantalla = <Dashboard />;

  return <main>{pantalla}</main>;
}

export default App;
