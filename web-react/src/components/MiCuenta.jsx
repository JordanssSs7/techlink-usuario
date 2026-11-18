import { useEffect, useRef, useState } from 'react';
import './Cuenta.css';
import { useAuth } from '../auth/authContext';
import Avatar from './Avatar';
import { avisarPerfilActualizado } from './fotoPerfil';

const MAX_DESCRIPCION = 300;
const MAX_INTERESES = 8;
const MAX_FOTO = 2 * 1024 * 1024;
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
  const [foto, setFoto] = useState(null);       // archivo elegido, todavía sin subir
  const [vista, setVista] = useState(null);     // dirección local para mostrarlo antes de guardar
  const [errores, setErrores] = useState({});
  const [aviso, setAviso] = useState('');
  const [guardando, setGuardando] = useState(false);
  const [falla, setFalla] = useState('');
  const entrada = useRef(null);

  const cargarFormulario = (p) => {
    setPerfil(p);
    setDescripcion(p.descripcion || '');
    setCiclo(p.ciclo ? String(p.ciclo) : '');
    setIntereses(p.intereses || []);
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

  useEffect(() => () => { if (vista) URL.revokeObjectURL(vista); }, [vista]);

  if (falla) return <p className="cu-error" role="alert">{falla}</p>;
  if (!perfil) return <p className="cu-vacio">Cargando tu perfil…</p>;

  const esEstudiante = perfil.rol === 'ESTUDIANTE';

  const elegirFoto = (e) => {
    const archivo = e.target.files?.[0];
    e.target.value = '';
    if (!archivo) return;
    if (!TIPOS_FOTO.includes(archivo.type)) {
      setErrores((x) => ({ ...x, foto: 'La foto debe ser JPG o PNG.' }));
      return;
    }
    if (archivo.size > MAX_FOTO) {
      setErrores((x) => ({ ...x, foto: 'La foto no puede pesar más de 2 MB.' }));
      return;
    }
    setErrores((x) => ({ ...x, foto: undefined }));
    setFoto(archivo);
    setVista(URL.createObjectURL(archivo));
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
        body: JSON.stringify({ descripcion, ciclo: esEstudiante && ciclo ? Number(ciclo) : null, intereses })
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        setErrores(data.campos || { general: data.error || 'No se pudo guardar tu perfil.' });
        return;
      }
      let actualizado = data;

      if (foto) {
        const formulario = new FormData();
        formulario.append('foto', foto);
        const respFoto = await authFetch('/api/perfil/foto', { method: 'POST', body: formulario });
        const dataFoto = await respFoto.json().catch(() => ({}));
        if (!respFoto.ok) {
          // Los datos ya se guardaron; solo la foto falló.
          cargarFormulario(actualizado);
          setErrores(dataFoto.campos || { foto: dataFoto.error || 'No se pudo guardar la foto.' });
          return;
        }
        actualizado = dataFoto;
        setFoto(null);
        setVista(null);
      }

      cargarFormulario(actualizado);
      if (esEstudiante) actualizarUsuario({ ciclo: actualizado.ciclo });
      avisarPerfilActualizado();
      setAviso('Cambios guardados.');
    } catch {
      setErrores({ general: 'No se pudo conectar con el servidor.' });
    } finally {
      setGuardando(false);
    }
  }

  return (
    <form className="cu" onSubmit={guardar} noValidate>
      {aviso && <p className="cu-aviso" role="status">{aviso}</p>}
      {errores.general && <p className="cu-error" role="alert">{errores.general}</p>}

      <section className="cu-tarjeta cu-foto-fila">
        {vista
          ? <img className="cu-vista" src={vista} alt="Vista previa de tu foto" />
          : <Avatar idUsuario={perfil.id} tieneFoto={perfil.tieneFoto} version={perfil.fotoActualizada}
              iniciales={iniciales(perfil)} tamano={96} />}
        <div>
          <h2 className="cu-nombre">{perfil.nombres} {perfil.apellidos !== '-' ? perfil.apellidos : ''}</h2>
          <p className="cu-sub">{perfil.correoInstitucional}</p>
          <input ref={entrada} type="file" accept="image/png,image/jpeg" className="cu-oculto" onChange={elegirFoto} aria-label="Elegir foto" />
          <button type="button" className="cu-boton" onClick={() => entrada.current?.click()}>
            {perfil.tieneFoto || vista ? 'Cambiar foto' : 'Subir foto'}
          </button>
          <p className="cu-ayuda">JPG o PNG, máximo 2 MB.{foto && ' Se guardará al pulsar «Guardar cambios».'}</p>
          {errores.foto && <p className="cu-campo-error" role="alert">{errores.foto}</p>}
        </div>
      </section>

      <section className="cu-tarjeta">
        <label htmlFor="cu-desc">Descripción <span className="cu-obligatorio">(obligatoria)</span></label>
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
            maxLength={40}
            value={nuevo}
            placeholder="Ej: Java, inteligencia artificial…"
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
        <p className="cu-ayuda">Hasta {MAX_INTERESES} intereses.</p>
        {errores.intereses && <p className="cu-campo-error" role="alert">{errores.intereses}</p>}
      </section>

      <div className="cu-acciones">
        <button type="submit" className="cu-boton primario" disabled={guardando}>
          {guardando ? 'Guardando…' : 'Guardar cambios'}
        </button>
      </div>
    </form>
  );
}
