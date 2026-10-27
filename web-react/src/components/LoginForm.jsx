import { useState } from 'react';
import './LoginForm.css';
import loginBg from '../assets/login-bg.webp';
import { useAuth } from '../auth/authContext';
import { loginRequest, googleRequest, MOCK_AUTH } from '../auth/authApi';

const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID;
const DOMINIO = '@tecsup.edu.pe';
const EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@tecsup\.edu\.pe$/i;

// Carga el script de Google Identity Services una sola vez
let gsiPromise;
function cargarGoogle() {
  if (!gsiPromise) {
    gsiPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'https://accounts.google.com/gsi/client';
      script.async = true;
      script.onload = resolve;
      script.onerror = () => reject(new Error('No se pudo cargar Google'));
      document.head.appendChild(script);
    });
  }
  return gsiPromise;
}

export default function LoginForm() {
  const { login } = useAuth();
  const [form, setForm] = useState({ correoInstitucional: '', password: '' });
  const [errors, setErrors] = useState({});
  const [showPassword, setShowPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isGoogleLoading, setIsGoogleLoading] = useState(false);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm({ ...form, [name]: value });
    if (errors[name] || errors.global) setErrors({ ...errors, [name]: null, global: null });
  };

  const validate = () => {
    const newErrors = {};
    const correo = form.correoInstitucional.trim();
    if (!correo) newErrors.correoInstitucional = 'Ingresa tu correo institucional.';
    else if (!EMAIL_REGEX.test(correo)) newErrors.correoInstitucional = `Usa tu correo institucional (${DOMINIO}).`;
    if (!form.password) newErrors.password = 'Ingresa tu contraseña.';
    return newErrors;
  };

  // Inicio de sesión con correo y contraseña (HU-02)
  const handleSubmit = async (e) => {
    e.preventDefault();
    const validationErrors = validate();
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }

    setIsSubmitting(true);
    try {
      const { ok, status, data } = await loginRequest(form.correoInstitucional.trim(), form.password);

      if (ok) {
        login(data);
        return;
      }

      if (status === 401) {
        setErrors({ global: data.error || 'Correo o contraseña incorrectos.' });
      } else if (status === 423 || status === 429) {
        setErrors({ global: data.error || 'Cuenta bloqueada temporalmente. Inténtalo en 15 minutos.' });
      } else if (status === 400 && data.campos) {
        setErrors(data.campos);
      } else {
        setErrors({ global: data.error || 'No se pudo iniciar sesión. Inténtalo nuevamente.' });
      }
    } catch {
      setErrors({ global: 'Error al conectar con el servidor de autenticación.' });
    } finally {
      setIsSubmitting(false);
    }
  };

  // OAuth2 (flujo de código de autorización) con Google.
  // El backend recibe el "code", lo canjea por los datos del usuario y valida el dominio.
  const handleGoogle = async () => {
    if (MOCK_AUTH) {
      login((await googleRequest()).data);
      return;
    }
    if (!GOOGLE_CLIENT_ID) {
      setErrors({ global: 'Falta configurar VITE_GOOGLE_CLIENT_ID para iniciar sesión con Google.' });
      return;
    }

    setIsGoogleLoading(true);
    try {
      await cargarGoogle();
      const client = window.google.accounts.oauth2.initCodeClient({
        client_id: GOOGLE_CLIENT_ID,
        scope: 'openid email profile',
        ux_mode: 'popup',
        hd: 'tecsup.edu.pe',
        callback: async ({ code, error }) => {
          if (error || !code) {
            setIsGoogleLoading(false);
            return;
          }
          try {
            const { ok, data } = await googleRequest(code);
            if (ok) {
              login(data);
            } else {
              setErrors({ global: data.error || `Solo se permiten cuentas ${DOMINIO}.` });
            }
          } catch {
            setErrors({ global: 'Error al conectar con el servidor de autenticación.' });
          } finally {
            setIsGoogleLoading(false);
          }
        },
        error_callback: () => setIsGoogleLoading(false)
      });
      client.requestCode();
    } catch {
      setErrors({ global: 'No se pudo cargar Google. Revisa tu conexión.' });
      setIsGoogleLoading(false);
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

          <h1 className="login-title">Iniciar sesión</h1>
          <p className="login-subtitle">Accede a tu panel de mentorías TechLink</p>

          {(
            <>
              <button
                type="button"
                className="login-google"
                onClick={handleGoogle}
                disabled={isGoogleLoading}
              >
                <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true">
                  <path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9.1 3.6l6.8-6.8C35.8 2.4 30.3 0 24 0 14.6 0 6.5 5.4 2.6 13.2l7.9 6.1C12.4 13.6 17.7 9.5 24 9.5z" />
                  <path fill="#4285F4" d="M46.5 24.5c0-1.6-.1-3.1-.4-4.5H24v9h12.7c-.6 3-2.3 5.5-4.8 7.2l7.5 5.8c4.4-4.1 7.1-10.1 7.1-17.5z" />
                  <path fill="#FBBC05" d="M10.5 28.7c-.5-1.5-.8-3-.8-4.7s.3-3.2.8-4.7l-7.9-6.1C.9 16.4 0 20.1 0 24s.9 7.6 2.6 10.8l7.9-6.1z" />
                  <path fill="#34A853" d="M24 48c6.5 0 11.9-2.1 15.9-5.8l-7.5-5.8c-2.1 1.4-4.8 2.3-8.4 2.3-6.3 0-11.6-4.1-13.5-9.8l-7.9 6.1C6.5 42.6 14.6 48 24 48z" />
                </svg>
                {isGoogleLoading ? 'Conectando...' : 'Iniciar sesión con Google'}
              </button>

              <div className="login-divider"><span>O</span></div>

              <form onSubmit={handleSubmit} noValidate>
                {errors.global && <div className="login-alert" role="alert">{errors.global}</div>}

                <div className="login-field">
                  <label htmlFor="correoInstitucional">Correo</label>
                  <input
                    id="correoInstitucional"
                    name="correoInstitucional"
                    type="email"
                    autoComplete="username"
                    placeholder={`usuario${DOMINIO}`}
                    value={form.correoInstitucional}
                    onChange={handleChange}
                    aria-invalid={!!errors.correoInstitucional}
                  />
                  {errors.correoInstitucional && <span className="login-error">{errors.correoInstitucional}</span>}
                </div>

                <div className="login-field">
                  <div className="login-label-row">
                    <label htmlFor="password">Contraseña</label>
                    <a href="#recuperar" className="login-forgot">¿Olvidaste tu contraseña?</a>
                  </div>
                  <div className="login-password">
                    <input
                      id="password"
                      name="password"
                      type={showPassword ? 'text' : 'password'}
                      autoComplete="current-password"
                      placeholder="Ingresa tu contraseña"
                      value={form.password}
                      onChange={handleChange}
                      aria-invalid={!!errors.password}
                    />
                    <button
                      type="button"
                      className="login-eye"
                      onClick={() => setShowPassword((v) => !v)}
                      aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                      aria-pressed={showPassword}
                    >
                      {showPassword ? 'Ocultar' : 'Ver'}
                    </button>
                  </div>
                  {errors.password && <span className="login-error">{errors.password}</span>}
                </div>

                <button type="submit" className="login-submit" disabled={isSubmitting}>
                  {isSubmitting ? 'Verificando...' : 'Iniciar sesión'}
                </button>
              </form>

             
            </>
          )}
        </div>
      </section>

      <aside className="login-art" aria-hidden="true">
        <img src={loginBg} alt="" />
      </aside>
    </div>
  );
}
