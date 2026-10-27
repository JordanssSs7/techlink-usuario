import { useState } from 'react';
import './LoginForm.css';
import loginBg from '../assets/login-bg.webp';
import { useAuth } from '../auth/authContext';
import { MOCK_AUTH } from '../auth/authApi';

const CICLOS = ['I', 'II', 'III', 'IV', 'V', 'VI'];

// Se muestra tras el primer ingreso (cuentas creadas con Google), cuando el estudiante aún no tiene ciclo.
export default function ElegirCiclo() {
  const { usuario, actualizarUsuario, authFetch, logout } = useAuth();
  const [ciclo, setCiclo] = useState(null);
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!ciclo) {
      setError('Elige tu ciclo para continuar.');
      return;
    }

    setIsSubmitting(true);
    setError('');
    try {
      // En modo de prueba no hay backend: se guarda solo en la sesión local.
      if (!MOCK_AUTH) {
        const response = await authFetch('/api/estudiantes/me', {
          method: 'PATCH',
          body: JSON.stringify({ ciclo })
        });
        if (!response.ok) {
          const data = await response.json().catch(() => ({}));
          setError(data.campos?.ciclo || data.error || 'No se pudo guardar tu ciclo. Inténtalo nuevamente.');
          return;
        }
      }
      actualizarUsuario({ ciclo });
    } catch {
      setError('Error al conectar con el servidor.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="login-page">
      <section className="login-panel">
        <div className="login-box">
          <svg className="login-logo" width="22" height="22" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M3 3h8l-3 9H3z" fill="currentColor" />
            <path d="M13 3h8v9h-5l-3 9H9z" fill="currentColor" />
          </svg>

          <h1 className="login-title">Elige tu ciclo</h1>
          <p className="login-subtitle">Hola, {usuario.nombres}. Solo falta un dato para continuar.</p>

          <form onSubmit={handleSubmit} noValidate>
            {error && <div className="login-alert" role="alert">{error}</div>}

            <div className="ciclo-grid" role="radiogroup" aria-label="Ciclo académico">
              {CICLOS.map((romano, i) => (
                <button
                  key={romano}
                  type="button"
                  role="radio"
                  aria-checked={ciclo === i + 1}
                  className={`ciclo-opcion${ciclo === i + 1 ? ' ciclo-opcion-activa' : ''}`}
                  onClick={() => { setCiclo(i + 1); setError(''); }}
                >
                  {romano} Ciclo
                </button>
              ))}
            </div>

            <button type="submit" className="login-submit" disabled={isSubmitting}>
              {isSubmitting ? 'Guardando...' : 'Continuar'}
            </button>
          </form>

          <p className="login-signup">
            <a href="#salir" onClick={(e) => { e.preventDefault(); logout(); }}>Cerrar sesión</a>
          </p>
        </div>
      </section>

      <aside className="login-art" aria-hidden="true">
        <img src={loginBg} alt="" />
      </aside>
    </div>
  );
}
