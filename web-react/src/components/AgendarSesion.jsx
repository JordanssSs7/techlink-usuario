
import { useEffect, useState } from 'react';
import { useAuth } from '../auth/authContext';
import Cargando from './Cargando';
import Desplegable from './Desplegable';

const DIAS = [
  'Domingo', 'Lunes', 'Martes', 'Miércoles',
  'Jueves', 'Viernes', 'Sábado'
];

import { hora12 } from './horas';

const horaCorta = (valor) => hora12(valor);

function obtenerDiaSemana(fecha) {
  const [anio, mes, dia] = fecha.split('-').map(Number);
  // Se arma con un año fijo y luego se pone el real: new Date(26, …) tomaría el año 26 como 1926.
  const f = new Date(2000, mes - 1, dia);
  f.setFullYear(anio);
  const diaJS = f.getDay();
  return diaJS === 0 ? 7 : diaJS;
}

function obtenerFechaLocal() {
  const ahora = new Date();
  const anio = ahora.getFullYear();
  const mes = String(ahora.getMonth() + 1).padStart(2, '0');
  const dia = String(ahora.getDate()).padStart(2, '0');
  return `${anio}-${mes}-${dia}`;
}

// Convierte "HH:mm[:ss]" en minutos desde la medianoche y al revés.
const aMinutos = (t) => Number(t.slice(0, 2)) * 60 + Number(t.slice(3, 5));
const deMinutos = (m) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}:00`;

// Las franjas en que el mentor atiende ese día (cada una con su id, para reservar dentro de ella).
function ventanasDelDia(disponibilidades, fecha) {
  // Mientras se escribe el año (por ejemplo 0026) o si la fecha ya pasó, no hay horarios que mostrar.
  if (!fecha || fecha < obtenerFechaLocal()) return [];
  const dia = obtenerDiaSemana(fecha);
  return disponibilidades
    .filter((d) => d.diaSemana === dia)
    .map((d) => ({ idDisponibilidad: d.idDisponibilidad, inicio: aMinutos(d.horaInicio), fin: aMinutos(d.horaFin) }));
}

// Horas que se pueden elegir dentro de una franja, de media en media hora: [desde, hasta].
function puntos(desde, hasta) {
  const lista = [];
  for (let t = desde; t <= hasta; t += 30) lista.push(t);
  return lista;
}

const mayuscula = (t) => t.charAt(0).toUpperCase() + t.slice(1);
const aIso = (a, m, d) => `${a}-${String(m + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
const fechaLarga = (iso) => {
  const [a, m, d] = iso.split('-').map(Number);
  return mayuscula(new Date(a, m - 1, d).toLocaleDateString('es-PE', { weekday: 'long', day: 'numeric', month: 'long' }));
};
const duracionTexto = (min) => {
  const h = Math.floor(min / 60);
  const r = min % 60;
  if (h === 0) return `${r} min`;
  return r === 0 ? `${h} h` : `${h} h ${r} min`;
};
const iniciales = (m) => `${(m.nombres || '')[0] ?? ''}${(m.apellidos || '')[0] ?? ''}`.toUpperCase();

const SEMANA = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];

// Los próximos días en que el mentor atiende (solo esos), con sus franjas. Hoy cuenta si aún queda alguna franja por delante.
function proximosDias(disponibilidades) {
  const ahora = new Date();
  const minutos = ahora.getHours() * 60 + ahora.getMinutes();
  const lista = [];
  for (let i = 0; i < 150 && lista.length < 60; i += 1) {
    const d = new Date(ahora.getFullYear(), ahora.getMonth(), ahora.getDate() + i);
    const iso = aIso(d.getFullYear(), d.getMonth(), d.getDate());
    const franjas = disponibilidades.filter((x) => x.diaSemana === obtenerDiaSemana(iso));
    if (franjas.length === 0) continue;
    if (i === 0 && franjas.every((f) => aMinutos(f.horaFin) <= minutos)) continue;
    lista.push({
      iso,
      etiqueta: i === 0 ? 'Hoy' : i === 1 ? 'Mañana' : '',
      semana: SEMANA[d.getDay()],
      numero: d.getDate(),
      mes: d.toLocaleDateString('es-PE', { month: 'short' }).replace('.', ''),
      horario: `${horaCorta(franjas[0].horaInicio).replace(':00', '')} – ${horaCorta(franjas[0].horaFin).replace(':00', '')}`,
      extra: franjas.length - 1
    });
  }
  return lista;
}

// Tarjetas con los próximos días disponibles: sin celdas vacías, solo lo que se puede elegir.
function DiasDisponibles({ valor, onElegir, disponibilidades }) {
  const [cantidad, setCantidad] = useState(12);
  const dias = proximosDias(disponibilidades);
  if (dias.length === 0) return <p className="bm-ayuda">El mentor todavía no tiene días disponibles.</p>;
  return (
    <div className="ag-dias">
      <ul className="ag-dias-lista" role="radiogroup" aria-label="Día de la sesión">
        {dias.slice(0, cantidad).map((x, i) => (
          <li key={x.iso}>
            <button type="button" role="radio" aria-checked={valor === x.iso} className={`ag-dia-card${valor === x.iso ? ' elegido' : ''}`}
              style={{ '--i': i }} onClick={() => onElegir(x.iso)}>
              {x.etiqueta && <em>{x.etiqueta}</em>}
              <span className="ag-dia-sem">{x.semana}</span>
              <strong>{x.numero}</strong>
              <span className="ag-dia-mes">{x.mes}</span>
              <small>{x.horario}{x.extra > 0 ? ` +${x.extra}` : ''}</small>
            </button>
          </li>
        ))}
      </ul>
      {dias.length > cantidad && (
        <button type="button" className="ag-mas" onClick={() => setCantidad((c) => c + 12)}>
          Ver más fechas
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg>
        </button>
      )}
    </div>
  );
}

// HU-07: agendar una sesión. HU-19: con "reprogramar" (una sesión del historial) mueve esa sesión a otro horario
// del mismo mentor en lugar de crear una nueva. HU-20: al agendar se puede elegir el curso a reforzar.
export default function AgendarSesion({ mentor, onVolver, reprogramar = null, onConfirmado }) {
  const { authFetch } = useAuth();
  const reprogramando = Boolean(reprogramar);

  const [cursos, setCursos] = useState([]);
  const [cursoId, setCursoId] = useState('');

  const [disponibilidades, setDisponibilidades] = useState([]);
  const [fecha, setFecha] = useState('');
  const [bloqueSeleccionado, setBloqueSeleccionado] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState('');
  const [confirmacion, setConfirmacion] = useState(null);

  useEffect(() => {
    let activo = true;

    async function cargarHorarios() {
      try {
        const respuesta = await authFetch(
          `/api/sesiones/disponibilidades/mentor/${mentor.idMentor}`
        );

        if (!respuesta.ok) {
          throw new Error('No se pudieron consultar los horarios.');
        }

        const datos = await respuesta.json();

        if (activo) setDisponibilidades(datos);
      } catch {
        if (activo) {
          setError('No se pudieron cargar los horarios del mentor.');
        }
      } finally {
        if (activo) setCargando(false);
      }
    }

    cargarHorarios();

    return () => {
      activo = false;
    };
  }, [authFetch, mentor.idMentor]);

  // Cursos para elegir: los que el estudiante y el mentor tienen en común (si se conocen) o todo el catálogo.
  useEffect(() => {
    if (reprogramando) return undefined;
    let activo = true;
    (async () => {
      try {
        const respuesta = await authFetch('/api/cursos');
        if (!respuesta.ok) return;
        const catalogo = await respuesta.json();
        const comunes = mentor.cursosEnComun;
        const lista = comunes?.length ? catalogo.filter((c) => comunes.includes(c.nombre)) : catalogo;
        if (activo) {
          setCursos(lista);
          if (lista.length === 1) setCursoId(String(lista[0].id));
        }
      } catch { /* el curso es opcional: sin lista se agenda igual */ }
    })();
    return () => { activo = false; };
  }, [authFetch, mentor.cursosEnComun, reprogramando]);

  const ventanas = ventanasDelDia(disponibilidades, fecha);
  const [ventanaId, setVentanaId] = useState('');
  const [desde, setDesde] = useState('');
  const [hasta, setHasta] = useState('');
  const ventana = ventanas.find((v) => String(v.idDisponibilidad) === String(ventanaId)) || ventanas[0] || null;

  // Se arma la reserva cuando ya se eligió «desde» y «hasta» dentro de la franja.
  function elegirRango(v, d, h) {
    setError('');
    if (v && d !== '' && h !== '' && Number(h) > Number(d)) {
      setBloqueSeleccionado({ idDisponibilidad: v.idDisponibilidad, horaInicio: deMinutos(Number(d)), horaFin: deMinutos(Number(h)) });
    } else {
      setBloqueSeleccionado(null);
    }
  }

  async function confirmarReserva() {
    if (!fecha || !bloqueSeleccionado) {
      setError('Selecciona una fecha y un horario.');
      return;
    }

    setError('');
    setGuardando(true);

    try {
      const respuesta = await authFetch(
        reprogramando ? `/api/sesiones/${reprogramar.id}/reprogramar` : '/api/sesiones',
        {
          method: reprogramando ? 'PATCH' : 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify(reprogramando
            ? {
              disponibilidadId: bloqueSeleccionado.idDisponibilidad,
              fecha,
              horaInicio: bloqueSeleccionado.horaInicio,
              horaFin: bloqueSeleccionado.horaFin
            }
            : {
              mentorId: mentor.idMentor,
              disponibilidadId: bloqueSeleccionado.idDisponibilidad,
              fecha,
              horaInicio: bloqueSeleccionado.horaInicio,
              horaFin: bloqueSeleccionado.horaFin,
              cursoId: cursoId ? Number(cursoId) : null
            })
        }
      );

      if (respuesta.status === 409) {
        const datos = await respuesta.json().catch(() => ({}));
        setError(datos.error || 'Ese horario ya está reservado. Selecciona otro.');
        setBloqueSeleccionado(null);
        return;
      }

      if (!respuesta.ok) {
        const datos = await respuesta.json().catch(() => ({}));
        setError(datos.error || (reprogramando
          ? 'No se pudo reprogramar la sesión. Revisa los datos.'
          : 'No se pudo registrar la sesión. Revisa los datos.'));
        return;
      }

      const datos = await respuesta.json();
      setConfirmacion(datos);
      onConfirmado?.(datos);
    } catch {
      setError('No se pudo conectar con el servidor.');
    } finally {
      setGuardando(false);
    }
  }

  // Al elegir la hora de inicio se propone 1 hora (o lo que quepa en la franja); luego se puede cambiar la duración.
  function elegirInicio(t) {
    const dur = Math.min(60, ventana.fin - t);
    setDesde(t);
    setHasta(t + dur);
    elegirRango(ventana, t, t + dur);
  }
  function elegirDuracion(dur) {
    setHasta(desde + dur);
    elegirRango(ventana, desde, desde + dur);
  }
  function elegirFecha(iso) {
    setFecha(iso);
    setVentanaId('');
    setDesde('');
    setHasta('');
    setBloqueSeleccionado(null);
    setError('');
  }

  const mostrarCurso = !reprogramando && cursos.length > 0;
  const duraciones = ventana && desde !== '' ? puntos(30, ventana.fin - desde).filter((m) => m % 30 === 0 && m <= 240) : [];
  const nombreMentor = `${mentor.nombres} ${mentor.apellidos || ''}`.trim();

  if (confirmacion) {
    return (
      <section className="bm-panel ag ag-listo">
        <svg className="ag-check" viewBox="0 0 52 52" aria-hidden="true">
          <circle cx="26" cy="26" r="24" />
          <path d="m15 27 8 8 15-17" />
        </svg>
        <h2>{reprogramando ? '¡Sesión reprogramada!' : '¡Solicitud enviada!'}</h2>
        <p className="ag-listo-texto">{reprogramando
          ? 'Tu mentoría cambió de horario y avisamos al mentor.'
          : 'Le pedimos la sesión al mentor. Cuando la acepte te llegará un aviso y aparecerá en «Mis sesiones».'}</p>

        <dl className="ag-ticket">
          <div><dt>Mentor</dt><dd>{nombreMentor}</dd></div>
          <div><dt>Fecha</dt><dd>{fechaLarga(confirmacion.fecha)}</dd></div>
          <div><dt>Horario</dt><dd>{horaCorta(confirmacion.horaInicio)} – {horaCorta(confirmacion.horaFin)}</dd></div>
          <div><dt>Estado</dt><dd><span className="ag-estado">{confirmacion.estado === 'PENDIENTE' ? 'Pendiente de aprobación' : 'Confirmada'}</span></dd></div>
        </dl>

        <button type="button" className="bm-primario" onClick={onVolver}>
          {reprogramando ? 'Volver a mis sesiones' : 'Volver a los mentores'}
        </button>
      </section>
    );
  }

  // Los bloques se numeran según los que se muestren (el curso es opcional y no siempre aparece).
  const nCurso = mostrarCurso ? 1 : 0;
  return (
    <section className="bm-panel ag">
      <header className="ag-cab">
        <span className="ag-avatar" aria-hidden="true">{iniciales(mentor)}</span>
        <div>
          <p className="ag-etiqueta">{reprogramando ? 'Reprogramar' : 'Nueva sesión'}</p>
          <h2>{reprogramando ? 'Elige otro horario' : 'Agendar sesión'}</h2>
          <p className="ag-mentor">con <strong>{nombreMentor}</strong>{mentor.especialidad ? ` · ${mentor.especialidad}` : ''}</p>
        </div>
      </header>

      {reprogramando && (
        <p className="bm-ayuda">Solo se puede cambiar con al menos 2 horas de anticipación.</p>
      )}

      {error && <p className="bm-error" role="alert">{error}</p>}

      {cargando ? (
        <Cargando texto="Cargando horarios…" />
      ) : (
        <>
          {disponibilidades.length > 0 && (
            <div className="ag-atiende">
              <span>Atiende</span>
              <ul>
                {disponibilidades.map((d) => (
                  <li key={d.idDisponibilidad}><strong>{(DIAS[d.diaSemana % 7] || `Día ${d.diaSemana}`).slice(0, 3)}</strong> {horaCorta(d.horaInicio)} – {horaCorta(d.horaFin)}</li>
                ))}
              </ul>
            </div>
          )}

          <div className={`ag-cuerpo${mostrarCurso ? ' con-curso' : ''}`}>
            <div className="ag-col">
              <div className="ag-bloque ag-dia">
                <h3><span className="ag-num">1</span> Elige el día<small>Días en que atiende</small></h3>
                <DiasDisponibles valor={fecha} onElegir={elegirFecha} disponibilidades={disponibilidades} />
              </div>
            </div>

            <div className="ag-col">
              {mostrarCurso && (
                <div className="ag-bloque">
                  <h3><span className="ag-num">2</span> Curso a reforzar <small>opcional</small></h3>
                  <Desplegable id="curso-sesion" value={cursoId} onChange={(e) => setCursoId(e.target.value)} aria-label="Curso que quieres reforzar">
                    <option value="">Sin especificar</option>
                    {cursos.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
                  </Desplegable>
                </div>
              )}

              <div className="ag-bloque ag-horas">
                <h3><span className="ag-num">{nCurso + 2}</span> Elige la hora<small>Empieza y dura</small></h3>

                {!fecha && (
                  <div className="ag-vacio">
                    <svg width="46" height="46" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" />
                    </svg>
                    <p>Primero elige un día en el calendario y aquí verás las horas disponibles.</p>
                  </div>
                )}

                {fecha && !ventana && (
                  <p className="bm-ayuda">{fecha < obtenerFechaLocal() ? 'Elige una fecha desde hoy.' : 'El mentor no tiene horarios para ese día.'}</p>
                )}

                {fecha && ventana && (
                  <div className="ag-rango" key={fecha}>
                    <p className="ag-fecha-larga">{fechaLarga(fecha)}</p>

                    {ventanas.length > 1 && (
                      <div className="ag-franjas" role="tablist" aria-label="Franja del mentor">
                        {ventanas.map((v) => (
                          <button key={v.idDisponibilidad} type="button" role="tab" aria-selected={v.idDisponibilidad === ventana.idDisponibilidad}
                            className={`ag-franja${v.idDisponibilidad === ventana.idDisponibilidad ? ' activa' : ''}`}
                            onClick={() => { setVentanaId(v.idDisponibilidad); setDesde(''); setHasta(''); setBloqueSeleccionado(null); }}>
                            {hora12(deMinutos(v.inicio))} – {hora12(deMinutos(v.fin))}
                          </button>
                        ))}
                      </div>
                    )}

                    <p className="ag-sub">¿A qué hora empiezas?</p>
                    <div className="ag-chips" role="radiogroup" aria-label="Hora de inicio">
                      {puntos(ventana.inicio, ventana.fin - 30).map((t, i) => (
                        <button key={t} type="button" role="radio" aria-checked={desde === t}
                          className={`ag-chip${desde === t ? ' elegido' : ''}`} style={{ '--i': i }} onClick={() => elegirInicio(t)}>
                          {hora12(deMinutos(t))}
                        </button>
                      ))}
                    </div>

                    {desde !== '' && (
                      <div className="ag-duracion">
                        <p className="ag-sub">¿Cuánto tiempo?</p>
                        <div className="ag-chips" role="radiogroup" aria-label="Duración">
                          {duraciones.map((m, i) => (
                            <button key={m} type="button" role="radio" aria-checked={hasta === desde + m}
                              className={`ag-chip${hasta === desde + m ? ' elegido' : ''}`} style={{ '--i': i }} onClick={() => elegirDuracion(m)}>
                              {duracionTexto(m)}
                            </button>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                )}

                <div className={`ag-boleto${bloqueSeleccionado ? ' listo' : ''}`} aria-live="polite">
                  <p className="ag-boleto-tit">Tu reserva</p>
                  <dl>
                    {!reprogramando && <div><dt>Curso</dt><dd>{cursos.find((c) => String(c.id) === String(cursoId))?.nombre || 'Sin especificar'}</dd></div>}
                    <div><dt>Día</dt><dd key={fecha}>{fecha ? fechaLarga(fecha) : 'Sin elegir'}</dd></div>
                    <div><dt>Hora</dt><dd key={bloqueSeleccionado ? `${desde}-${hasta}` : 'x'}>{bloqueSeleccionado
                      ? `${horaCorta(bloqueSeleccionado.horaInicio)} – ${horaCorta(bloqueSeleccionado.horaFin)} · ${duracionTexto(hasta - desde)}`
                      : 'Sin elegir'}</dd></div>
                  </dl>
                </div>
              </div>
            </div>
          </div>

          <footer className="ag-pie">
            <div className={`ag-resumen${bloqueSeleccionado ? ' listo' : ''}`} aria-live="polite">
              {bloqueSeleccionado ? (
                <>
                  <span className="ag-resumen-ico" aria-hidden="true">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><path d="M20 6 9 17l-5-5" /></svg>
                  </span>
                  <strong>Todo listo para enviar</strong>
                </>
              ) : (
                <span className="ag-resumen-vacio">Elige un día y una hora para continuar.</span>
              )}
            </div>
            <div className="ag-botones">
              <button type="button" className="bm-secundario" onClick={onVolver}>Volver</button>
              <button type="button" className="bm-primario" onClick={confirmarReserva} disabled={!bloqueSeleccionado || guardando}>
                {guardando ? 'Confirmando...' : (reprogramando ? 'Confirmar cambio' : 'Confirmar reserva')}
              </button>
            </div>
          </footer>
        </>
      )}
    </section>
  );
}
