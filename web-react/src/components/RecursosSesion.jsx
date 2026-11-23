import { useEffect, useRef, useState } from 'react';
import './Comunidad.css';
import { useAuth } from '../auth/authContext';

const MAX_ARCHIVO = 10 * 1024 * 1024;
const TIPOS = ['application/pdf', 'image/png', 'image/jpeg'];
const ICONOS = { ARCHIVO: '📄', ENLACE: '🔗', CODIGO: '💻' };

const tamano = (b) => (b >= 1024 * 1024 ? `${(b / (1024 * 1024)).toFixed(1)} MB` : `${Math.max(1, Math.round(b / 1024))} KB`);

// Una parte de lo compartido: la imagen, o la primera página del PDF. El archivo se pide con la sesión iniciada (no se puede poner en un <img>
// directamente) y se muestra desde una dirección temporal del navegador.
function VistaPrevia({ recurso }) {
  const { authFetch } = useAuth();
  const [url, setUrl] = useState(null);
  const [abierta, setAbierta] = useState(false); // la vista previa empieza chica y se abre con «Ver más»
  const tipo = recurso.tipoContenido || '';
  const esImagen = tipo.startsWith('image/');
  const esPdf = tipo === 'application/pdf';

  useEffect(() => {
    if (!esImagen && !esPdf) return undefined;
    let activo = true;
    let direccion = null;
    (async () => {
      try {
        const response = await authFetch(`/api/recursos/${recurso.id}/archivo`);
        if (!response.ok || !activo) return;
        direccion = URL.createObjectURL(await response.blob());
        if (activo) setUrl(direccion);
        else URL.revokeObjectURL(direccion);
      } catch { /* sin vista previa: queda el botón de descargar */ }
    })();
    return () => {
      activo = false;
      if (direccion) URL.revokeObjectURL(direccion);
    };
  }, [authFetch, recurso.id, esImagen, esPdf]);

  if (!esImagen && !esPdf) return null;
  if (!url) return <div className="co-vista cargando" aria-hidden="true" />;
  return (
    <div className={`co-vista${abierta ? ' abierta' : ''}`}>
      <div className="co-vista-marco">
        {esImagen
          ? <img src={url} alt={`Vista previa de ${recurso.titulo}`} loading="lazy" />
          : <iframe src={`${url}#page=1&view=FitH&toolbar=0&navpanes=0`} title={`Primera página de ${recurso.titulo}`} tabIndex={-1} />}
      </div>
      <div className="co-vista-barra">
        <button type="button" className="co-vista-mas" aria-expanded={abierta} onClick={() => setAbierta((v) => !v)}>
          {abierta ? 'Ver menos' : 'Ver más'}
        </button>
        <a className="co-vista-abrir" href={url} target="_blank" rel="noopener noreferrer">{esImagen ? 'Abrir imagen' : 'Abrir PDF'}</a>
      </div>
    </div>
  );
}

// HU-18: los recursos que el mentor comparte en una sesión (archivo PDF o imagen de hasta 10 MB, enlace o código).
// El estudiante los ve y los descarga; el mentor además los agrega y los quita.
export default function RecursosSesion({ sesion, esMentor, onCambio }) {
  const { authFetch } = useAuth();
  const [recursos, setRecursos] = useState(null);
  const [error, setError] = useState('');
  const [version, setVersion] = useState(0);
  const [agregando, setAgregando] = useState(false);
  const [quitar, setQuitar] = useState(null);
  const [copiado, setCopiado] = useState(null);

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const response = await authFetch(`/api/sesiones/${sesion.id}/recursos`);
        const data = await response.json().catch(() => null);
        if (!response.ok) throw new Error(data?.error || '');
        if (!activo) return;
        setRecursos(data);
        setError('');
      } catch (e) {
        if (activo) setError(e.message || 'No se pudieron cargar los recursos.');
      }
    })();
    return () => { activo = false; };
  }, [authFetch, sesion.id, version]);

  const recargar = () => { setVersion((v) => v + 1); onCambio?.(); };

  async function descargar(r) {
    setError('');
    try {
      const response = await authFetch(`/api/recursos/${r.id}/archivo`);
      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        throw new Error(data.error || 'No se pudo descargar el archivo.');
      }
      const url = URL.createObjectURL(await response.blob());
      const enlace = document.createElement('a');
      enlace.href = url;
      enlace.download = r.nombreArchivo || 'recurso';
      document.body.appendChild(enlace);
      enlace.click();
      enlace.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) {
      setError(e.message);
    }
  }

  async function eliminar(r) {
    setError('');
    try {
      const response = await authFetch(`/api/recursos/${r.id}`, { method: 'DELETE' });
      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        throw new Error(data.error || 'No se pudo quitar el recurso.');
      }
      setQuitar(null);
      recargar();
    } catch (e) {
      setError(e.message);
    }
  }

  async function copiar(r) {
    try {
      await navigator.clipboard.writeText(r.codigo);
      setCopiado(r.id);
      setTimeout(() => setCopiado(null), 1500);
    } catch { /* el navegador no dejó copiar: se puede seleccionar a mano */ }
  }

  return (
    <div className="co co-recursos">
      <h2 className="co-titulo-tarjeta">Recursos de la sesión</h2>
      <p className="co-sub">{sesion.curso || 'Mentoría'} con {sesion.contraparte}</p>

      {error && <p className="co-error" role="alert">{error}</p>}
      {!recursos && !error && <p className="co-vacio">Cargando recursos…</p>}
      {recursos?.length === 0 && (
        <p className="co-vacio">{esMentor ? 'Todavía no compartiste recursos en esta sesión.' : 'Tu mentor todavía no compartió recursos.'}</p>
      )}

      <ul className="co-lista">
        {recursos?.map((r) => (
          <li key={r.id} className={`co-recurso${r.oculto ? ' oculto' : ''}`}>
            <div className="co-recurso-cab">
              <span aria-hidden="true">{ICONOS[r.tipo]}</span>
              <h3>{r.titulo}</h3>
              {r.tipo === 'CODIGO' && r.lenguaje && <span className="co-etiqueta curso">{r.lenguaje}</span>}
            </div>
            {r.oculto && (
              <p className="co-aviso-oculto" role="status">
                Un administrador ocultó este recurso{r.motivoModeracion ? `: ${r.motivoModeracion}` : '.'} Tus estudiantes no lo ven.
              </p>
            )}
            {r.descripcion && <p className="co-texto">{r.descripcion}</p>}
            {r.tipo === 'ARCHIVO' && !r.oculto && <VistaPrevia recurso={r} />}

            {r.tipo === 'CODIGO' && (
              <div className="co-codigo">
                <pre><code>{r.codigo}</code></pre>
                <button type="button" className="co-boton chico" onClick={() => copiar(r)}>{copiado === r.id ? '¡Copiado!' : 'Copiar'}</button>
              </div>
            )}
            <div className="co-acciones izq">
              {r.tipo === 'ENLACE' && (
                <a className="co-boton primario chico" href={r.url} target="_blank" rel="noopener noreferrer">Abrir enlace</a>
              )}
              {r.tipo === 'ARCHIVO' && (
                <button type="button" className="co-boton primario chico" onClick={() => descargar(r)}>
                  Descargar{r.tamanoBytes ? ` (${tamano(r.tamanoBytes)})` : ''}
                </button>
              )}
              {r.puedeEliminar && (quitar === r.id ? (
                <>
                  <span className="co-sub">¿Quitar este recurso?</span>
                  <button type="button" className="co-boton chico peligro" onClick={() => eliminar(r)}>Sí, quitar</button>
                  <button type="button" className="co-boton chico" onClick={() => setQuitar(null)}>No</button>
                </>
              ) : (
                <button type="button" className="co-boton chico" onClick={() => setQuitar(r.id)}>Quitar</button>
              ))}
            </div>
          </li>
        ))}
      </ul>

      {esMentor && (agregando
        ? <FormRecurso sesionId={sesion.id} onListo={() => { setAgregando(false); recargar(); }} onCancelar={() => setAgregando(false)} />
        : <div className="co-acciones"><button type="button" className="co-boton primario" onClick={() => setAgregando(true)}>Compartir un recurso</button></div>)}
    </div>
  );
}

// El mentor elige qué compartir: un archivo, un enlace o un fragmento de código.
function FormRecurso({ sesionId, onListo, onCancelar }) {
  const { authFetch } = useAuth();
  const [tipo, setTipo] = useState('ENLACE');
  const [titulo, setTitulo] = useState('');
  const [descripcion, setDescripcion] = useState('');
  const [url, setUrl] = useState('');
  const [lenguaje, setLenguaje] = useState('');
  const [codigo, setCodigo] = useState('');
  const [archivo, setArchivo] = useState(null);
  const [errores, setErrores] = useState({});
  const [enviando, setEnviando] = useState(false);
  const entrada = useRef(null);

  const elegir = (e) => {
    const elegido = e.target.files?.[0];
    e.target.value = '';
    if (!elegido) return;
    if (!TIPOS.includes(elegido.type)) {
      setErrores({ archivo: 'El archivo debe ser un PDF, PNG o JPG.' });
      return;
    }
    if (elegido.size > MAX_ARCHIVO) {
      setErrores({ archivo: 'El archivo no puede pesar más de 10 MB.' });
      return;
    }
    setErrores({});
    setArchivo(elegido);
  };

  async function enviar(e) {
    e.preventDefault();
    setErrores({});
    // Al compartir un archivo sin escribir título, se usa el nombre del archivo.
    let tituloFinal = titulo.trim();
    if (tipo === 'ARCHIVO' && !tituloFinal && archivo) {
      tituloFinal = archivo.name.replace(/\.[^.]+$/, '').replace(/[_-]+/g, ' ').trim().slice(0, 150);
      if (tituloFinal.length < 3) tituloFinal = 'Archivo compartido';
      setTitulo(tituloFinal);
    }
    if (tituloFinal.length < 3) {
      setErrores({ titulo: 'Escribe un título de al menos 3 letras.' });
      document.getElementById('rc-titulo')?.focus();
      return;
    }
    setEnviando(true);
    try {
      let response;
      if (tipo === 'ARCHIVO') {
        if (!archivo) {
          setErrores({ archivo: 'Elige el archivo que quieres compartir.' });
          return;
        }
        const formulario = new FormData();
        formulario.append('archivo', archivo);
        formulario.append('titulo', tituloFinal);
        if (descripcion.trim()) formulario.append('descripcion', descripcion);
        response = await authFetch(`/api/sesiones/${sesionId}/recursos/archivo`, { method: 'POST', body: formulario });
      } else if (tipo === 'ENLACE') {
        response = await authFetch(`/api/sesiones/${sesionId}/recursos/enlace`, {
          method: 'POST', body: JSON.stringify({ titulo: tituloFinal, descripcion, url })
        });
      } else {
        response = await authFetch(`/api/sesiones/${sesionId}/recursos/codigo`, {
          method: 'POST', body: JSON.stringify({ titulo: tituloFinal, descripcion, lenguaje, codigo })
        });
      }
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        setErrores(data.campos || { general: data.error || 'No se pudo compartir el recurso.' });
        return;
      }
      onListo();
    } catch {
      setErrores({ general: 'No se pudo conectar con el servidor.' });
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form className="co-tarjeta" onSubmit={enviar} noValidate>
      <div className="co-pestanas" role="tablist" aria-label="Tipo de recurso">
        {[['ENLACE', 'Enlace'], ['ARCHIVO', 'Archivo'], ['CODIGO', 'Código']].map(([valor, texto]) => (
          <button key={valor} type="button" role="tab" aria-selected={tipo === valor}
            className={tipo === valor ? 'activo' : ''} onClick={() => { setTipo(valor); setErrores({}); }}>{texto}</button>
        ))}
      </div>
      {errores.general && <p className="co-error" role="alert">{errores.general}</p>}

      <label htmlFor="rc-titulo">Título{tipo === 'ARCHIVO' ? ' (si lo dejas vacío, se usa el nombre del archivo)' : ''}</label>
      <input id="rc-titulo" type="text" maxLength={150} value={titulo} aria-invalid={Boolean(errores.titulo)}
        placeholder="Ej: Guía de repaso de JOIN" onChange={(e) => setTitulo(e.target.value)} />
      {errores.titulo && <p className="co-campo-error" role="alert">{errores.titulo}</p>}

      {tipo === 'ENLACE' && (
        <>
          <label htmlFor="rc-url">Enlace</label>
          <input id="rc-url" type="text" inputMode="url" maxLength={500} value={url} aria-invalid={Boolean(errores.url)}
            placeholder="https://…" onChange={(e) => setUrl(e.target.value)} />
          {errores.url && <p className="co-campo-error" role="alert">{errores.url}</p>}
        </>
      )}

      {tipo === 'ARCHIVO' && (
        <>
          <input ref={entrada} type="file" accept="application/pdf,image/png,image/jpeg" className="co-oculto" onChange={elegir} aria-label="Elegir archivo" />
          <button type="button" className="co-zona" onClick={() => entrada.current?.click()}>
            {archivo
              ? <><strong>{archivo.name}</strong><span>{tamano(archivo.size)} · toca para cambiarlo</span></>
              : <><strong>Elegir archivo</strong><span>PDF, PNG o JPG · máximo 10 MB</span></>}
          </button>
          {errores.archivo && <p className="co-campo-error" role="alert">{errores.archivo}</p>}
        </>
      )}

      {tipo === 'CODIGO' && (
        <>
          <label htmlFor="rc-lenguaje">Lenguaje (opcional)</label>
          <input id="rc-lenguaje" type="text" maxLength={30} value={lenguaje} placeholder="Ej: SQL, Java, JavaScript"
            onChange={(e) => setLenguaje(e.target.value)} />
          <label htmlFor="rc-codigo">Código</label>
          <textarea id="rc-codigo" className="co-mono" rows={7} maxLength={5000} value={codigo} aria-invalid={Boolean(errores.codigo)}
            placeholder="Pega aquí el código" onChange={(e) => setCodigo(e.target.value)} />
          {errores.codigo && <p className="co-campo-error" role="alert">{errores.codigo}</p>}
        </>
      )}

      <label htmlFor="rc-desc">Descripción (opcional)</label>
      <input id="rc-desc" type="text" maxLength={300} value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
      {errores.descripcion && <p className="co-campo-error" role="alert">{errores.descripcion}</p>}

      <div className="co-acciones">
        <button type="button" className="co-boton" onClick={onCancelar}>Cancelar</button>
        <button type="submit" className="co-boton primario" disabled={enviando}>{enviando ? 'Compartiendo…' : 'Compartir'}</button>
      </div>
    </form>
  );
}
