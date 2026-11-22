import { useEffect, useRef, useState } from 'react';
import './Estadisticas.css';
import { useAuth } from '../auth/authContext';
import Cargando from './Cargando';

const OPCIONES_MESES = [6, 12, 24];

const horas = (h) => `${Number.isInteger(h) ? h : String(Number(h.toFixed(2))).replace('.', ',')} h`;
const nota = (n) => (n == null ? '—' : Number(n).toFixed(1).replace('.', ','));
const fechaCorta = (iso) => (iso ? new Date(`${iso}T00:00:00`).toLocaleDateString('es-PE', { day: 'numeric', month: 'short', year: 'numeric' }) : '');

// "2026-11" -> "nov" (y el año, para la tabla y las descripciones).
function etiquetaMes(clave, conAnio = false) {
  const [anio, mes] = clave.split('-').map(Number);
  const nombre = new Date(anio, mes - 1, 1).toLocaleDateString('es-PE', { month: 'short' }).replace('.', '');
  return conAnio ? `${nombre} ${anio}` : nombre;
}

const ANCHO = 640;
const ALTO = 220;
const MARGEN = { izq: 38, der: 12, arriba: 14, abajo: 28 };

function Ejes({ meses, maximo, formato, divisiones }) {
  const util = ALTO - MARGEN.arriba - MARGEN.abajo;
  const paso = meses.length > 12 ? 2 : 1;
  const anchoMes = (ANCHO - MARGEN.izq - MARGEN.der) / meses.length;
  return (
    <>
      {Array.from({ length: divisiones + 1 }, (_, i) => {
        const valor = (maximo / divisiones) * i;
        const y = ALTO - MARGEN.abajo - (util * i) / divisiones;
        return (
          <g key={i}>
            <line x1={MARGEN.izq} x2={ANCHO - MARGEN.der} y1={y} y2={y} className="es-rejilla" />
            <text x={MARGEN.izq - 6} y={y + 4} textAnchor="end" className="es-eje">{formato(valor)}</text>
          </g>
        );
      })}
      {meses.map((m, i) => (i % paso === 0 || i === meses.length - 1) && (
        <text key={m.mes} x={MARGEN.izq + anchoMes * (i + 0.5)} y={ALTO - 8} textAnchor="middle" className="es-eje">
          {etiquetaMes(m.mes)}
        </text>
      ))}
    </>
  );
}

// Barras: horas dictadas por mes.
function GraficoHoras({ meses }) {
  const maximo = Math.max(2, Math.ceil(Math.max(...meses.map((m) => m.horas)) / 2) * 2);
  const util = ALTO - MARGEN.arriba - MARGEN.abajo;
  const anchoMes = (ANCHO - MARGEN.izq - MARGEN.der) / meses.length;
  return (
    <svg viewBox={`0 0 ${ANCHO} ${ALTO}`} className="es-grafico" role="img" aria-label="Horas dictadas por mes">
      <Ejes meses={meses} maximo={maximo} divisiones={4} formato={(v) => Number(v.toFixed(1))} />
      {meses.map((m, i) => {
        const alto = (m.horas / maximo) * util;
        return (
          <rect key={m.mes} x={MARGEN.izq + anchoMes * i + anchoMes * 0.18} y={ALTO - MARGEN.abajo - alto}
            width={anchoMes * 0.64} height={alto} rx="3" className="es-barra">
            <title>{`${etiquetaMes(m.mes, true)}: ${horas(m.horas)} en ${m.sesiones} ${m.sesiones === 1 ? 'sesión' : 'sesiones'}`}</title>
          </rect>
        );
      })}
    </svg>
  );
}

// Línea: evolución del promedio de estrellas (escala de 0 a 5). Los meses sin calificaciones quedan sin punto.
function GraficoCalificacion({ meses }) {
  const util = ALTO - MARGEN.arriba - MARGEN.abajo;
  const anchoMes = (ANCHO - MARGEN.izq - MARGEN.der) / meses.length;
  const puntos = meses
    .map((m, i) => (m.promedio == null ? null : {
      x: MARGEN.izq + anchoMes * (i + 0.5),
      y: ALTO - MARGEN.abajo - (Number(m.promedio) / 5) * util,
      m
    }))
    .filter(Boolean);
  return (
    <svg viewBox={`0 0 ${ANCHO} ${ALTO}`} className="es-grafico" role="img" aria-label="Evolución de la calificación promedio por mes">
      <Ejes meses={meses} maximo={5} divisiones={5} formato={(v) => Math.round(v)} />
      {puntos.length > 1 && <polyline points={puntos.map((p) => `${p.x},${p.y}`).join(' ')} className="es-linea" />}
      {puntos.map((p) => (
        <circle key={p.m.mes} cx={p.x} cy={p.y} r="4.5" className="es-punto">
          <title>{`${etiquetaMes(p.m.mes, true)}: ${nota(p.m.promedio)} ★ (${p.m.calificaciones} ${p.m.calificaciones === 1 ? 'calificación' : 'calificaciones'})`}</title>
        </circle>
      ))}
    </svg>
  );
}

// HU-27: constancia de horas de mentoría. Muestra lo que dirá, la abre en una vista previa y se descarga en PDF.
function Constancia() {
  const { authFetch } = useAuth();
  const [datos, setDatos] = useState(null);
  const [error, setError] = useState('');
  const [vista, setVista] = useState(null);
  const [ocupado, setOcupado] = useState(false);
  const urlRef = useRef(null);

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const response = await authFetch('/api/mentor/constancia');
        const data = await response.json().catch(() => null);
        if (!response.ok) throw new Error(data?.error || '');
        if (activo) setDatos(data);
      } catch (e) {
        if (activo) setError(e.message || 'No se pudo cargar la constancia.');
      }
    })();
    return () => { activo = false; };
  }, [authFetch]);

  // Libera el archivo temporal de la vista previa al salir de la pantalla.
  useEffect(() => () => { if (urlRef.current) URL.revokeObjectURL(urlRef.current); }, []);

  // Escape cierra la vista previa.
  useEffect(() => {
    if (!vista) return undefined;
    const escape = (e) => { if (e.key === 'Escape') cerrar(); };
    document.addEventListener('keydown', escape);
    return () => document.removeEventListener('keydown', escape);
  }, [vista]);

  async function pedirPdf(descargar) {
    const response = await authFetch(`/api/mentor/constancia/pdf${descargar ? '?descargar=true' : ''}`);
    if (!response.ok) {
      const data = await response.json().catch(() => null);
      throw new Error(data?.error || 'No se pudo generar la constancia.');
    }
    return URL.createObjectURL(await response.blob());
  }

  async function verVistaPrevia() {
    setError('');
    setOcupado(true);
    try {
      const url = await pedirPdf(false);
      if (urlRef.current) URL.revokeObjectURL(urlRef.current);
      urlRef.current = url;
      setVista(url);
    } catch (e) {
      setError(e.message);
    } finally {
      setOcupado(false);
    }
  }

  async function descargar() {
    setError('');
    setOcupado(true);
    try {
      const url = await pedirPdf(true);
      const enlace = document.createElement('a');
      enlace.href = url;
      enlace.download = 'constancia-mentoria-techlink.pdf';
      document.body.appendChild(enlace);
      enlace.click();
      enlace.remove();
      setTimeout(() => URL.revokeObjectURL(url), 10000);
    } catch (e) {
      setError(e.message);
    } finally {
      setOcupado(false);
    }
  }

  function cerrar() {
    setVista(null);
  }

  return (
    <section className="es-tarjeta" aria-labelledby="es-constancia">
      <h2 id="es-constancia" className="es-titulo">Constancia de horas de mentoría</h2>
      <p className="es-sub">Un documento en PDF con tu nombre, el total de horas y los cursos que atendiste, para acreditar tu participación.</p>
      {error && <p className="es-error" role="alert">{error}</p>}
      {!datos && !error && <Cargando texto="Preparando tu constancia…" />}

      {datos && !datos.puedeEmitir && (
        <p className="es-vacio">Todavía no tienes sesiones completadas. Cuando termines tu primera sesión podrás descargar tu constancia.</p>
      )}

      {datos?.puedeEmitir && (
        <>
          <p className="es-resumen-constancia">
            <strong>{horas(datos.horasTotales)}</strong> en <strong>{datos.sesionesCompletadas}</strong> {datos.sesionesCompletadas === 1 ? 'sesión' : 'sesiones'}
            {' · '}del {fechaCorta(datos.desde)} al {fechaCorta(datos.hasta)}
          </p>
          <ul className="es-cursos">
            {datos.cursos.map((c) => (
              <li key={c.curso}><span>{c.curso}</span><span>{c.sesiones} {c.sesiones === 1 ? 'sesión' : 'sesiones'} · {horas(c.horas)}</span></li>
            ))}
          </ul>
          <div className="es-acciones">
            <button type="button" className="es-boton" onClick={verVistaPrevia} disabled={ocupado}>Ver vista previa</button>
            <button type="button" className="es-boton primario" onClick={descargar} disabled={ocupado}>Descargar PDF</button>
          </div>
        </>
      )}

      {vista && (
        <div className="es-velo" role="dialog" aria-modal="true" aria-label="Vista previa de la constancia" onClick={cerrar}>
          <div className="es-modal" onClick={(e) => e.stopPropagation()}>
            <div className="es-modal-cab">
              <strong>Vista previa de la constancia</strong>
              <div className="es-acciones">
                <button type="button" className="es-boton primario" onClick={descargar} disabled={ocupado}>Descargar PDF</button>
                <button type="button" className="es-boton" onClick={cerrar}>Cerrar</button>
              </div>
            </div>
            <iframe className="es-pdf" src={vista} title="Vista previa de la constancia en PDF" />
          </div>
        </div>
      )}
    </section>
  );
}

// HU-28: panel de estadísticas del mentor (sesiones, horas y promedio por mes, con el gráfico de evolución de la calificación)
// y HU-27: su constancia de horas.
export default function EstadisticasMentor() {
  const { authFetch } = useAuth();
  const [mesesPedidos, setMesesPedidos] = useState(12);
  const [respuesta, setRespuesta] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const response = await authFetch(`/api/mentor/estadisticas?meses=${mesesPedidos}`);
        const data = await response.json().catch(() => null);
        if (!response.ok) throw new Error(data?.error || '');
        if (!activo) return;
        setRespuesta({ meses: mesesPedidos, datos: data });
        setError('');
      } catch (e) {
        if (activo) setError(e.message || 'No se pudieron cargar tus estadísticas.');
      }
    })();
    return () => { activo = false; };
  }, [authFetch, mesesPedidos]);

  const datos = respuesta?.meses === mesesPedidos ? respuesta.datos : null;
  const resumen = datos?.resumen;
  const sinActividad = datos && datos.meses.every((m) => m.sesiones === 0 && m.calificaciones === 0);

  return (
    <div className="es">
      {error && <p className="es-error" role="alert">{error}</p>}
      {!datos && !error && <Cargando texto="Cargando tus estadísticas…" />}

      {datos && (
        <>
          <ul className="es-kpis">
            <li><span className="es-kpi-valor">{horas(resumen.horasTotales)}</span><span className="es-kpi-nombre">Horas dictadas</span></li>
            <li><span className="es-kpi-valor">{resumen.sesionesCompletadas}</span><span className="es-kpi-nombre">Sesiones completadas</span></li>
            <li>
              <span className="es-kpi-valor">{nota(resumen.promedio)}{resumen.promedio != null && <small> ★</small>}</span>
              <span className="es-kpi-nombre">Calificación promedio{resumen.totalCalificaciones > 0 ? ` (${resumen.totalCalificaciones})` : ''}</span>
            </li>
            <li><span className="es-kpi-valor">{resumen.sesionesProximas}</span><span className="es-kpi-nombre">Próximas sesiones</span></li>
          </ul>

          <div className="es-meses" role="group" aria-label="Cantidad de meses">
            <span>Mostrar los últimos</span>
            {OPCIONES_MESES.map((n) => (
              <button key={n} type="button" className={mesesPedidos === n ? 'activo' : ''} aria-pressed={mesesPedidos === n}
                onClick={() => setMesesPedidos(n)}>{n} meses</button>
            ))}
          </div>

          {sinActividad && (
            <p className="es-vacio">Todavía no tienes sesiones ni calificaciones en este período. Cuando completes sesiones, aquí verás tu evolución.</p>
          )}

          <section className="es-tarjeta" aria-labelledby="es-horas">
            <h2 id="es-horas" className="es-titulo">Horas dictadas por mes</h2>
            <GraficoHoras meses={datos.meses} />
          </section>

          <section className="es-tarjeta" aria-labelledby="es-nota">
            <h2 id="es-nota" className="es-titulo">Evolución de tu calificación</h2>
            <GraficoCalificacion meses={datos.meses} />
            <p className="es-sub">El promedio de cada mes se calcula con las calificaciones de las sesiones que diste en ese mes.</p>
          </section>

          <section className="es-tarjeta" aria-labelledby="es-tabla">
            <h2 id="es-tabla" className="es-titulo">Detalle por mes</h2>
            <div className="es-tabla-scroll">
              <table className="es-tabla">
                <thead>
                  <tr><th>Mes</th><th>Sesiones</th><th>Horas</th><th>Promedio</th><th>Calificaciones</th></tr>
                </thead>
                <tbody>
                  {[...datos.meses].reverse().map((m) => (
                    <tr key={m.mes}>
                      <td>{etiquetaMes(m.mes, true)}</td>
                      <td>{m.sesiones}</td>
                      <td>{m.horas ? horas(m.horas) : '—'}</td>
                      <td>{nota(m.promedio)}</td>
                      <td>{m.calificaciones || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}

      <Constancia />
    </div>
  );
}
