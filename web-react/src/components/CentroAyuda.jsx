import { useEffect, useState } from 'react';
import './Comunidad.css';
import { useAuth } from '../auth/authContext';

// HU-30: centro de ayuda. Preguntas frecuentes agrupadas por tema, con búsqueda por palabra clave y un enlace para
// escribir a soporte si no se encuentra la respuesta.
export default function CentroAyuda() {
  const { authFetch } = useAuth();
  const [busqueda, setBusqueda] = useState('');
  const [ayuda, setAyuda] = useState(null);
  const [error, setError] = useState('');

  // Espera un momento después de escribir para no pedir una búsqueda por cada letra.
  useEffect(() => {
    let activo = true;
    const espera = setTimeout(async () => {
      try {
        const q = busqueda.trim();
        const response = await authFetch(`/api/ayuda${q ? `?q=${encodeURIComponent(q)}` : ''}`);
        const data = await response.json().catch(() => null);
        if (!response.ok) throw new Error(data?.error || '');
        if (!activo) return;
        setAyuda(data);
        setError('');
      } catch (e) {
        if (activo) setError(e.message || 'No se pudo cargar el centro de ayuda.');
      }
    }, busqueda ? 300 : 0);
    return () => { activo = false; clearTimeout(espera); };
  }, [authFetch, busqueda]);

  const correo = ayuda?.correoSoporte;
  const enlaceSoporte = correo
    ? `mailto:${correo}?subject=${encodeURIComponent('Ayuda con TechLink')}${busqueda.trim() ? `&body=${encodeURIComponent(`Hola, no encontré respuesta sobre: ${busqueda.trim()}`)}` : ''}`
    : null;

  return (
    <div className="co">
      <div className="co-buscador">
        <input
          type="search"
          aria-label="Buscar en el centro de ayuda"
          placeholder="¿Qué necesitas saber? Por ejemplo: cancelar, foto, insignias…"
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
        />
      </div>

      {error && <p className="co-error" role="alert">{error}</p>}
      {!ayuda && !error && <p className="co-vacio">Cargando…</p>}

      {ayuda && (
        <p className="co-sub" aria-live="polite">
          {ayuda.busqueda
            ? `${ayuda.total} ${ayuda.total === 1 ? 'resultado' : 'resultados'} para «${ayuda.busqueda}»`
            : `${ayuda.total} preguntas frecuentes`}
        </p>
      )}

      {ayuda?.temas.map((tema) => (
        <section key={tema.id} className="co-tarjeta">
          <h2 className="co-titulo-tarjeta">{tema.nombre}</h2>
          <div className="co-preguntas">
            {tema.preguntas.map((p) => (
              <details key={p.id} className="co-pregunta" open={Boolean(ayuda.busqueda)}>
                <summary>{p.pregunta}</summary>
                <p>{p.respuesta}</p>
              </details>
            ))}
          </div>
        </section>
      ))}

      {ayuda && ayuda.total === 0 && (
        <p className="co-vacio">No encontramos nada con esas palabras. Prueba con otra, o escríbenos.</p>
      )}

      {enlaceSoporte && (
        <section className="co-tarjeta co-soporte">
          <div>
            <h2 className="co-titulo-tarjeta">¿No encontraste tu respuesta?</h2>
            <p className="co-sub">Escríbenos y te ayudamos.</p>
          </div>
          <a className="co-boton primario" href={enlaceSoporte}>Contactar a soporte</a>
        </section>
      )}
    </div>
  );
}
