import { useEffect, useRef, useState } from 'react';
import './ReportarModal.css';
import { useAuth } from '../auth/authContext';

const MOTIVOS = [
  { valor: 'CONDUCTA_INADECUADA', texto: 'Conducta inadecuada' },
  { valor: 'ACOSO', texto: 'Acoso' },
  { valor: 'CONTENIDO_OFENSIVO', texto: 'Contenido ofensivo' },
  { valor: 'INCUMPLIMIENTO', texto: 'Incumplimiento (no asistió, llegó tarde…)' },
  { valor: 'SPAM', texto: 'Spam o publicidad' },
  { valor: 'OTRO', texto: 'Otro' }
];
const MIN_DESCRIPCION = 10;
const MAX_DESCRIPCION = 500;

// HU-29: ventana para reportar una sesión (tipo "SESION" con idSesion), a un usuario ("USUARIO" con idUsuario) o algo del foro
// ("CONSULTA" con idConsulta o "RESPUESTA" con idRespuesta).
// Un administrador revisa el reporte desde el panel. Escape o tocar el fondo la cierra.
export default function ReportarModal({ tipo, idSesion, idUsuario, idConsulta, idRespuesta, titulo, subtitulo, onCerrar }) {
  const { authFetch } = useAuth();
  const [motivo, setMotivo] = useState('');
  const [descripcion, setDescripcion] = useState('');
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [enviado, setEnviado] = useState('');
  const caja = useRef(null);
  const cerrarRef = useRef(onCerrar);

  useEffect(() => { cerrarRef.current = onCerrar; });

  // Se enfoca la ventana una sola vez: si dependiera de onCerrar, volvería a robar el foco cada vez que el padre se redibuja.
  useEffect(() => {
    const anterior = document.activeElement;
    caja.current?.focus();
    const escape = (e) => { if (e.key === 'Escape') cerrarRef.current(); };
    document.addEventListener('keydown', escape);
    return () => {
      document.removeEventListener('keydown', escape);
      anterior?.focus?.();
    };
  }, []);

  async function enviar(e) {
    e.preventDefault();
    if (!motivo) {
      setError('Elige el motivo del reporte.');
      return;
    }
    if (descripcion.trim().length < MIN_DESCRIPCION) {
      setError(`Cuéntanos qué pasó con al menos ${MIN_DESCRIPCION} caracteres.`);
      return;
    }
    setEnviando(true);
    setError('');
    try {
      const response = await authFetch('/api/reportes', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          tipo, idSesion: idSesion ?? null, idUsuario: idUsuario ?? null, idConsulta: idConsulta ?? null, idRespuesta: idRespuesta ?? null,
          motivo, descripcion: descripcion.trim()
        })
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        setError(data.error || 'No se pudo enviar el reporte.');
        return;
      }
      setEnviado(data.mensaje || 'Recibimos tu reporte. Un administrador lo revisará.');
    } catch {
      setError('No se pudo conectar con el servidor.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="rp-velo" onMouseDown={(e) => { if (e.target === e.currentTarget) onCerrar(); }}>
      <div className="rp-modal" role="dialog" aria-modal="true" aria-label={titulo} tabIndex={-1} ref={caja}>
        {enviado ? (
          <>
            <h2>Reporte enviado</h2>
            <p className="rp-sub">{enviado}</p>
            <div className="rp-botones">
              <button type="button" className="rp-boton primario" onClick={onCerrar}>Cerrar</button>
            </div>
          </>
        ) : (
          <form onSubmit={enviar}>
            <h2>{titulo}</h2>
            {subtitulo && <p className="rp-sub">{subtitulo}</p>}

            <label htmlFor="rp-motivo">Motivo</label>
            <select id="rp-motivo" value={motivo} onChange={(e) => { setMotivo(e.target.value); setError(''); }}>
              <option value="">Elige un motivo…</option>
              {MOTIVOS.map((m) => <option key={m.valor} value={m.valor}>{m.texto}</option>)}
            </select>

            <label htmlFor="rp-descripcion">¿Qué pasó?</label>
            <textarea
              id="rp-descripcion"
              rows={4}
              maxLength={MAX_DESCRIPCION}
              value={descripcion}
              placeholder="Describe lo que ocurrió con el mayor detalle posible."
              onChange={(e) => { setDescripcion(e.target.value); setError(''); }}
            />
            <p className="rp-contador">{descripcion.length}/{MAX_DESCRIPCION}</p>
            <p className="rp-pista">Solo un administrador verá tu reporte. La otra persona no sabrá quién lo envió.</p>

            {error && <p className="rp-error" role="alert">{error}</p>}
            <div className="rp-botones">
              <button type="button" className="rp-boton" onClick={onCerrar}>Cancelar</button>
              <button type="submit" className="rp-boton primario" disabled={enviando}>{enviando ? 'Enviando…' : 'Enviar reporte'}</button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
