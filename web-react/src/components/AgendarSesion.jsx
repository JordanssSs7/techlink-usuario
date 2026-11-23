
import { useEffect, useState } from 'react';
import { useAuth } from '../auth/authContext';
import Cargando from './Cargando';

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

  if (confirmacion) {
    return (
      <section className="bm-panel">
        <h2>{reprogramando ? '¡Sesión reprogramada!' : '¡Solicitud enviada!'}</h2>
        <p>{reprogramando
          ? 'Tu mentoría cambió de horario y avisamos al mentor.'
          : 'Le pedimos la sesión al mentor. Cuando la acepte te llegará un aviso y aparecerá en «Mis sesiones».'}</p>

        <p><strong>Mentor:</strong> {mentor.nombres} {mentor.apellidos}</p>
        <p><strong>Fecha:</strong> {confirmacion.fecha}</p>
        <p>
          <strong>Horario:</strong>{' '}
          {horaCorta(confirmacion.horaInicio)} –{' '}
          {horaCorta(confirmacion.horaFin)}
        </p>
        <p><strong>Estado:</strong> {confirmacion.estado === 'PENDIENTE' ? 'Pendiente de aprobación del mentor' : 'Confirmada'}</p>

        <button
          type="button"
          className="bm-primario"
          onClick={onVolver}
        >
          {reprogramando ? 'Volver a mis sesiones' : 'Volver a los mentores'}
        </button>
      </section>
    );
  }

  return (
    <section className="bm-panel">
      <h2>{reprogramando ? 'Reprogramar sesión' : 'Agendar sesión'}</h2>

      <p>
        <strong>Mentor:</strong> {mentor.nombres} {mentor.apellidos}
      </p>

      <p className="bm-ayuda">
        {reprogramando
          ? 'Elige el nuevo día y horario. Solo se puede cambiar con al menos 2 horas de anticipación.'
          : 'Selecciona una fecha y un horario disponible para tu mentoría.'}
      </p>

      {error && (
        <p className="bm-error" role="alert">{error}</p>
      )}

      {cargando ? (
        <Cargando texto="Cargando horarios…" />
      ) : (
        <>
          <p><strong>Horarios del mentor:</strong></p>

          {disponibilidades.map((d) => (
            <p key={d.idDisponibilidad}>
              {DIAS[d.diaSemana % 7] || `Día ${d.diaSemana}`}:{' '}
              {horaCorta(d.horaInicio)} – {horaCorta(d.horaFin)}
            </p>
          ))}

          {!reprogramando && cursos.length > 0 && (
            <>
              <label className="bm-etiqueta" htmlFor="curso-sesion">
                Curso que quieres reforzar (opcional)
              </label>
              <select
                id="curso-sesion"
                value={cursoId}
                onChange={(e) => setCursoId(e.target.value)}
              >
                <option value="">Sin especificar</option>
                {cursos.map((c) => (
                  <option key={c.id} value={c.id}>{c.nombre}</option>
                ))}
              </select>
            </>
          )}

          <label className="bm-etiqueta" htmlFor="fecha-sesion">
            Fecha de la sesión
          </label>

          <input
            id="fecha-sesion"
            type="date"
            min={obtenerFechaLocal()}
            value={fecha}
            onChange={(e) => {
              setFecha(e.target.value);
              setVentanaId('');
              setDesde('');
              setHasta('');
              setBloqueSeleccionado(null);
              setError('');
            }}
          />

          {fecha && (
            <>
              <h3>Horarios para la fecha seleccionada</h3>

              {!ventana ? (
                <p className="bm-ayuda">
                  {fecha < obtenerFechaLocal() ? 'Elige una fecha desde hoy.' : 'El mentor no tiene horarios para ese día.'}
                </p>
              ) : (
                <div className="ag-rango">
                  {ventanas.length > 1 && (
                    <>
                      <label htmlFor="ag-franja">Franja del mentor</label>
                      <select id="ag-franja" value={ventana.idDisponibilidad}
                        onChange={(e) => { setVentanaId(e.target.value); setDesde(''); setHasta(''); setBloqueSeleccionado(null); }}>
                        {ventanas.map((v) => (
                          <option key={v.idDisponibilidad} value={v.idDisponibilidad}>{hora12(deMinutos(v.inicio))} – {hora12(deMinutos(v.fin))}</option>
                        ))}
                      </select>
                    </>
                  )}
                  <p className="bm-ayuda">
                    El mentor atiende de <strong>{hora12(deMinutos(ventana.inicio))}</strong> a <strong>{hora12(deMinutos(ventana.fin))}</strong>.
                    Elige a qué hora quieres empezar y a qué hora terminar.
                  </p>
                  <div className="ag-rango-campos">
                    <div>
                      <label htmlFor="ag-desde">Desde</label>
                      <select id="ag-desde" value={desde}
                        onChange={(e) => { setDesde(e.target.value); setHasta(''); setBloqueSeleccionado(null); }}>
                        <option value="" disabled>Elige la hora</option>
                        {puntos(ventana.inicio, ventana.fin - 30).map((t) => <option key={t} value={t}>{hora12(deMinutos(t))}</option>)}
                      </select>
                    </div>
                    <div>
                      <label htmlFor="ag-hasta">Hasta</label>
                      <select id="ag-hasta" value={hasta} disabled={desde === ''}
                        onChange={(e) => { setHasta(e.target.value); elegirRango(ventana, desde, e.target.value); }}>
                        <option value="" disabled>Elige la hora</option>
                        {desde !== '' && puntos(Number(desde) + 30, ventana.fin).map((t) => <option key={t} value={t}>{hora12(deMinutos(t))}</option>)}
                      </select>
                    </div>
                  </div>
                </div>
              )}
            </>
          )}

          <div className="bm-acciones">
            <button
              type="button"
              className="bm-secundario"
              onClick={onVolver}
            >
              Volver
            </button>

            <button
              type="button"
              className="bm-primario"
              onClick={confirmarReserva}
              disabled={!bloqueSeleccionado || guardando}
            >
              {guardando ? 'Confirmando...' : (reprogramando ? 'Confirmar cambio' : 'Confirmar reserva')}
            </button>
          </div>
        </>
      )}
    </section>
  );
}
