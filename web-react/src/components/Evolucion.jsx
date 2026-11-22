import { useCallback, useEffect, useState } from 'react';
import './Seguimiento.css';
import { useAuth } from '../auth/authContext';
import Cargando from './Cargando';
import { fechaCorta } from './fechasSeguimiento';

const horas = (h) => `${Number.isInteger(h) ? h : h.toFixed(1)} h`;
const decimal = (n) => (n === null || n === undefined ? '—' : n.toFixed(1));

// Gráfico interactivo: las barras son las horas de asesoría recibidas y la línea, la autoevaluación (de 1 a 5).
// Al pasar el mouse o enfocar con el teclado un periodo se muestra su detalle debajo.
function GraficoEvolucion({ periodos, activo, onActivar }) {
  const ancho = 720;
  const alto = 280;
  const izq = 44;
  const der = 40;
  const arriba = 16;
  const abajo = 38;
  const areaAncho = ancho - izq - der;
  const areaAlto = alto - arriba - abajo;
  const ranura = areaAncho / periodos.length;
  const maximo = Math.max(2, Math.ceil(Math.max(...periodos.map((p) => p.horas))));
  const yAuto = (v) => arriba + areaAlto * (1 - (v - 1) / 4);
  const cx = (i) => izq + ranura * i + ranura / 2;

  const puntos = periodos.map((p, i) => (p.autoevaluacion === null ? null : [cx(i), yAuto(p.autoevaluacion)]));
  const trazo = puntos.filter(Boolean).map((p, i) => `${i === 0 ? 'M' : 'L'}${p[0].toFixed(1)} ${p[1].toFixed(1)}`).join(' ');

  return (
    <svg className="sg-grafico" viewBox={`0 0 ${ancho} ${alto}`} role="group" aria-label="Horas de asesoría y autoevaluación por periodo">
      {[0, 0.5, 1].map((f) => (
        <g key={f}>
          <line x1={izq} x2={ancho - der} y1={arriba + areaAlto * (1 - f)} y2={arriba + areaAlto * (1 - f)} className="sg-linea" />
          <text x={izq - 8} y={arriba + areaAlto * (1 - f) + 4} textAnchor="end" className="sg-eje">{horas(Math.round(maximo * f * 10) / 10)}</text>
        </g>
      ))}
      {[1, 3, 5].map((v) => <text key={v} x={ancho - der + 8} y={yAuto(v) + 4} className="sg-eje">{v}★</text>)}

      {periodos.map((p, i) => {
        const altoBarra = areaAlto * (p.horas / maximo);
        const anchoBarra = Math.min(46, ranura * 0.55);
        return (
          <g key={p.desde} className={`sg-ranura${activo === i ? ' activa' : ''}`} tabIndex={0} role="button"
            aria-label={`${p.etiqueta}: ${horas(p.horas)} de asesoría, ${p.sesiones} ${p.sesiones === 1 ? 'sesión' : 'sesiones'}, autoevaluación ${decimal(p.autoevaluacion)}`}
            onMouseEnter={() => onActivar(i)} onFocus={() => onActivar(i)} onClick={() => onActivar(i)}>
            <rect x={izq + ranura * i} y={arriba} width={ranura} height={areaAlto + abajo} fill="transparent" />
            <rect className="sg-barra-g" x={cx(i) - anchoBarra / 2} y={arriba + areaAlto - altoBarra} width={anchoBarra} height={Math.max(altoBarra, 0)} rx="6" />
            <text x={cx(i)} y={alto - 14} textAnchor="middle" className="sg-eje">{p.etiqueta}</text>
          </g>
        );
      })}

      {trazo && <path d={trazo} className="sg-curva" fill="none" />}
      {puntos.map((p, i) => p && <circle key={periodos[i].desde} cx={p[0]} cy={p[1]} r={activo === i ? 6 : 4.5} className="sg-punto" />)}
    </svg>
  );
}

function Tarjeta({ titulo, valor, detalle }) {
  return (
    <div className="sg-kpi">
      <span className="sg-kpi-titulo">{titulo}</span>
      <strong className="sg-kpi-valor">{valor}</strong>
      {detalle && <span className="sg-kpi-detalle">{detalle}</span>}
    </div>
  );
}

// HU-12: evolución del estudiante por semana o por mes: horas de asesoría recibidas, autoevaluación y metas, y cómo cambió
// su autoevaluación antes y después de su primera sesión de mentoría.
export default function Evolucion() {
  const { authFetch } = useAuth();
  const [agrupar, setAgrupar] = useState('semana');
  const [datos, setDatos] = useState(null);
  const [error, setError] = useState('');
  const [activo, setActivo] = useState(null);
  const [version, setVersion] = useState(0);
  const recargar = useCallback(() => setVersion((v) => v + 1), []);

  useEffect(() => {
    let vigente = true;
    (async () => {
      try {
        const response = await authFetch(`/api/evolucion?agrupar=${agrupar}`);
        const data = await response.json().catch(() => null);
        if (!response.ok) throw new Error(data?.error || '');
        if (!vigente) return;
        setDatos(data);
        setActivo(data.periodos.length - 1);
        setError('');
      } catch (e) {
        if (!vigente) return;
        setError(e.message || 'No se pudo cargar tu evolución.');
      }
    })();
    return () => { vigente = false; };
  }, [authFetch, agrupar, version]);

  const periodo = datos && activo !== null ? datos.periodos[activo] : null;
  const { resumen, antesDespues } = datos ?? {};

  return (
    <section className="sg" aria-label="Mi evolución">
      <div className="sg-barra">
        <h2 className="sg-rango">{agrupar === 'semana' ? 'Últimas 8 semanas' : 'Últimos 6 meses'}</h2>
        <div className="sg-vistas" role="tablist" aria-label="Agrupar por">
          {[['semana', 'Semanal'], ['mes', 'Mensual']].map(([valor, texto]) => (
            <button key={valor} type="button" role="tab" aria-selected={agrupar === valor} className={`sg-vista${agrupar === valor ? ' activo' : ''}`}
              onClick={() => { if (agrupar !== valor) { setDatos(null); setAgrupar(valor); } }}>{texto}</button>
          ))}
        </div>
      </div>

      {error && <p className="sg-error" role="alert">{error}</p>}
      {!datos && !error && <Cargando texto="Calculando tu evolución…" />}

      {datos && (
        <>
          <div className="sg-kpis">
            <Tarjeta titulo="Horas de asesoría" valor={horas(resumen.totalHoras)} detalle={`${resumen.totalSesiones} ${resumen.totalSesiones === 1 ? 'sesión' : 'sesiones'}`} />
            <Tarjeta titulo="Autoevaluación" valor={decimal(resumen.promedioAutoevaluacion)} detalle="promedio de 1 a 5" />
            <Tarjeta titulo="Cambio" valor={resumen.variacion === null ? '—' : `${resumen.variacion > 0 ? '+' : ''}${resumen.variacion.toFixed(1)}`}
              detalle="primer y último periodo con datos" />
            <Tarjeta titulo="Metas cumplidas" valor={`${resumen.metasCompletadas}/${resumen.totalMetas}`}
              detalle={resumen.totalMetas ? `${Math.round((100 * resumen.metasCompletadas) / resumen.totalMetas)}%` : 'sin metas aún'} />
          </div>

          <div className="sg-tarjeta">
            <div className="sg-leyenda" aria-hidden="true">
              <span><i className="sg-muestra barra" /> Horas de asesoría</span>
              <span><i className="sg-muestra linea" /> Autoevaluación (1 a 5)</span>
            </div>
            <GraficoEvolucion periodos={datos.periodos} activo={activo} onActivar={setActivo} />
            {periodo && (
              <p className="sg-detalle" aria-live="polite">
                <strong>{periodo.etiqueta}</strong>
                <span>{horas(periodo.horas)} de asesoría · {periodo.sesiones} {periodo.sesiones === 1 ? 'sesión' : 'sesiones'}</span>
                <span>Autoevaluación: {decimal(periodo.autoevaluacion)}{periodo.nota !== null ? ` · Nota promedio: ${decimal(periodo.nota)}` : ''}</span>
                <span>Metas: {periodo.metasCompletadas}/{periodo.metasTotal}</span>
              </p>
            )}
          </div>

          <div className="sg-tarjeta">
            <h3 className="sg-titulo">Antes y después de tus sesiones</h3>
            {antesDespues.primeraSesion === null ? (
              <p className="sg-vacio">Cuando tengas tu primera sesión de mentoría podrás ver cómo cambió tu autoevaluación.</p>
            ) : antesDespues.antes === null || antesDespues.despues === null ? (
              <p className="sg-vacio">Tu primera sesión fue el {fechaCorta(antesDespues.primeraSesion)}. Registra autoevaluaciones antes y después de las sesiones para compararlas.</p>
            ) : (
              <div className="sg-comparacion">
                <div><span>Antes de tu primera sesión</span><strong>{decimal(antesDespues.antes)}</strong>
                  <div className="sg-mini"><span style={{ width: `${(antesDespues.antes / 5) * 100}%` }} /></div></div>
                <div><span>Después</span><strong>{decimal(antesDespues.despues)}</strong>
                  <div className="sg-mini"><span style={{ width: `${(antesDespues.despues / 5) * 100}%` }} /></div></div>
                <p className="sg-nota">Tu primera sesión fue el {fechaCorta(antesDespues.primeraSesion)}.</p>
              </div>
            )}
          </div>

          <Autoevaluaciones onCambio={recargar} />
        </>
      )}
    </section>
  );
}

// Registro de autoevaluaciones: cómo se siente con un curso (de 1 a 5) y, si quiere, la nota que sacó (de 0 a 20).
function Autoevaluaciones({ onCambio }) {
  const { authFetch } = useAuth();
  const [recientes, setRecientes] = useState([]);
  const [cursos, setCursos] = useState([]);
  const [puntaje, setPuntaje] = useState(0);
  const [idCurso, setIdCurso] = useState('');
  const [nota, setNota] = useState('');
  const [comentario, setComentario] = useState('');
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const [r1, r2] = await Promise.all([authFetch('/api/autoevaluaciones'), authFetch('/api/cursos')]);
        const d1 = await r1.json().catch(() => []);
        const d2 = await r2.json().catch(() => []);
        if (!activo) return;
        if (r1.ok && Array.isArray(d1)) setRecientes(d1);
        if (r2.ok && Array.isArray(d2)) setCursos(d2);
      } catch { /* la lista queda vacía */ }
    })();
    return () => { activo = false; };
  }, [authFetch, version]);

  async function enviar(e) {
    e.preventDefault();
    if (puntaje === 0) { setError('Elige de 1 a 5 cómo te sientes.'); return; }
    if (nota !== '' && (Number(nota) < 0 || Number(nota) > 20)) { setError('La nota va de 0 a 20.'); return; }
    setEnviando(true);
    setError('');
    try {
      const response = await authFetch('/api/autoevaluaciones', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ idCurso: idCurso ? Number(idCurso) : null, puntaje, nota: nota === '' ? null : Number(nota), comentario: comentario.trim() || null })
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) { setError(data.error || 'No se pudo guardar la autoevaluación.'); return; }
      setPuntaje(0); setNota(''); setComentario('');
      setVersion((v) => v + 1);
      onCambio();
    } catch {
      setError('No se pudo conectar con el servidor.');
    } finally {
      setEnviando(false);
    }
  }

  async function quitar(id) {
    try {
      const response = await authFetch(`/api/autoevaluaciones/${id}`, { method: 'DELETE' });
      if (response.ok) { setVersion((v) => v + 1); onCambio(); }
    } catch { /* se puede volver a intentar */ }
  }

  return (
    <div className="sg-tarjeta">
      <h3 className="sg-titulo">¿Cómo vas hoy?</h3>
      <p className="sg-nota">Autoevalúate de vez en cuando: con tus respuestas se arma la línea de tu evolución.</p>
      <form className="sg-form plano" onSubmit={enviar}>
        <div className="sg-estrellas" role="radiogroup" aria-label="Cómo te sientes con tu preparación">
          {[1, 2, 3, 4, 5].map((n) => (
            <button key={n} type="button" role="radio" aria-checked={puntaje === n} aria-label={`${n} de 5`}
              className={n <= puntaje ? 'llena' : ''} onClick={() => { setPuntaje(n); setError(''); }}>★</button>
          ))}
        </div>
        <div className="sg-form-fila">
          <div>
            <label htmlFor="sg-auto-curso">Curso (opcional)</label>
            <select id="sg-auto-curso" value={idCurso} onChange={(e) => setIdCurso(e.target.value)}>
              <option value="">Ninguno</option>
              {cursos.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
            </select>
          </div>
          <div>
            <label htmlFor="sg-auto-nota">Nota de 0 a 20 (opcional)</label>
            <input id="sg-auto-nota" type="number" min="0" max="20" step="0.5" value={nota} onChange={(e) => setNota(e.target.value)} />
          </div>
        </div>
        <label htmlFor="sg-auto-comentario">Comentario (opcional)</label>
        <input id="sg-auto-comentario" type="text" maxLength={200} value={comentario} onChange={(e) => setComentario(e.target.value)} />
        {error && <p className="sg-error" role="alert">{error}</p>}
        <div className="sg-form-botones">
          <button type="submit" className="sg-boton primario" disabled={enviando}>{enviando ? 'Guardando…' : 'Registrar'}</button>
        </div>
      </form>

      {recientes.length > 0 && (
        <ul className="sg-recientes">
          {recientes.map((a) => (
            <li key={a.id}>
              <span className="sg-reciente-estrellas" aria-label={`${a.puntaje} de 5`}>{'★'.repeat(a.puntaje)}<i>{'★'.repeat(5 - a.puntaje)}</i></span>
              <span>{fechaCorta(a.fecha)}{a.curso ? ` · ${a.curso}` : ''}{a.nota !== null ? ` · nota ${a.nota}` : ''}{a.comentario ? ` · “${a.comentario}”` : ''}</span>
              <button type="button" className="sg-quitar" aria-label="Quitar esta autoevaluación" onClick={() => quitar(a.id)}>×</button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
