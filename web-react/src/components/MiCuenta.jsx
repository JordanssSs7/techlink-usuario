import { useEffect, useRef, useState } from 'react';
import './Cuenta.css';
import { useAuth } from '../auth/authContext';
import Avatar from './Avatar';
import { avisarPerfilActualizado, usePortadaUrl } from './fotoPerfil';
import Cargando from './Cargando';
import SeguidoresModal from './SeguidoresModal';

const MAX_DESCRIPCION = 300;
const MAX_INTERESES = 8;
const MAX_INTERES = 50; // caracteres por interés
const MAX_FOTO = 2 * 1024 * 1024;
const MAX_PORTADA = 4 * 1024 * 1024;
const MAX_FRASE = 60;
const EMOJIS = ['😀', '🎯', '📚', '💻', '🚀', '☕', '🌱', '🎧', '🔥', '🧠', '✨', '🏖️'];
const TIPOS_FOTO = ['image/jpeg', 'image/png'];
const ROMANOS = ['I', 'II', 'III', 'IV', 'V', 'VI'];

const iniciales = (p) => [p.nombres, p.apellidos].filter((x) => x && x !== '-').map((x) => x.trim()[0]).join('').slice(0, 2).toUpperCase();

// HU-13: el usuario edita su perfil: foto (JPG o PNG, máximo 2 MB), descripción (máximo 300 caracteres), ciclo
// (solo estudiantes) e intereses. Los cambios se ven de inmediato y los campos obligatorios vacíos se rechazan.
export default function MiCuenta() {
  const { authFetch, actualizarUsuario } = useAuth();
  const [perfil, setPerfil] = useState(null);
  const [descripcion, setDescripcion] = useState('');
  const [ciclo, setCiclo] = useState('');
  const [intereses, setIntereses] = useState([]);
  const [nuevo, setNuevo] = useState('');
  const [frase, setFrase] = useState('');
  const [editandoEstado, setEditandoEstado] = useState(false);
  const [menuFoto, setMenuFoto] = useState(false);       // menú al tocar la foto: agregar / eliminar
  const [editandoPerfil, setEditandoPerfil] = useState(false); // editor del apodo
  const [apodo, setApodo] = useState('');
  const [conteos, setConteos] = useState({ seguidores: 0, siguiendo: 0 });
  const [lista, setLista] = useState(null);              // 'seguidores' o 'siguiendo'
  const [subiendo, setSubiendo] = useState('');  // 'foto' o 'portada' mientras se sube
  const [errores, setErrores] = useState({});
  const [aviso, setAviso] = useState('');
  const [guardando, setGuardando] = useState(false);
  const [falla, setFalla] = useState('');
  const entrada = useRef(null);
  const entradaPortada = useRef(null);

  const cargarFormulario = (p) => {
    setPerfil(p);
    setDescripcion(p.descripcion || '');
    setCiclo(p.ciclo ? String(p.ciclo) : '');
    setIntereses(p.intereses || []);
    setFrase(p.frase || '');
    setApodo(p.apodo || '');
  };

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const response = await authFetch('/api/perfil');
        if (!response.ok) throw new Error();
        const p = await response.json();
        if (activo) cargarFormulario(p);
      } catch {
        if (activo) setFalla('No se pudo cargar tu perfil. Intenta de nuevo.');
      }
    })();
    return () => { activo = false; };
  }, [authFetch]);

  const idPerfil = perfil?.id;
  useEffect(() => {
    if (!idPerfil) return undefined;
    let activo = true;
    (async () => {
      try {
        const response = await authFetch(`/api/usuarios/${idPerfil}/perfil-social`);
        if (response.ok && activo) {
          const d = await response.json();
          setConteos({ seguidores: d.seguidores, siguiendo: d.siguiendoTotal });
        }
      } catch { /* sin conteos */ }
    })();
    return () => { activo = false; };
  }, [authFetch, idPerfil]);

  const urlPortada = usePortadaUrl(perfil?.id, perfil?.tienePortada, perfil?.portadaActualizada);

  if (falla) return <p className="cu-error" role="alert">{falla}</p>;
  if (!perfil) return <Cargando texto="Cargando tu perfil…" />;

  const esEstudiante = perfil.rol === 'ESTUDIANTE';

  // Sube la foto de perfil o la portada en cuanto se elige (como en una red social): no hace falta pulsar «Guardar cambios».
  const subirImagen = async (e, cual) => {
    const archivo = e.target.files?.[0];
    e.target.value = '';
    if (!archivo) return;
    const nombre = cual === 'portada' ? 'La portada' : 'La foto';
    const limite = cual === 'portada' ? MAX_PORTADA : MAX_FOTO;
    if (!TIPOS_FOTO.includes(archivo.type)) {
      setErrores((x) => ({ ...x, [cual]: `${nombre} debe ser JPG o PNG.` }));
      return;
    }
    if (archivo.size > limite) {
      setErrores((x) => ({ ...x, [cual]: `${nombre} no puede pesar más de ${limite / 1024 / 1024} MB.` }));
      return;
    }
    setErrores((x) => ({ ...x, [cual]: undefined }));
    setAviso('');
    setSubiendo(cual);
    try {
      const formulario = new FormData();
      formulario.append(cual, archivo);
      const response = await authFetch(`/api/perfil/${cual}`, { method: 'POST', body: formulario });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        setErrores((x) => ({ ...x, [cual]: data.campos?.[cual] || data.error || 'No se pudo guardar la imagen.' }));
        return;
      }
      setPerfil((p) => ({ ...p, tieneFoto: data.tieneFoto, fotoActualizada: data.fotoActualizada,
        tienePortada: data.tienePortada, portadaActualizada: data.portadaActualizada }));
      if (cual === 'foto') avisarPerfilActualizado();
      setAviso(cual === 'portada' ? 'Portada actualizada.' : 'Foto actualizada.');
    } catch {
      setErrores((x) => ({ ...x, [cual]: 'No se pudo conectar con el servidor.' }));
    } finally {
      setSubiendo('');
    }
  };

  const agregarInteres = () => {
    const limpio = nuevo.trim().replace(/\s+/g, ' ');
    if (limpio.length < 2) return;
    if (intereses.length >= MAX_INTERESES) {
      setErrores((x) => ({ ...x, intereses: `Puedes agregar hasta ${MAX_INTERESES} intereses.` }));
      return;
    }
    if (!intereses.some((i) => i.toLowerCase() === limpio.toLowerCase())) setIntereses([...intereses, limpio]);
    setNuevo('');
    setErrores((x) => ({ ...x, intereses: undefined }));
  };

  async function guardar(e) {
    e.preventDefault();
    setGuardando(true);
    setAviso('');
    setErrores({});
    try {
      const response = await authFetch('/api/perfil', {
        method: 'PUT',
        body: JSON.stringify({ descripcion, ciclo: esEstudiante && ciclo ? Number(ciclo) : null, intereses, frase: perfil.frase ?? '' })
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        setErrores(data.campos || { general: data.error || 'No se pudo guardar tu perfil.' });
        return;
      }
      cargarFormulario(data);
      if (esEstudiante) actualizarUsuario({ ciclo: data.ciclo });
      avisarPerfilActualizado();
      setAviso('Cambios guardados.');
    } catch {
      setErrores({ general: 'No se pudo conectar con el servidor.' });
    } finally {
      setGuardando(false);
    }
  }

  // Quita la foto de perfil: vuelven las iniciales. Los demás lo ven al instante.
  async function quitarFoto() {
    setAviso('');
    setErrores({});
    try {
      const response = await authFetch('/api/perfil/foto', { method: 'DELETE' });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        setErrores({ foto: data.error || 'No se pudo quitar la foto.' });
        return;
      }
      setPerfil((p) => ({ ...p, tieneFoto: false, fotoActualizada: null }));
      avisarPerfilActualizado();
      setAviso('Foto quitada.');
    } catch {
      setErrores({ foto: 'No se pudo conectar con el servidor.' });
    }
  }

  // Como el «Edit status» de GitHub: un emoji y una frase corta que se guardan al instante.
  async function guardarEstado(valor) {
    setAviso('');
    setErrores((x) => ({ ...x, frase: undefined }));
    try {
      // Solo el estado: no depende de que la descripción y el resto del perfil ya estén completos.
      const response = await authFetch('/api/perfil/estado', { method: 'PUT', body: JSON.stringify({ frase: valor }) });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        setErrores((x) => ({ ...x, frase: data.campos?.frase || data.error || 'No se pudo guardar tu estado.' }));
        return;
      }
      setFrase(data.frase || '');
      setPerfil((p) => ({ ...p, frase: data.frase }));
      setEditandoEstado(false);
    } catch {
      setErrores((x) => ({ ...x, frase: 'No se pudo conectar con el servidor.' }));
    }
  }

  async function guardarApodo() {
    setAviso('');
    setErrores((x) => ({ ...x, apodo: undefined }));
    try {
      const response = await authFetch('/api/perfil', {
        method: 'PUT',
        body: JSON.stringify({ descripcion, ciclo: esEstudiante && ciclo ? Number(ciclo) : null, intereses, frase: perfil.frase ?? '', apodo })
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        setErrores((x) => ({ ...x, apodo: data.campos?.apodo || data.campos?.descripcion || data.error || 'No se pudo guardar tu apodo.' }));
        return;
      }
      setPerfil((p) => ({ ...p, apodo: data.apodo }));
      setApodo(data.apodo || '');
      setEditandoPerfil(false);
    } catch {
      setErrores((x) => ({ ...x, apodo: 'No se pudo conectar con el servidor.' }));
    }
  }

  async function quitarPortada() {
    setAviso('');
    try {
      const response = await authFetch('/api/perfil/portada', { method: 'DELETE' });
      if (!response.ok) throw new Error();
      setPerfil((p) => ({ ...p, tienePortada: false, portadaActualizada: null }));
      setAviso('Portada quitada.');
    } catch {
      setErrores((x) => ({ ...x, portada: 'No se pudo quitar la portada.' }));
    }
  }

  return (
    <form className="cu" onSubmit={guardar} noValidate>
      {aviso && <p className="cu-aviso" role="status">{aviso}</p>}
      {errores.general && <p className="cu-error" role="alert">{errores.general}</p>}

      <section className="cu-tarjeta cu-perfil">
        <div className={`cu-portada${urlPortada ? '' : ' sin-imagen'}`} style={urlPortada ? { backgroundImage: `url(${urlPortada})` } : undefined}>
          <input ref={entradaPortada} type="file" accept="image/png,image/jpeg" className="cu-oculto" onChange={(e) => subirImagen(e, 'portada')} aria-label="Elegir foto de portada" />
          <div className="cu-portada-acciones">
            <button type="button" className="cu-boton-claro" disabled={subiendo === 'portada'} onClick={() => entradaPortada.current?.click()}>
              {subiendo === 'portada' ? 'Subiendo…' : 'Editar foto de portada'}
            </button>
            {perfil.tienePortada && <button type="button" className="cu-boton-claro" onClick={quitarPortada}>Quitar</button>}
          </div>
        </div>
        <div className="cu-perfil-cuerpo">
          <div className="cu-perfil-avatar">
            <button type="button" className="cu-avatar-boton" aria-haspopup="menu" aria-expanded={menuFoto} disabled={subiendo === 'foto'}
              onClick={() => setMenuFoto((v) => !v)} aria-label="Foto de perfil: ver opciones">
              <Avatar idUsuario={perfil.id} tieneFoto={perfil.tieneFoto} version={perfil.fotoActualizada}
                iniciales={iniciales(perfil)} tamano={132} insignia={perfil.rol === 'MENTOR'} />
            </button>
            <input ref={entrada} type="file" accept="image/png,image/jpeg" className="cu-oculto" onChange={(e) => subirImagen(e, 'foto')} aria-label="Elegir foto de perfil" />
            {menuFoto && (
              <div className="cu-menu-foto" role="menu">
                <button type="button" role="menuitem" onClick={() => { setMenuFoto(false); entrada.current?.click(); }}>
                  {perfil.tieneFoto ? 'Cambiar foto de perfil' : 'Agregar foto de perfil'}
                </button>
                {perfil.tieneFoto && (
                  <button type="button" role="menuitem" className="peligro" onClick={() => { setMenuFoto(false); quitarFoto(); }}>Eliminar foto de perfil</button>
                )}
              </div>
            )}
            {!editandoEstado && (
              <button type="button" className="cu-estado sobre-foto" onClick={() => setEditandoEstado(true)}>
                <span aria-hidden="true">{perfil.frase ? '💬' : '☺'}</span>
                {perfil.frase || 'Editar estado'}
              </button>
            )}
          </div>
          <div className="cu-perfil-datos">
            <h2 className="cu-nombre">{perfil.nombres} {perfil.apellidos !== '-' ? perfil.apellidos : ''}</h2>
            {perfil.apodo && <p className="cu-apodo">{perfil.apodo}</p>}
            <p className="cu-sub">{perfil.correoInstitucional}</p>
            {!editandoPerfil && (
              <button type="button" className="cu-boton-gris" onClick={() => { setApodo(perfil.apodo || ''); setEditandoPerfil(true); }}>Editar perfil</button>
            )}
            {editandoPerfil && (
              <div className="cu-estado-editor">
                <label htmlFor="cu-apodo">Apodo</label>
                <div className="cu-estado-campo">
                  <input id="cu-apodo" type="text" autoFocus value={apodo} maxLength={30} placeholder="Cómo quieres que te llamen"
                    onChange={(e) => setApodo(e.target.value)} />
                  <span className="cu-contador">{[...apodo].length}/30</span>
                </div>
                <p className="cu-ayuda">Tu nombre no se puede cambiar, pero puedes poner un apodo.</p>
                <div className="cu-estado-acciones">
                  <button type="button" className="cu-boton primario" onClick={guardarApodo}>Guardar</button>
                  <button type="button" className="cu-boton" onClick={() => setEditandoPerfil(false)}>Cancelar</button>
                </div>
              </div>
            )}
            <p className="cu-conteo">
              <button type="button" onClick={() => setLista('seguidores')}><strong>{conteos.seguidores}</strong> {conteos.seguidores === 1 ? 'seguidor' : 'seguidores'}</button>
              {' · '}
              <button type="button" onClick={() => setLista('siguiendo')}><strong>{conteos.siguiendo}</strong> siguiendo</button>
            </p>
            {editandoEstado && (
              <div className="cu-estado-editor">
                <label htmlFor="cu-frase">¿Qué estás pensando?</label>
                <div className="cu-estado-campo">
                  <input id="cu-frase" type="text" autoFocus value={frase} maxLength={MAX_FRASE} placeholder="Ej: Estudiando para el parcial 📚"
                    onChange={(e) => setFrase(e.target.value)} />
                  <span className="cu-contador">{[...frase].length}/{MAX_FRASE}</span>
                </div>
                <div className="cu-emojis">
                  {EMOJIS.map((em) => (
                    <button key={em} type="button" aria-label={`Agregar ${em}`}
                      onClick={() => setFrase((f) => ([...f].length < MAX_FRASE ? `${f}${em}` : f))}>{em}</button>
                  ))}
                </div>
                <div className="cu-estado-acciones">
                  <button type="button" className="cu-boton primario" onClick={() => guardarEstado(frase)}>Guardar estado</button>
                  <button type="button" className="cu-boton" onClick={() => { setFrase(perfil.frase || ''); setEditandoEstado(false); }}>Cancelar</button>
                  {perfil.frase && <button type="button" className="cu-enlace" onClick={() => { setFrase(''); guardarEstado(''); }}>Quitar estado</button>}
                </div>
              </div>
            )}
            {errores.frase && <p className="cu-campo-error" role="alert">{errores.frase}</p>}
            {errores.apodo && <p className="cu-campo-error" role="alert">{errores.apodo}</p>}
            {errores.foto && <p className="cu-campo-error" role="alert">{errores.foto}</p>}
            {errores.portada && <p className="cu-campo-error" role="alert">{errores.portada}</p>}
          </div>
        </div>
      </section>

      <section className="cu-tarjeta">
        <label htmlFor="cu-desc">Descripción <span className="cu-opcional">(opcional)</span></label>
        <textarea
          id="cu-desc"
          rows={4}
          maxLength={MAX_DESCRIPCION}
          value={descripcion}
          aria-invalid={Boolean(errores.descripcion)}
          placeholder="Cuéntales a los demás quién eres y qué te gusta."
          onChange={(e) => setDescripcion(e.target.value)}
        />
        <p className="cu-contador">{descripcion.length}/{MAX_DESCRIPCION}</p>
        {errores.descripcion && <p className="cu-campo-error" role="alert">{errores.descripcion}</p>}

        {esEstudiante && (
          <>
            <label htmlFor="cu-ciclo">Ciclo <span className="cu-obligatorio">(obligatorio)</span></label>
            <select id="cu-ciclo" value={ciclo} aria-invalid={Boolean(errores.ciclo)} onChange={(e) => setCiclo(e.target.value)}>
              <option value="">Elige tu ciclo</option>
              {ROMANOS.map((r, i) => <option key={r} value={i + 1}>{r} ciclo</option>)}
            </select>
            {errores.ciclo && <p className="cu-campo-error" role="alert">{errores.ciclo}</p>}
          </>
        )}

        <label htmlFor="cu-interes">Intereses</label>
        <div className="cu-fila">
          <input
            id="cu-interes"
            type="text"
            maxLength={MAX_INTERES}
            value={nuevo}
            placeholder="Ej: Soy bueno en JavaScript, me interesa la IA…"
            onChange={(e) => setNuevo(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); agregarInteres(); } }}
          />
          <button type="button" className="cu-boton" onClick={agregarInteres}>Agregar</button>
        </div>
        {intereses.length > 0 && (
          <ul className="cu-chips">
            {intereses.map((i) => (
              <li key={i}>
                {i}
                <button type="button" aria-label={`Quitar ${i}`} onClick={() => setIntereses(intereses.filter((x) => x !== i))}>×</button>
              </li>
            ))}
          </ul>
        )}
        <p className="cu-ayuda">
          Hasta {MAX_INTERESES} intereses, de hasta {MAX_INTERES} caracteres cada uno. Puedes escribir frases como «Soy bueno en Python» o «Me interesa la inteligencia artificial».
          <span className="cu-contador"> {nuevo.length}/{MAX_INTERES}</span>
        </p>
        {errores.intereses && <p className="cu-campo-error" role="alert">{errores.intereses}</p>}
      </section>

      <div className="cu-acciones">
        <button type="submit" className="cu-boton primario" disabled={guardando}>
          {guardando ? 'Guardando…' : 'Guardar cambios'}
        </button>
      </div>
      {lista && <SeguidoresModal idUsuario={perfil.id} tipo={lista} onCerrar={() => setLista(null)} />}
    </form>
  );
}
