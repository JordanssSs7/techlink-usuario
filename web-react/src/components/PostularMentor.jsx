import { useEffect, useRef, useState } from 'react';
import './Cuenta.css';
import { useAuth } from '../auth/authContext';
import Avatar from './Avatar';
import { useFotoPropia } from './fotoPerfil';

const MAX_ARCHIVO = 5 * 1024 * 1024;
const TIPOS = ['application/pdf', 'image/png', 'image/jpeg'];
const MAX_COMENTARIO = 500;
const NOTA_MINIMA = 17; // nota mínima, en el curso que se quiere dictar, para postular a mentor

const tamano = (bytes) => (bytes >= 1024 * 1024 ? `${(bytes / (1024 * 1024)).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`);
const fecha = (iso) => (iso ? new Date(iso).toLocaleDateString('es-PE', { day: 'numeric', month: 'long', year: 'numeric' }) : '');

const ESTADOS = {
  EN_REVISION: { titulo: 'En revisión', texto: 'Recibimos tu postulación y tu comprobante. Un administrador la está evaluando; te avisaremos el resultado.' },
  APROBADA: { titulo: 'Aprobada', texto: '¡Felicitaciones! Tu postulación fue aprobada. Cierra sesión y vuelve a entrar para ver tu cuenta de mentor.' },
  RECHAZADA: { titulo: 'Rechazada', texto: 'Tu postulación no fue aprobada. Puedes corregir tu comprobante y postular de nuevo.' }
};

// HU-03: un estudiante de ciclos avanzados postula a ser mentor adjuntando su comprobante de cursos aprobados
// (PDF o imagen, máximo 5 MB). Al enviarla queda «En revisión» y se le avisa que la recibimos.
const PASOS = [
  {
    titulo: 'Entra a tu cuenta de alumno',
    texto: <>Ingresa a <a href="https://sis.tecsup.edu.pe/Alumnos/Login" target="_blank" rel="noopener noreferrer">sis.tecsup.edu.pe</a> con tu usuario de Tecsup.</>
  },
  {
    titulo: 'Abre «Concentración de calificaciones»',
    texto: <>Ve a la sección <strong>Consultas</strong> y elige <strong>Concentración de calificaciones</strong>.</>
  },
  {
    titulo: 'Toma la captura o descarga el PDF',
    texto: <>Busca el período donde aparece el curso que quieres enseñar. Basta con los cursos que vas a dictar, no todos. En cada curso tu nota final debe ser de <strong>{NOTA_MINIMA} o más</strong> (escala de 0 a 20).</>
  },
  {
    titulo: 'Súbelo aquí y envía',
    texto: <>Adjunta la imagen (JPG o PNG) o el PDF, añade un comentario si quieres y pulsa «Enviar postulación».</>
  }
];
const SEGUNDOS_POR_PASO = 6;

// Las indicaciones, como un carrusel: avanza solo cuando se llena la barrita del punto activo (se detiene si pasas el cursor o enfocas algo dentro), se maneja con las
// flechas o con los puntos, y la tarjeta se oscurece al pasar el cursor por encima. Si el sistema pide menos movimiento, no avanza solo.
function PasosCarrusel() {
  const [actual, setActual] = useState(0);
  const [pausa, setPausa] = useState(false);

  const ir = (n) => setActual((n + PASOS.length) % PASOS.length);

  return (
    <div className="cu-carrusel" role="region" aria-roledescription="carrusel" aria-label="¿Cómo postular?"
      onMouseEnter={() => setPausa(true)} onMouseLeave={() => setPausa(false)}
      onFocus={() => setPausa(true)} onBlur={() => setPausa(false)}>
      <div className="cu-car-cab">
        <h3>¿Cómo postular?</h3>
        <span aria-live="polite">Paso {actual + 1} de {PASOS.length}</span>
      </div>
      <div className="cu-car-ventana">
        <div className="cu-car-pista" style={{ transform: `translateX(-${actual * 100}%)` }}>
          {PASOS.map((p, n) => (
            <article key={p.titulo} className={`cu-car-paso${n === actual ? ' activo' : ''}`} inert={n !== actual} aria-label={`Paso ${n + 1} de ${PASOS.length}`}>
              <span className="cu-car-num" aria-hidden="true">{n + 1}</span>
              <div><strong>{p.titulo}</strong><p>{p.texto}</p></div>
            </article>
          ))}
        </div>
      </div>
      <div className="cu-car-ctrl">
        <button type="button" className="cu-car-flecha" onClick={() => ir(actual - 1)} aria-label="Paso anterior">‹</button>
        <div className="cu-car-puntos">
          {PASOS.map((p, n) => (
            <button key={p.titulo} type="button" className={`cu-car-punto${n === actual ? ' activo' : ''}${pausa ? ' quieto' : ''}`}
              style={{ '--cu-segundos': `${SEGUNDOS_POR_PASO}s` }} onClick={() => ir(n)} onAnimationEnd={n === actual ? () => ir(actual + 1) : undefined} aria-label={`Ir al paso ${n + 1}`} aria-current={n === actual}>
              <i aria-hidden="true" />
            </button>
          ))}
        </div>
        <button type="button" className="cu-car-flecha" onClick={() => ir(actual + 1)} aria-label="Paso siguiente">›</button>
      </div>
    </div>
  );
}

export default function PostularMentor() {
  const { authFetch, usuario } = useAuth();
  const fotoPropia = useFotoPropia();
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
  const iniciales = [usuario.nombres, usuario.apellidos].filter((x) => x && x !== '-').map((x) => x.trim()[0]).join('').slice(0, 2).toUpperCase();

  return (
    <div className="cu">
      <section className="cu-tarjeta cu-hero-insignia">
        <Avatar idUsuario={fotoPropia.idUsuario} tieneFoto={fotoPropia.tieneFoto} version={fotoPropia.version}
          iniciales={iniciales} tamano={104} insignia />
        <div>
          <h2 className="cu-titulo-tarjeta">Postula para que obtengas tu insignia</h2>
          <p className="cu-ayuda">
            Cuando un administrador apruebe tu postulación, este aro dorado rodeará tu foto de perfil y todos lo verán: en tu perfil
            público, el ranking y las recomendaciones de mentores.
          </p>
        </div>
      </section>

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
          <PasosCarrusel />
          <p className="cu-importante">
            <strong>Importante:</strong> Solo puedes ser mentor de cursos aprobados con nota {NOTA_MINIMA} o más. La captura debe mostrar tu nombre, el período y la nota. Solo se aceptan imágenes sin editar:
            un administrador las compara con tu cuenta.
          </p>

          <input ref={entrada} type="file" accept="application/pdf,image/png,image/jpeg" className="cu-oculto" onChange={elegir} aria-label="Elegir comprobante" />
          <div className="cu-zona-envoltura">
            <button type="button" className="cu-zona" onClick={() => entrada.current?.click()}>
              {archivo
                ? <><strong>{archivo.name}</strong><span>{tamano(archivo.size)} · toca para cambiarlo</span></>
                : <><strong>Elegir comprobante</strong><span>PDF, PNG o JPG · máximo 5 MB</span></>}
            </button>
            {archivo && (
              <button type="button" className="cu-zona-quitar" aria-label="Quitar el archivo elegido" title="Quitar archivo"
                onClick={() => { setArchivo(null); setError(''); }}>✕</button>
            )}
          </div>

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
