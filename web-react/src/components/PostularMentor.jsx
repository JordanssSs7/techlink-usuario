import { useEffect, useRef, useState } from 'react';
import './Cuenta.css';
import { useAuth } from '../auth/authContext';

const MAX_ARCHIVO = 5 * 1024 * 1024;
const TIPOS = ['application/pdf', 'image/png', 'image/jpeg'];
const MAX_COMENTARIO = 500;

const tamano = (bytes) => (bytes >= 1024 * 1024 ? `${(bytes / (1024 * 1024)).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`);
const fecha = (iso) => (iso ? new Date(iso).toLocaleDateString('es-PE', { day: 'numeric', month: 'long', year: 'numeric' }) : '');

const ESTADOS = {
  EN_REVISION: { titulo: 'En revisión', texto: 'Recibimos tu postulación y tu comprobante. Un administrador la está evaluando; te avisaremos el resultado.' },
  APROBADA: { titulo: 'Aprobada', texto: '¡Felicitaciones! Tu postulación fue aprobada. Cierra sesión y vuelve a entrar para ver tu cuenta de mentor.' },
  RECHAZADA: { titulo: 'Rechazada', texto: 'Tu postulación no fue aprobada. Puedes corregir tu comprobante y postular de nuevo.' }
};

// HU-03: un estudiante de ciclos avanzados postula a ser mentor adjuntando su comprobante de cursos aprobados
// (PDF o imagen, máximo 5 MB). Al enviarla queda «En revisión» y se le avisa que la recibimos.
export default function PostularMentor() {
  const { authFetch } = useAuth();
  const [estado, setEstado] = useState(null);
  const [archivo, setArchivo] = useState(null);
  const [comentario, setComentario] = useState('');
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [exito, setExito] = useState(false);
  const entrada = useRef(null);

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const response = await authFetch('/api/postulaciones/mia');
        const data = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(data.error || '');
        if (activo) setEstado(data);
      } catch (e) {
        if (activo) setError(e.message || 'No se pudo cargar tu postulación.');
      }
    })();
    return () => { activo = false; };
  }, [authFetch]);

  const elegir = (e) => {
    const elegido = e.target.files?.[0];
    e.target.value = '';
    if (!elegido) return;
    if (!TIPOS.includes(elegido.type)) {
      setError('El comprobante debe ser un PDF, PNG o JPG.');
      return;
    }
    if (elegido.size > MAX_ARCHIVO) {
      setError('El comprobante no puede pesar más de 5 MB.');
      return;
    }
    setError('');
    setArchivo(elegido);
  };

  async function enviar(e) {
    e.preventDefault();
    if (!archivo) {
      setError('Adjunta tu comprobante de cursos aprobados.');
      return;
    }
    setEnviando(true);
    setError('');
    try {
      const formulario = new FormData();
      formulario.append('archivo', archivo);
      if (comentario.trim()) formulario.append('comentario', comentario.trim());
      const response = await authFetch('/api/postulaciones', { method: 'POST', body: formulario });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        setError(data.campos?.archivo || data.campos?.comentario || data.error || 'No se pudo enviar la postulación.');
        return;
      }
      setEstado(data);
      setArchivo(null);
      setComentario('');
      setExito(true);
    } catch {
      setError('No se pudo conectar con el servidor.');
    } finally {
      setEnviando(false);
    }
  }

  if (!estado) return error ? <p className="cu-error" role="alert">{error}</p> : <p className="cu-vacio">Cargando…</p>;

  const info = ESTADOS[estado.estado];

  return (
    <div className="cu">
      {exito && (
        <p className="cu-aviso" role="status">
          ¡Postulación enviada! Su estado es «En revisión». Te enviamos un correo para confirmar que la recibimos.
        </p>
      )}

      {info && (
        <section className={`cu-tarjeta cu-estado ${estado.estado.toLowerCase()}`}>
          <span className="cu-insignia-estado">{info.titulo}</span>
          <p>{info.texto}</p>
          <p className="cu-sub">
            Enviada el {fecha(estado.fechaEnvio) || 'hoy'}{estado.nombreArchivo ? ` · ${estado.nombreArchivo}` : ''}
          </p>
          {estado.estado === 'RECHAZADA' && estado.motivoRevision && (
            <p className="cu-motivo"><strong>Motivo:</strong> {estado.motivoRevision}</p>
          )}
        </section>
      )}

      {!estado.puedePostular && estado.mensaje && !info && <p className="cu-aviso" role="status">{estado.mensaje}</p>}

      {estado.puedePostular && (
        <form className="cu-tarjeta" onSubmit={enviar} noValidate>
          <h2 className="cu-titulo-tarjeta">Postula a la insignia de Mentor</h2>
          <p className="cu-ayuda">
            Adjunta el comprobante de tus cursos aprobados, sacado de tu cuenta de Tecsup. Un administrador lo revisará
            para acreditarte como mentor.
          </p>
          <div className="cu-pasos">
            <strong>Cómo obtener tu comprobante</strong>
            <ol>
              <li>
                Entra a tu cuenta de alumno en{' '}
                <a href="https://sis.tecsup.edu.pe/Alumnos/Login" target="_blank" rel="noopener noreferrer">sis.tecsup.edu.pe</a>.
              </li>
              <li>Ve a la sección <strong>Consultas</strong> y elige <strong>Concentración de calificaciones</strong>.</li>
              <li>Busca el período donde aparece el curso que quieres enseñar y toma una captura de pantalla (o descarga el PDF).</li>
              <li>Sube la captura en JPG o PNG, o el PDF.</li>
            </ol>
            <p>
              Basta con las notas de los cursos que vas a dictar como mentor, no de todos. La captura debe mostrar tu nombre, el
              período y la nota. Solo se aceptan imágenes sin editar: un administrador las compara con tu cuenta.
            </p>
          </div>

          <input ref={entrada} type="file" accept="application/pdf,image/png,image/jpeg" className="cu-oculto" onChange={elegir} aria-label="Elegir comprobante" />
          <button type="button" className="cu-zona" onClick={() => entrada.current?.click()}>
            {archivo
              ? <><strong>{archivo.name}</strong><span>{tamano(archivo.size)} · toca para cambiarlo</span></>
              : <><strong>Elegir comprobante</strong><span>PDF, PNG o JPG · máximo 5 MB</span></>}
          </button>

          <label htmlFor="po-comentario">Comentario (opcional)</label>
          <textarea
            id="po-comentario"
            rows={3}
            maxLength={MAX_COMENTARIO}
            value={comentario}
            placeholder="Algo que quieras decirle a quien revise tu postulación."
            onChange={(e) => setComentario(e.target.value)}
          />
          <p className="cu-contador">{comentario.length}/{MAX_COMENTARIO}</p>

          {error && <p className="cu-error" role="alert">{error}</p>}
          <div className="cu-acciones">
            <button type="submit" className="cu-boton primario" disabled={enviando}>{enviando ? 'Enviando…' : 'Enviar postulación'}</button>
          </div>
        </form>
      )}

      {!estado.puedePostular && estado.mensaje && info && estado.estado !== 'EN_REVISION' && estado.estado !== 'APROBADA' && (
        <p className="cu-aviso" role="status">{estado.mensaje}</p>
      )}
      {error && !estado.puedePostular && <p className="cu-error" role="alert">{error}</p>}
    </div>
  );
}
