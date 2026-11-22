import { useCallback, useEffect, useState } from 'react';
import './Seguimiento.css';
import { useAuth } from '../auth/authContext';
import Cargando from './Cargando';
import { aIso, fechaCorta, hoyLocal, sumarDias } from './fechasSeguimiento';

// HU-11: el mentor acuerda metas semanales con sus estudiantes (los que tienen sesiones con él) y ve si las cumplen.
export default function MetasMentor() {
  const { authFetch } = useAuth();
  const [datos, setDatos] = useState(null);
  const [cursos, setCursos] = useState([]);
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');
  const [version, setVersion] = useState(0);
  const recargar = useCallback(() => setVersion((v) => v + 1), []);

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const [r1, r2] = await Promise.all([authFetch('/api/metas/mentor'), authFetch('/api/cursos')]);
        const d1 = await r1.json().catch(() => null);
        const d2 = await r2.json().catch(() => []);
        if (!r1.ok) throw new Error(d1?.error || '');
        if (!activo) return;
        setDatos(d1);
        if (r2.ok && Array.isArray(d2)) setCursos(d2);
        setError('');
      } catch (e) {
        if (!activo) return;
        setError(e.message || 'No se pudieron cargar las metas.');
        setDatos((actual) => actual ?? { estudiantes: [], metas: [] });
      }
    })();
    return () => { activo = false; };
  }, [authFetch, version]);

  useEffect(() => {
    if (!aviso) return undefined;
    const t = setTimeout(() => setAviso(''), 5000);
    return () => clearTimeout(t);
  }, [aviso]);

  async function quitar(meta) {
    try {
      const response = await authFetch(`/api/metas/${meta.id}`, { method: 'DELETE' });
      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        throw new Error(data.error || 'No se pudo quitar la meta.');
      }
      setAviso('Meta eliminada.');
      recargar();
    } catch (e) {
      setError(e.message);
    }
  }

  const pendientes = datos?.metas.filter((m) => !m.completada) ?? [];
  const cumplidas = datos?.metas.filter((m) => m.completada) ?? [];

  return (
    <section className="sg" aria-label="Metas de mis estudiantes">
      {datos === null && !error && <Cargando texto="Cargando tus estudiantes…" />}
      {aviso && <p className="sg-aviso" role="status">{aviso}</p>}
      {error && <p className="sg-error" role="alert">{error}</p>}

      {datos && (
        <>
          {datos.estudiantes.length === 0 ? (
            <p className="sg-nota">Todavía no tienes estudiantes: cuando alguien agende una sesión contigo podrás ponerle metas.</p>
          ) : (
            <FormularioMentor estudiantes={datos.estudiantes} cursos={cursos}
              onCreada={() => { setAviso('Meta enviada. Tu estudiante la verá en su panel.'); recargar(); }} />
          )}

          <h3 className="sg-titulo">Pendientes ({pendientes.length})</h3>
          {pendientes.length === 0 ? <p className="sg-vacio">No hay metas pendientes.</p> : (
            <ul className="sg-lista">
              {pendientes.map((m) => <FilaMentor key={m.id} meta={m} onQuitar={quitar} />)}
            </ul>
          )}

          <h3 className="sg-titulo">Cumplidas ({cumplidas.length})</h3>
          {cumplidas.length === 0 ? <p className="sg-vacio">Todavía ninguna.</p> : (
            <ul className="sg-lista">
              {cumplidas.map((m) => <FilaMentor key={m.id} meta={m} onQuitar={quitar} />)}
            </ul>
          )}
        </>
      )}
    </section>
  );
}

function FilaMentor({ meta, onQuitar }) {
  const estado = meta.completada ? 'Completada' : meta.atrasada ? 'Atrasada' : 'Pendiente';
  return (
    <li className={`sg-meta${meta.completada ? ' hecha' : ''}${meta.atrasada ? ' atrasada' : ''}`}>
      <span className="sg-check solo-lectura" aria-hidden="true">{meta.completada ? '✓' : ''}</span>
      <div className="sg-meta-cuerpo">
        <p className="sg-meta-titulo">{meta.titulo}</p>
        <p className="sg-meta-datos">
          <span className="sg-estado">{estado}</span>
          <span>{meta.estudiante}</span>
          <span>Entrega: {fechaCorta(meta.fechaEntrega)}</span>
          {meta.curso && <span>{meta.curso}</span>}
        </p>
      </div>
      <button type="button" className="sg-quitar" aria-label={`Quitar la meta: ${meta.titulo}`} onClick={() => onQuitar(meta)}>×</button>
    </li>
  );
}

function FormularioMentor({ estudiantes, cursos, onCreada }) {
  const { authFetch } = useAuth();
  const hoy = aIso(hoyLocal());
  const [alumno, setAlumno] = useState('');
  const [titulo, setTitulo] = useState('');
  const [idCurso, setIdCurso] = useState('');
  const [fecha, setFecha] = useState(aIso(sumarDias(hoyLocal(), 7)));
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);

  async function enviar(e) {
    e.preventDefault();
    if (!alumno) { setError('Elige al estudiante.'); return; }
    if (titulo.trim().length < 3) { setError('Escribe la meta con al menos 3 caracteres.'); return; }
    setEnviando(true);
    setError('');
    try {
      const response = await authFetch('/api/metas', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ titulo: titulo.trim(), idCurso: idCurso ? Number(idCurso) : null, fechaEntrega: fecha, idUsuarioEstudiante: Number(alumno) })
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) { setError(data.error || 'No se pudo enviar la meta.'); return; }
      setTitulo('');
      onCreada();
    } catch {
      setError('No se pudo conectar con el servidor.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form className="sg-form" onSubmit={enviar}>
      <h3 className="sg-titulo">Nueva meta para un estudiante</h3>
      <label htmlFor="sgm-alumno">Estudiante</label>
      <select id="sgm-alumno" value={alumno} onChange={(e) => { setAlumno(e.target.value); setError(''); }}>
        <option value="">Elige un estudiante…</option>
        {estudiantes.map((e) => <option key={e.idUsuario} value={e.idUsuario}>{e.nombre}</option>)}
      </select>
      <label htmlFor="sgm-titulo">Meta</label>
      <input id="sgm-titulo" type="text" maxLength={150} value={titulo} placeholder="Ej. Resolver los ejercicios de subconsultas"
        onChange={(e) => { setTitulo(e.target.value); setError(''); }} />
      <div className="sg-form-fila">
        <div>
          <label htmlFor="sgm-curso">Curso (opcional)</label>
          <select id="sgm-curso" value={idCurso} onChange={(e) => setIdCurso(e.target.value)}>
            <option value="">Ninguno</option>
            {cursos.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="sgm-fecha">Fecha de entrega</label>
          <input id="sgm-fecha" type="date" value={fecha} min={hoy} onChange={(e) => setFecha(e.target.value)} />
        </div>
      </div>
      {error && <p className="sg-error" role="alert">{error}</p>}
      <div className="sg-form-botones">
        <button type="submit" className="sg-boton primario" disabled={enviando}>{enviando ? 'Enviando…' : 'Enviar meta'}</button>
      </div>
    </form>
  );
}
