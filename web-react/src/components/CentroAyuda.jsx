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

  const [copiado, setCopiado] = useState(false);
  const correo = ayuda?.correoSoporte;

  const [asuntoMsg, setAsuntoMsg] = useState('');
  const [mensajeMsg, setMensajeMsg] = useState('');
  const [erroresMsg, setErroresMsg] = useState({});
  const [enviandoMsg, setEnviandoMsg] = useState(false);
  const [enviadoMsg, setEnviadoMsg] = useState(false);

  // Envía el mensaje a soporte (llega al correo de soporte de TechLink).
  async function enviarMensaje(e) {
    e.preventDefault();
    setEnviandoMsg(true);
    setEnviadoMsg(false);
    setErroresMsg({});
    try {
      const response = await authFetch('/api/ayuda/contacto', { method: 'POST', body: JSON.stringify({ asunto: asuntoMsg, mensaje: mensajeMsg }) });
      if (response.status === 204) {
        setAsuntoMsg('');
        setMensajeMsg('');
        setEnviadoMsg(true);
        return;
      }
      const data = await response.json().catch(() => ({}));
      setErroresMsg(data.campos || { general: data.error || 'No se pudo enviar el mensaje.' });
    } catch {
      setErroresMsg({ general: 'No se pudo conectar con el servidor.' });
    } finally {
      setEnviandoMsg(false);
    }
  }

  async function copiarCorreo() {
    try {
      await navigator.clipboard.writeText(correo);
      setCopiado(true);
      setTimeout(() => setCopiado(false), 2000);
    } catch { /* el correo sigue visible para copiarlo a mano */ }
  }

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

      {correo && (
        <form className="co-tarjeta co-contacto" onSubmit={enviarMensaje} noValidate>
          <div>
            <h2 className="co-titulo-tarjeta">¿No encontraste tu respuesta?</h2>
            <p className="co-sub">Escribe a soporte desde aquí: tu mensaje llega a <strong>{correo}</strong> y te respondemos a tu correo.</p>
          </div>
          <label htmlFor="co-asunto">Asunto</label>
          <input id="co-asunto" type="text" maxLength={100} value={asuntoMsg} placeholder="Ej: No puedo agendar una sesión"
            onChange={(e) => setAsuntoMsg(e.target.value)} />
          {erroresMsg.asunto && <p className="co-error" role="alert">{erroresMsg.asunto}</p>}
          <label htmlFor="co-mensaje">¿Qué pasó?</label>
          <textarea id="co-mensaje" rows={5} maxLength={2000} value={mensajeMsg} placeholder="Cuéntanos con detalle qué está mal."
            onChange={(e) => setMensajeMsg(e.target.value)} />
          {erroresMsg.mensaje && <p className="co-error" role="alert">{erroresMsg.mensaje}</p>}
          {erroresMsg.general && <p className="co-error" role="alert">{erroresMsg.general}</p>}
          {enviadoMsg && <p className="co-ok" role="status">¡Mensaje enviado! Soporte te responderá a tu correo.</p>}
          <div className="co-soporte-acciones">
            <button type="submit" className="co-boton primario" disabled={enviandoMsg}>{enviandoMsg ? 'Enviando…' : 'Enviar mensaje'}</button>
            <button type="button" className="co-boton" onClick={copiarCorreo}>{copiado ? '¡Copiado!' : 'Copiar correo de soporte'}</button>
          </div>
        </form>
      )}
    </div>
  );
}
