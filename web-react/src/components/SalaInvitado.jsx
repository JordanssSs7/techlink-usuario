import { lazy, Suspense, useMemo, useState } from 'react';
import './SalaInvitado.css';

// La sala la lleva el mismo componente de las sesiones; se baja solo cuando alguien entra.
const Sala = lazy(() => import('./sala/Sala'));

const PASOS = [
  { icono: <><path d="M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3z" /><path d="M6 11a6 6 0 0 0 12 0M12 17v4" /></>, titulo: 'Habla con tu micrófono', texto: 'Puedes encender también la cámara o compartir tu pantalla.' },
  { icono: <><path d="M8 13V6a1.5 1.5 0 0 1 3 0v5M11 11V4.5a1.5 1.5 0 0 1 3 0V11M14 11V6a1.5 1.5 0 0 1 3 0v8a6 6 0 0 1-6 6h-1a6 6 0 0 1-5-3l-2-3a1.5 1.5 0 0 1 2.4-1.7L8 15" /></>, titulo: 'Los asistentes levantan la mano', texto: 'Verás quién quiere preguntar en el panel «Personas».' },
  { icono: <><path d="m5 12 4.5 4.5L19 7" /></>, titulo: 'Tú das la palabra', texto: 'Pulsa «Dar la palabra» y esa persona sabrá que puede hablar.' }
];

function Icono({ children }) {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {children}
    </svg>
  );
}

function Marca() {
  return (
    <div className="si-marca">
      <svg width="26" height="26" viewBox="0 0 32 32" aria-hidden="true"><path fill="currentColor" d="M3 5h19l-3.5 6H14v16h-6.5V11H3zM22 13h7l-5 14h-7z" /></svg>
      <strong>TechLink</strong>
      <span>Charla virtual</span>
    </div>
  );
}

// Página pública del invitado de una charla (/sala/invitado/<enlace secreto>). No pide cuenta ni contraseña: el enlace que le mandó el
// administrador es su permiso. Escribe su nombre, entra a la sala y da su charla; los asistentes levantan la mano para preguntar.
export default function SalaInvitado({ token }) {
  const [nombre, setNombre] = useState('');
  const [entrando, setEntrando] = useState(false);
  const [terminada, setTerminada] = useState(false);
  const [error, setError] = useState('');
  const invitado = useMemo(() => (entrando ? { token, nombre: nombre.trim() } : null), [entrando, token, nombre]);

  function entrar(e) {
    e.preventDefault();
    if (nombre.trim().length < 2) {
      setError('Escribe tu nombre para que los asistentes sepan quién habla.');
      return;
    }
    setError('');
    setTerminada(false);
    setEntrando(true);
  }

  if (entrando) {
    return (
      <Suspense fallback={null}>
        <Sala tipo="charla" id={0} invitado={invitado} onCerrar={() => { setEntrando(false); setTerminada(true); }} />
      </Suspense>
    );
  }

  if (terminada) {
    return (
      <section className="si">
        <div className="si-tarjeta centrada">
          <span className="si-exito" aria-hidden="true"><Icono><path d="m5 12 4.5 4.5L19 7" /></Icono></span>
          <h1>¡Gracias por tu charla!</h1>
          <p>Saliste de la sala. Los asistentes ya pueden seguir con su día, y tú puedes cerrar esta pestaña.</p>
          <button type="button" className="si-boton secundario" onClick={() => setTerminada(false)}>Volver a entrar</button>
        </div>
      </section>
    );
  }

  return (
    <section className="si">
      <form className="si-tarjeta" onSubmit={entrar} noValidate>
        <Marca />
        <h1>Bienvenido, invitado</h1>
        <p className="si-lead">Vas a dar tu charla desde la sala de TechLink. Así funciona:</p>
        <ul className="si-pasos">
          {PASOS.map((p) => (
            <li key={p.titulo}>
              <span className="si-paso-icono"><Icono>{p.icono}</Icono></span>
              <span><strong>{p.titulo}</strong><small>{p.texto}</small></span>
            </li>
          ))}
        </ul>
        <label htmlFor="si-nombre">¿Cómo te presentamos?</label>
        <input id="si-nombre" value={nombre} maxLength={60} autoComplete="name" onChange={(e) => setNombre(e.target.value)} placeholder="Tu nombre y apellido" aria-invalid={Boolean(error)} />
        {error && <p className="si-error" role="alert">{error}</p>}
        <button type="submit" className="si-boton">Entrar a la sala</button>
      </form>
    </section>
  );
}
