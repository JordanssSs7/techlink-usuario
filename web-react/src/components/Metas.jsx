import { useCallback, useEffect, useState } from 'react';
import './Seguimiento.css';
import { useAuth } from '../auth/authContext';
import Cargando from './Cargando';
import { MESES, aIso, deIso, fechaCorta, hoyLocal, lunesDe, sumarDias } from './fechasSeguimiento';

function rotuloSemana(desde) {
  const hasta = sumarDias(desde, 6);
  return desde.getMonth() === hasta.getMonth()
    ? `${desde.getDate()} – ${hasta.getDate()} ${MESES[hasta.getMonth()]} ${hasta.getFullYear()}`
    : `${desde.getDate()} ${MESES[desde.getMonth()]} – ${hasta.getDate()} ${MESES[hasta.getMonth()]} ${hasta.getFullYear()}`;
}

// Barra de progreso de la semana. Se anima sola cuando cambia el porcentaje (al marcar una meta).
export function BarraProgreso({ progreso }) {
  return (
    <div className="sg-progreso-caja">
      <div className="sg-progreso-texto">
        <strong>{progreso.completadas} de {progreso.total} {progreso.total === 1 ? 'meta' : 'metas'}</strong>
        <span>{progreso.porcentaje}%</span>
      </div>
      <div className="sg-progreso" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={progreso.porcentaje}
        aria-label="Progreso de la semana">
        <span style={{ width: `${progreso.porcentaje}%` }} />
      </div>
    </div>
  );
}

// Una meta: curso, mentor, fecha de entrega y estado (pendiente, completada o atrasada). El círculo la marca o la desmarca.
function FilaMeta({ meta, ocupada, onMarcar, onQuitar }) {
  const estado = meta.completada ? 'Completada' : meta.atrasada ? 'Atrasada' : 'Pendiente';
  return (
    <li className={`sg-meta${meta.completada ? ' hecha' : ''}${meta.atrasada ? ' atrasada' : ''}`}>
      <button
        type="button"
        role="checkbox"
        aria-checked={meta.completada}
        aria-label={`${meta.completada ? 'Desmarcar' : 'Marcar como cumplida'}: ${meta.titulo}`}
        className="sg-check"
        disabled={ocupada}
        onClick={() => onMarcar(meta)}
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M5 12.5 10 17.5 19 7" />
        </svg>
      </button>
      <div className="sg-meta-cuerpo">
        <p className="sg-meta-titulo">{meta.titulo}</p>
        <p className="sg-meta-datos">
          <span className="sg-estado">{estado}</span>
          <span>Entrega: {fechaCorta(meta.fechaEntrega)}</span>
          {meta.curso && <span>{meta.curso}</span>}
          <span>{meta.mentor ? `Con tu mentor ${meta.mentor}` : 'Meta propia'}</span>
        </p>
      </div>
      {meta.propia && (
        <button type="button" className="sg-quitar" aria-label={`Quitar la meta: ${meta.titulo}`} disabled={ocupada} onClick={() => onQuitar(meta)}>×</button>
      )}
    </li>
  );
}

// HU-11: las metas de la semana ordenadas por fecha de entrega, con la barra de progreso. Al marcar una meta se guarda en
// la base y el progreso se actualiza al instante.
export default function Metas() {
  const { authFetch } = useAuth();
  const [semana, setSemana] = useState(() => lunesDe(hoyLocal()));
  const [datos, setDatos] = useState(null);
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');
  const [version, setVersion] = useState(0);
  const [ocupada, setOcupada] = useState(false);
  const [cursos, setCursos] = useState([]);
  const [formulario, setFormulario] = useState(false);
  const recargar = useCallback(() => setVersion((v) => v + 1), []);

  const semanaIso = aIso(semana);

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const response = await authFetch(`/api/metas?semana=${semanaIso}`);
        const data = await response.json().catch(() => null);
        if (!response.ok) throw new Error(data?.error || '');
        if (!activo) return;
        setDatos(data);
        setError('');
      } catch (e) {
        if (!activo) return;
        setError(e.message || 'No se pudieron cargar tus metas.');
        setDatos((actual) => actual ?? { semana: { total: 0, completadas: 0, porcentaje: 0 }, metas: [], atrasadas: [], proximas: [] });
      }
    })();
    return () => { activo = false; };
  }, [authFetch, semanaIso, version]);

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const response = await authFetch('/api/cursos');
        const data = await response.json().catch(() => []);
        if (activo && response.ok && Array.isArray(data)) setCursos(data);
      } catch { /* sin la lista de cursos el formulario sigue funcionando sin curso */ }
    })();
    return () => { activo = false; };
  }, [authFetch]);

  useEffect(() => {
    if (!aviso) return undefined;
    const t = setTimeout(() => setAviso(''), 5000);
    return () => clearTimeout(t);
  }, [aviso]);

  async function marcar(meta) {
    const nueva = !meta.completada;
    setOcupada(true);
    setError('');
    // Se refleja al instante y luego se confirma con lo que responde el servidor.
    const cambiar = (lista) => lista.map((m) => (m.id === meta.id ? { ...m, completada: nueva, atrasada: !nueva && m.fechaEntrega < aIso(hoyLocal()) } : m));
    setDatos((d) => {
      const metas = cambiar(d.metas);
      const hechas = metas.filter((m) => m.completada).length;
      return { ...d, metas, atrasadas: cambiar(d.atrasadas).filter((m) => !m.completada), proximas: cambiar(d.proximas).filter((m) => !m.completada),
        semana: { ...d.semana, completadas: hechas, porcentaje: metas.length ? Math.round((100 * hechas) / metas.length) : 0 } };
    });
    try {
      const response = await authFetch(`/api/metas/${meta.id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ completada: nueva })
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.error || 'No se pudo guardar el cambio.');
      if (nueva) setAviso('¡Meta cumplida! Se guardó tu avance.');
      // Si la meta era atrasada, ahora cuenta en su semana: se vuelve a pedir la lista para ver todo en su lugar.
      if (data.progreso && data.progreso.desde === aIso(semana)) setDatos((d) => ({ ...d, semana: data.progreso }));
      else recargar();
    } catch (e) {
      setError(e.message || 'No se pudo conectar con el servidor.');
      recargar();
    } finally {
      setOcupada(false);
    }
  }

  async function quitar(meta) {
    setOcupada(true);
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
    } finally {
      setOcupada(false);
    }
  }

  const irASemana = (n) => { setDatos(null); setSemana(sumarDias(semana, 7 * n)); };
  const esActual = semanaIso === aIso(lunesDe(hoyLocal()));

  return (
    <section className="sg" aria-label="Metas semanales">
      <div className="sg-barra">
        <div className="sg-navegar">
          <button type="button" className="sg-boton icono" aria-label="Semana anterior" onClick={() => irASemana(-1)}>‹</button>
          <button type="button" className="sg-boton" disabled={esActual} onClick={() => { setDatos(null); setSemana(lunesDe(hoyLocal())); }}>Esta semana</button>
          <button type="button" className="sg-boton icono" aria-label="Semana siguiente" onClick={() => irASemana(1)}>›</button>
        </div>
        <h2 className="sg-rango">{rotuloSemana(semana)}</h2>
        <button type="button" className="sg-boton primario" onClick={() => setFormulario((f) => !f)} aria-expanded={formulario}>
          {formulario ? 'Cerrar' : 'Nueva meta'}
        </button>
      </div>

      {formulario && (
        <FormularioMeta cursos={cursos} semana={semana}
          onCreada={(creada) => {
            setFormulario(false);
            setAviso('Meta agregada.');
            // Se muestra la semana en la que cae la meta para que se vea de inmediato.
            const suSemana = lunesDe(deIso(creada.fechaEntrega));
            if (aIso(suSemana) !== semanaIso) { setDatos(null); setSemana(suSemana); } else recargar();
          }} />
      )}

      {aviso && <p className="sg-aviso" role="status">{aviso}</p>}
      {error && <p className="sg-error" role="alert">{error}</p>}
      {datos === null && <Cargando texto="Cargando tus metas…" />}

      {datos !== null && (
        <>
          <BarraProgreso progreso={datos.semana} />

          {datos.atrasadas.length > 0 && (
            <>
              <h3 className="sg-titulo">Atrasadas de semanas anteriores</h3>
              <ul className="sg-lista">
                {datos.atrasadas.map((m) => <FilaMeta key={m.id} meta={m} ocupada={ocupada} onMarcar={marcar} onQuitar={quitar} />)}
              </ul>
            </>
          )}

          <h3 className="sg-titulo">Metas de la semana</h3>
          {datos.metas.length === 0 ? (
            <p className="sg-vacio">No tienes metas para esta semana. Agrega una con "Nueva meta" o espera las que acuerdes con tu mentor.</p>
          ) : (
            <ul className="sg-lista">
              {datos.metas.map((m) => <FilaMeta key={m.id} meta={m} ocupada={ocupada} onMarcar={marcar} onQuitar={quitar} />)}
            </ul>
          )}

          {datos.proximas.length > 0 && (
            <>
              <h3 className="sg-titulo">Próximas semanas</h3>
              <ul className="sg-lista">
                {datos.proximas.map((m) => <FilaMeta key={m.id} meta={m} ocupada={ocupada} onMarcar={marcar} onQuitar={quitar} />)}
              </ul>
            </>
          )}
        </>
      )}
    </section>
  );
}

// Formulario para agregarse una meta a sí mismo.
function FormularioMeta({ cursos, semana, onCreada }) {
  const { authFetch } = useAuth();
  const hoy = aIso(hoyLocal());
  const [titulo, setTitulo] = useState('');
  const [idCurso, setIdCurso] = useState('');
  // La fecha propuesta es el viernes de la semana que se está mirando (o hoy si ya pasó).
  const viernes = aIso(sumarDias(semana, 4));
  const [fecha, setFecha] = useState(viernes >= hoy ? viernes : hoy);
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);

  async function enviar(e) {
    e.preventDefault();
    if (titulo.trim().length < 3) { setError('Escribe la meta con al menos 3 caracteres.'); return; }
    setEnviando(true);
    setError('');
    try {
      const response = await authFetch('/api/metas', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ titulo: titulo.trim(), idCurso: idCurso ? Number(idCurso) : null, fechaEntrega: fecha })
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) { setError(data.error || 'No se pudo agregar la meta.'); return; }
      onCreada(data);
    } catch {
      setError('No se pudo conectar con el servidor.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form className="sg-form" onSubmit={enviar}>
      <label htmlFor="sg-meta-titulo">¿Qué quieres lograr?</label>
      <input id="sg-meta-titulo" type="text" maxLength={150} value={titulo} placeholder="Ej. Resolver los ejercicios de JOINs"
        onChange={(e) => { setTitulo(e.target.value); setError(''); }} />
      <div className="sg-form-fila">
        <div>
          <label htmlFor="sg-meta-curso">Curso (opcional)</label>
          <select id="sg-meta-curso" value={idCurso} onChange={(e) => setIdCurso(e.target.value)}>
            <option value="">Ninguno</option>
            {cursos.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="sg-meta-fecha">Fecha de entrega</label>
          <input id="sg-meta-fecha" type="date" value={fecha} min={hoy} onChange={(e) => setFecha(e.target.value)} />
        </div>
      </div>
      {error && <p className="sg-error" role="alert">{error}</p>}
      <div className="sg-form-botones">
        <button type="submit" className="sg-boton primario" disabled={enviando}>{enviando ? 'Agregando…' : 'Agregar meta'}</button>
      </div>
    </form>
  );
}
