import { useEffect, useState } from 'react';
import './Verificar.css';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const horas = (h) => `${Number.isInteger(h) ? h : String(Number(h.toFixed(2))).replace('.', ',')} horas`;
function fechaLarga(iso) {
  if (!iso) return '';
  const [a, m, d] = iso.split('-').map(Number);
  return new Date(a, m - 1, d).toLocaleDateString('es-PE', { day: 'numeric', month: 'long', year: 'numeric' });
}

function Logo() {
  return (
    <svg width="34" height="34" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M3 3h8l-3 9H3z" fill="currentColor" />
      <path d="M13 3h8v9h-5l-3 9H9z" fill="currentColor" />
    </svg>
  );
}

// HU-27: página pública que comprueba una constancia de mentoría. Se llega escaneando el código QR de la constancia (o con la
// dirección /verificar/<código>). No pide iniciar sesión: muestra si el código existe y los datos de la constancia.
export default function VerificarConstancia({ codigo }) {
  const [resultado, setResultado] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const response = await fetch(`${API_URL}/api/verificar/${encodeURIComponent(codigo)}`);
        if (!response.ok) throw new Error();
        const data = await response.json();
        if (activo) setResultado(data);
      } catch {
        if (activo) setError('No pudimos comprobar la constancia en este momento. Inténtalo de nuevo en unos minutos.');
      }
    })();
    return () => { activo = false; };
  }, [codigo]);

  return (
    <div className="ver">
      <section className="ver-tarjeta" aria-live="polite">
        <div className="ver-marca"><Logo /><span>TechLink</span></div>
        <p className="ver-eyebrow">Verificación de constancia</p>

        {!resultado && !error && <p className="ver-texto">Comprobando el código <strong>{codigo}</strong>…</p>}
        {error && <p className="ver-error" role="alert">{error}</p>}

        {resultado?.valida && (
          <>
            <p className="ver-sello valida">✓ Constancia auténtica</p>
            <h1>{resultado.mentor}</h1>
            <dl className="ver-datos">
              <div><dt>Participó como mentor de</dt><dd>{resultado.curso || 'TechLink'}</dd></div>
              <div><dt>Horas de mentoría reconocidas</dt><dd>{horas(resultado.horas)}</dd></div>
              <div><dt>Fecha de emisión</dt><dd>{fechaLarga(resultado.fechaEmision)}</dd></div>
              <div><dt>Código de verificación</dt><dd>{codigo.toUpperCase()}</dd></div>
            </dl>
            <p className="ver-nota">
              Esta constancia fue emitida por TechLink, la plataforma de mentorías entre estudiantes de Tecsup. No constituye una
              certificación oficial de TECSUP.
            </p>
          </>
        )}

        {resultado && !resultado.valida && (
          <>
            <p className="ver-sello invalida">✕ No encontramos esta constancia</p>
            <p className="ver-texto">
              El código <strong>{codigo}</strong> no corresponde a ninguna constancia emitida por TechLink. Revisa que esté bien
              escrito o que el código QR esté completo. Si crees que es un error, escribe a soporte.techlink.app@gmail.com.
            </p>
          </>
        )}
      </section>
    </div>
  );
}
