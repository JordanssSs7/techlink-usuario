import { useEffect, useState } from 'react';
import './Comunidad.css';
import { useAuth } from '../auth/authContext';
import AvisoLlama from './AvisoLlama';
import ReportarModal from './ReportarModal';

const MAX_TITULO = 150;
const MAX_CONTENIDO = 2000;

const fechaHora = (iso) => {
  if (!iso) return '';
  const f = new Date(iso);
  return `${f.toLocaleDateString('es-PE', { day: 'numeric', month: 'short', year: 'numeric' }).replace('.', '')} · ${f.toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' })}`;
};

function Autor({ autor, fecha }) {
  return (
    <span className="co-autor">
      <strong>{autor.nombre}</strong>
      {autor.rol === 'MENTOR' && <span className="co-etiqueta mentor">Mentor</span>}
      <span className="co-sub">{fechaHora(fecha)}</span>
    </span>
  );
}

// HU-17: formulario para publicar una consulta asociada a un curso.
function NuevaConsulta({ cursos, onCreada, onCancelar }) {
  const { authFetch } = useAuth();
  const [cursoId, setCursoId] = useState('');
  const [titulo, setTitulo] = useState('');
  const [contenido, setContenido] = useState('');
  const [errores, setErrores] = useState({});
  const [enviando, setEnviando] = useState(false);
  const [avisoLlama, setAvisoLlama] = useState(''); // moderación: el servidor no dejó publicar el texto (422)

  async function enviar(e) {
    e.preventDefault();
    setEnviando(true);
    setErrores({});
    try {
      const response = await authFetch('/api/foro/consultas', {
        method: 'POST',
        body: JSON.stringify({ cursoId: cursoId ? Number(cursoId) : null, titulo, contenido })
      });
      const data = await response.json().catch(() => ({}));
      if (response.status === 422) {
        setAvisoLlama(data.error);
        return;
      }
      if (!response.ok) {
        setErrores(data.campos || { general: data.error || 'No se pudo publicar tu consulta.' });
        return;
      }
      onCreada(data);
    } catch {
      setErrores({ general: 'No se pudo conectar con el servidor.' });
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form className="co-tarjeta" onSubmit={enviar} noValidate>
      <h2 className="co-titulo-tarjeta">Nueva consulta</h2>
      {errores.general && <p className="co-error" role="alert">{errores.general}</p>}
      {avisoLlama && <AvisoLlama mensaje={avisoLlama} onCerrar={() => setAvisoLlama('')} />}

      <label htmlFor="fo-curso">Curso</label>
      <select id="fo-curso" value={cursoId} aria-invalid={Boolean(errores.cursoId)} onChange={(e) => setCursoId(e.target.value)}>
        <option value="">Elige el curso de tu duda</option>
        {cursos.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
      </select>
      {errores.cursoId && <p className="co-campo-error" role="alert">{errores.cursoId}</p>}

      <label htmlFor="fo-titulo">Título</label>
      <input id="fo-titulo" type="text" maxLength={MAX_TITULO} value={titulo} aria-invalid={Boolean(errores.titulo)}
        placeholder="Resume tu duda en una frase" onChange={(e) => setTitulo(e.target.value)} />
      {errores.titulo && <p className="co-campo-error" role="alert">{errores.titulo}</p>}

      <label htmlFor="fo-contenido">Tu duda</label>
      <textarea id="fo-contenido" rows={6} maxLength={MAX_CONTENIDO} value={contenido} aria-invalid={Boolean(errores.contenido)}
        placeholder="Cuéntanos qué intentaste y dónde te trabas." onChange={(e) => setContenido(e.target.value)} />
      <p className="co-contador">{contenido.length}/{MAX_CONTENIDO}</p>
      {errores.contenido && <p className="co-campo-error" role="alert">{errores.contenido}</p>}

      <div className="co-acciones">
        <button type="button" className="co-boton" onClick={onCancelar}>Cancelar</button>
        <button type="submit" className="co-boton primario" disabled={enviando}>{enviando ? 'Publicando…' : 'Publicar consulta'}</button>
      </div>
    </form>
  );
}

// Botones de lo que se puede hacer con una publicación: su autor la edita o la elimina; cualquier otra persona la reporta.
function AccionesDe({ item, onEditar, onEliminar, onReportar }) {
  if (item.esMia) {
    return (
      <>
        <button type="button" className="co-enlace" onClick={() => onEditar(item)}>Editar</button>
        <button type="button" className="co-enlace peligro" onClick={() => onEliminar(item)}>Eliminar</button>
      </>
    );
  }
  return <button type="button" className="co-enlace" onClick={() => onReportar(item)}>Reportar</button>;
}

// Una respuesta principal con su hilo, como los comentarios de Instagram: las respuestas a ella quedan recogidas bajo "Ver N
// respuestas" y se responde desde "Responder", sin llenar la página. Su autor puede editarla o eliminarla; los demás, reportarla.
function Hilo({ respuesta, hijas, puedeMarcar, onMarcarUtil, onResponder, onEditar, onEliminar, onReportar }) {
  const [abierto, setAbierto] = useState(false);
  const [escribiendo, setEscribiendo] = useState(false);
  const [texto, setTexto] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [editandoId, setEditandoId] = useState(null);
  const [textoEdicion, setTextoEdicion] = useState('');

  function abrirRespuesta(nombre) {
    // Responder a alguien del hilo lo menciona; igual queda en el mismo hilo.
    setTexto(nombre ? `@${nombre} ` : '');
    setEscribiendo(true);
    setAbierto(true);
  }

  async function enviar(e) {
    e.preventDefault();
    setEnviando(true);
    const ok = await onResponder(texto, respuesta.id);
    setEnviando(false);
    if (ok) {
      setTexto('');
      setEscribiendo(false);
      setAbierto(true);
    }
  }

  function empezarEdicion(item) {
    setEditandoId(item.id);
    setTextoEdicion(item.contenido);
  }

  async function guardarEdicion(e) {
    e.preventDefault();
    setEnviando(true);
    const ok = await onEditar(editandoId, textoEdicion);
    setEnviando(false);
    if (ok) setEditandoId(null);
  }

  // El texto de una publicación, o su formulario si se está editando.
  const cuerpo = (item) => (editandoId === item.id ? (
    <form className="co-hilo-form" onSubmit={guardarEdicion} noValidate>
      <textarea rows={3} maxLength={MAX_CONTENIDO} value={textoEdicion} autoFocus aria-label="Editar tu respuesta"
        onChange={(e) => setTextoEdicion(e.target.value)} />
      <div className="co-acciones">
        <button type="button" className="co-boton chico" onClick={() => setEditandoId(null)}>Cancelar</button>
        <button type="submit" className="co-boton primario chico" disabled={enviando || textoEdicion.trim().length === 0}>
          {enviando ? 'Guardando…' : 'Guardar'}
        </button>
      </div>
    </form>
  ) : (
    <p className="co-texto">{item.contenido}{item.editada && <span className="co-editado"> (editado)</span>}</p>
  ));

  const r = respuesta;
  return (
    <li className={`co-respuesta${r.util ? ' util' : ''}`}>
      {r.util && <span className="co-etiqueta util">★ Respuesta útil</span>}
      {cuerpo(r)}
      <div className="co-pie">
        <Autor autor={r.autor} fecha={r.fecha} />
        {puedeMarcar && (
          <button type="button" className="co-boton chico" onClick={() => onMarcarUtil(r.id)}>
            {r.util ? 'Quitar marca' : 'Marcar como útil'}
          </button>
        )}
      </div>

      <div className="co-hilo-acciones">
        <button type="button" className="co-enlace" onClick={() => abrirRespuesta('')}>Responder</button>
        {hijas.length > 0 && (
          <button type="button" className="co-enlace" aria-expanded={abierto} onClick={() => setAbierto((v) => !v)}>
            {abierto ? 'Ocultar respuestas' : `Ver ${hijas.length} ${hijas.length === 1 ? 'respuesta' : 'respuestas'}`}
          </button>
        )}
        <AccionesDe item={r} onEditar={empezarEdicion} onEliminar={(item) => onEliminar(item.id)} onReportar={(item) => onReportar('RESPUESTA', item)} />
      </div>

      {abierto && hijas.length > 0 && (
        <ul className="co-hijos">
          {hijas.map((h) => (
            <li key={h.id} className="co-hija">
              {cuerpo(h)}
              <div className="co-pie">
                <Autor autor={h.autor} fecha={h.fecha} />
                <span className="co-hilo-acciones">
                  <button type="button" className="co-enlace" onClick={() => abrirRespuesta(h.autor.nombre)}>Responder</button>
                  <AccionesDe item={h} onEditar={empezarEdicion} onEliminar={(item) => onEliminar(item.id)} onReportar={(item) => onReportar('RESPUESTA', item)} />
                </span>
              </div>
            </li>
          ))}
        </ul>
      )}

      {escribiendo && (
        <form className="co-hilo-form" onSubmit={enviar} noValidate>
          <textarea rows={2} maxLength={MAX_CONTENIDO} value={texto} autoFocus aria-label={`Responder a ${r.autor.nombre}`}
            placeholder="Escribe tu respuesta con respeto" onChange={(e) => setTexto(e.target.value)} />
          <div className="co-acciones">
            <button type="button" className="co-boton chico" onClick={() => { setEscribiendo(false); setTexto(''); }}>Cancelar</button>
            <button type="submit" className="co-boton primario chico" disabled={enviando || texto.trim().length === 0}>
              {enviando ? 'Publicando…' : 'Responder'}
            </button>
          </div>
        </form>
      )}
    </li>
  );
}

// Formulario para editar el título y el contenido de tu propia consulta.
function EdicionConsulta({ consulta, onGuardar, onCancelar }) {
  const [titulo, setTitulo] = useState(consulta.titulo);
  const [contenido, setContenido] = useState(consulta.contenido);
  const [guardando, setGuardando] = useState(false);

  async function guardar(e) {
    e.preventDefault();
    setGuardando(true);
    await onGuardar(titulo, contenido);
    setGuardando(false);
  }

  return (
    <form className="co-hilo-form" onSubmit={guardar} noValidate>
      <label htmlFor="fo-edit-titulo">Título</label>
      <input id="fo-edit-titulo" type="text" maxLength={MAX_TITULO} value={titulo} onChange={(e) => setTitulo(e.target.value)} />
      <label htmlFor="fo-edit-contenido">Tu duda</label>
      <textarea id="fo-edit-contenido" rows={5} maxLength={MAX_CONTENIDO} value={contenido} onChange={(e) => setContenido(e.target.value)} />
      <div className="co-acciones">
        <button type="button" className="co-boton chico" onClick={onCancelar}>Cancelar</button>
        <button type="submit" className="co-boton primario chico" disabled={guardando || !titulo.trim() || !contenido.trim()}>
          {guardando ? 'Guardando…' : 'Guardar cambios'}
        </button>
      </div>
    </form>
  );
}

// HU-17: una consulta con sus respuestas. La respuesta marcada como útil se muestra primero.
function DetalleConsulta({ id, onVolver }) {
  const { authFetch } = useAuth();
  const [consulta, setConsulta] = useState(null);
  const [error, setError] = useState('');
  const [texto, setTexto] = useState('');
  const [errorRespuesta, setErrorRespuesta] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [avisoLlama, setAvisoLlama] = useState(''); // moderación: el servidor no dejó publicar el texto (422)
  const [limite, setLimite] = useState(5); // respuestas principales visibles (el resto, con "Ver más")
  const [editando, setEditando] = useState(false); // se está editando la consulta
  const [reportando, setReportando] = useState(null); // { tipo: 'CONSULTA' | 'RESPUESTA', id, titulo } lo que se reporta

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const response = await authFetch(`/api/foro/consultas/${id}`);
        const data = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(data.error || '');
        if (activo) setConsulta(data);
      } catch (e) {
        if (activo) setError(e.message || 'No se pudo cargar la consulta.');
      }
    })();
    return () => { activo = false; };
  }, [authFetch, id]);

  // Publica una respuesta. padreId = null: respuesta principal a la consulta; si no, dentro del hilo de esa respuesta.
  // Devuelve true si se publicó (si la moderación no la dejó, LlamaLink avisa y devuelve false).
  async function publicar(contenido, padreId) {
    setErrorRespuesta('');
    try {
      const response = await authFetch(`/api/foro/consultas/${id}/respuestas`, {
        method: 'POST',
        body: JSON.stringify({ contenido, respuestaPadreId: padreId ?? null })
      });
      const data = await response.json().catch(() => ({}));
      if (response.status === 422) {
        setAvisoLlama(data.error);
        return false;
      }
      if (!response.ok) {
        setErrorRespuesta(data.campos?.contenido || data.error || 'No se pudo publicar tu respuesta.');
        return false;
      }
      setConsulta(data);
      return true;
    } catch {
      setErrorRespuesta('No se pudo conectar con el servidor.');
      return false;
    }
  }

  async function responder(e) {
    e.preventDefault();
    setEnviando(true);
    if (await publicar(texto, null)) setTexto('');
    setEnviando(false);
  }

  // Llamada genérica de edición o eliminación. Devuelve la respuesta del servidor o null si falló (LlamaLink avisa si la moderación no deja).
  async function llamar(ruta, metodo, cuerpo) {
    setErrorRespuesta('');
    try {
      const response = await authFetch(ruta, { method: metodo, body: cuerpo ? JSON.stringify(cuerpo) : undefined });
      const data = response.status === 204 ? {} : await response.json().catch(() => ({}));
      if (response.status === 422) {
        setAvisoLlama(data.error);
        return null;
      }
      if (!response.ok) {
        setErrorRespuesta((data.campos && Object.values(data.campos)[0]) || data.error || 'No se pudo completar la acción.');
        return null;
      }
      return data;
    } catch {
      setErrorRespuesta('No se pudo conectar con el servidor.');
      return null;
    }
  }

  async function editarConsulta(titulo, contenido) {
    const data = await llamar(`/api/foro/consultas/${id}`, 'PUT', { titulo, contenido });
    if (data) {
      setConsulta(data);
      setEditando(false);
    }
  }

  async function eliminarConsulta() {
    if (!window.confirm('¿Eliminar tu consulta? Se borrarán también sus respuestas y no se puede deshacer.')) return;
    if (await llamar(`/api/foro/consultas/${id}`, 'DELETE')) onVolver();
  }

  async function editarRespuesta(idRespuesta, contenido) {
    const data = await llamar(`/api/foro/consultas/${id}/respuestas/${idRespuesta}`, 'PUT', { contenido });
    if (!data) return false;
    setConsulta(data);
    return true;
  }

  async function eliminarRespuesta(idRespuesta) {
    if (!window.confirm('¿Eliminar tu respuesta? Si tiene respuestas dentro, también se borrarán.')) return;
    const data = await llamar(`/api/foro/consultas/${id}/respuestas/${idRespuesta}`, 'DELETE');
    if (data) setConsulta(data);
  }

  const reportar = (tipo, item) => setReportando({ tipo, id: item.id, titulo: tipo === 'CONSULTA' ? 'Reportar esta consulta' : 'Reportar esta respuesta' });

  async function marcarUtil(idRespuesta) {
    setErrorRespuesta('');
    try {
      const response = await authFetch(`/api/foro/consultas/${id}/respuestas/${idRespuesta}/util`, { method: 'PATCH' });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        setErrorRespuesta(data.error || 'No se pudo marcar la respuesta.');
        return;
      }
      setConsulta(data);
    } catch {
      setErrorRespuesta('No se pudo conectar con el servidor.');
    }
  }

  const principales = consulta ? consulta.respuestas.filter((x) => x.padreId == null) : [];

  return (
    <div className="co">
      <button type="button" className="co-volver" onClick={onVolver}>← Volver al foro</button>
      {error && <p className="co-error" role="alert">{error}</p>}
      {!consulta && !error && <p className="co-vacio">Cargando consulta…</p>}

      {consulta && (
        <>
          <article className="co-tarjeta">
            <span className="co-etiqueta curso">{consulta.curso}</span>
            <h2 className="co-titulo-consulta">{consulta.titulo}</h2>
            {editando ? (
              <EdicionConsulta consulta={consulta} onGuardar={editarConsulta} onCancelar={() => setEditando(false)} />
            ) : (
              <>
                <p className="co-texto">{consulta.contenido}{consulta.editada && <span className="co-editado"> (editado)</span>}</p>
                <Autor autor={consulta.autor} fecha={consulta.fecha} />
                <div className="co-hilo-acciones">
                  <AccionesDe item={{ esMia: consulta.esMia, id: consulta.id }} onEditar={() => setEditando(true)} onEliminar={eliminarConsulta}
                    onReportar={() => reportar('CONSULTA', consulta)} />
                </div>
              </>
            )}
          </article>

          <h3 className="co-subtitulo">{consulta.respuestas.length === 0 ? 'Aún no hay respuestas' : `${consulta.respuestas.length} ${consulta.respuestas.length === 1 ? 'respuesta' : 'respuestas'}`}</h3>
          {errorRespuesta && <p className="co-error" role="alert">{errorRespuesta}</p>}
          {avisoLlama && <AvisoLlama mensaje={avisoLlama} onCerrar={() => setAvisoLlama('')} />}
          {reportando && (
            <ReportarModal
              tipo={reportando.tipo}
              idConsulta={reportando.tipo === 'CONSULTA' ? reportando.id : undefined}
              idRespuesta={reportando.tipo === 'RESPUESTA' ? reportando.id : undefined}
              titulo={reportando.titulo}
              subtitulo="Cuéntanos qué está mal. Un administrador revisará el reporte y la otra persona no sabrá quién lo envió."
              onCerrar={() => setReportando(null)}
            />
          )}

          <ul className="co-lista">
            {principales.slice(0, limite).map((r) => (
              <Hilo
                key={r.id}
                respuesta={r}
                hijas={consulta.respuestas.filter((h) => h.padreId === r.id)}
                puedeMarcar={consulta.esMia}
                onMarcarUtil={marcarUtil}
                onResponder={publicar}
                onEditar={editarRespuesta}
                onEliminar={eliminarRespuesta}
                onReportar={reportar}
              />
            ))}
          </ul>
          {principales.length > limite && (
            <button type="button" className="co-boton" onClick={() => setLimite((n) => n + 5)}>
              Ver más respuestas ({principales.length - limite})
            </button>
          )}

          <form className="co-tarjeta" onSubmit={responder} noValidate>
            <label htmlFor="fo-respuesta">Tu respuesta</label>
            <textarea id="fo-respuesta" rows={4} maxLength={MAX_CONTENIDO} value={texto}
              placeholder="Ayuda con lo que sepas. Sé claro y respetuoso." onChange={(e) => setTexto(e.target.value)} />
            <p className="co-contador">{texto.length}/{MAX_CONTENIDO}</p>
            <div className="co-acciones">
              <button type="submit" className="co-boton primario" disabled={enviando || texto.trim().length === 0}>
                {enviando ? 'Publicando…' : 'Responder'}
              </button>
            </div>
          </form>
        </>
      )}
    </div>
  );
}

// HU-17: foro de consultas por curso. Se pueden filtrar por curso o buscar por una palabra, publicar una consulta nueva y
// responder las de otros. Lo que el administrador oculta (HU-54) no aparece aquí.
export default function Foro() {
  const { authFetch } = useAuth();
  const [cursos, setCursos] = useState([]);
  const [cursoId, setCursoId] = useState('');
  const [busqueda, setBusqueda] = useState('');
  const [aplicada, setAplicada] = useState('');
  const [consultas, setConsultas] = useState(null);
  const [error, setError] = useState('');
  const [redactando, setRedactando] = useState(false);
  const [abierta, setAbierta] = useState(null);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const response = await authFetch('/api/cursos');
        if (response.ok && activo) setCursos(await response.json());
      } catch { /* sin lista de cursos: el filtro queda vacío */ }
    })();
    return () => { activo = false; };
  }, [authFetch]);

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const parametros = new URLSearchParams();
        if (cursoId) parametros.set('cursoId', cursoId);
        if (aplicada.trim()) parametros.set('q', aplicada.trim());
        const response = await authFetch(`/api/foro/consultas${parametros.size ? `?${parametros}` : ''}`);
        const data = await response.json().catch(() => null);
        if (!response.ok) throw new Error(data?.error || '');
        if (!activo) return;
        setConsultas(data);
        setError('');
      } catch (e) {
        if (!activo) return;
        setError(e.message || 'No se pudieron cargar las consultas.');
        setConsultas((actual) => actual ?? []);
      }
    })();
    return () => { activo = false; };
  }, [authFetch, cursoId, aplicada, version]);

  if (abierta) {
    return <DetalleConsulta id={abierta} onVolver={() => { setAbierta(null); setVersion((v) => v + 1); }} />;
  }

  return (
    <div className="co">
      {redactando ? (
        <NuevaConsulta
          cursos={cursos}
          onCancelar={() => setRedactando(false)}
          onCreada={(c) => { setRedactando(false); setAbierta(c.id); }}
        />
      ) : (
        <div className="co-barra">
          <form className="co-filtros" onSubmit={(e) => { e.preventDefault(); setConsultas(null); setAplicada(busqueda); }}>
            <select aria-label="Filtrar por curso" value={cursoId} onChange={(e) => { setConsultas(null); setCursoId(e.target.value); }}>
              <option value="">Todos los cursos</option>
              {cursos.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
            </select>
            <input type="search" aria-label="Buscar en el foro" placeholder="Buscar una palabra…" value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)} />
            <button type="submit" className="co-boton">Buscar</button>
          </form>
          <button type="button" className="co-boton primario" onClick={() => setRedactando(true)}>Nueva consulta</button>
        </div>
      )}

      {error && <p className="co-error" role="alert">{error}</p>}
      {consultas === null && <p className="co-vacio">Cargando consultas…</p>}
      {consultas?.length === 0 && !error && (
        <p className="co-vacio">
          {cursoId || aplicada ? 'No hay consultas con ese filtro.' : 'Todavía no hay consultas. ¡Sé la primera persona en preguntar!'}
        </p>
      )}

      <ul className="co-lista">
        {consultas?.map((c) => (
          <li key={c.id}>
            <button type="button" className="co-consulta" onClick={() => setAbierta(c.id)}>
              <div className="co-consulta-cab">
                <span className="co-etiqueta curso">{c.curso}</span>
                {c.resuelta && <span className="co-etiqueta util">✓ Resuelta</span>}
              </div>
              <h3>{c.titulo}</h3>
              <p className="co-extracto">{c.extracto}</p>
              <div className="co-pie">
                <Autor autor={c.autor} fecha={c.fecha} />
                <span className="co-contador-resp">{c.respuestas} {c.respuestas === 1 ? 'respuesta' : 'respuestas'}</span>
              </div>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
