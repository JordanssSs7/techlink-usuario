
import { useEffect, useState } from 'react';
import { useAuth } from '../auth/authContext';

const DIAS = [
  'Domingo', 'Lunes', 'Martes', 'Miércoles',
  'Jueves', 'Viernes', 'Sábado'
];

const horaCorta = (valor) => String(valor).slice(0, 5);

function obtenerDiaSemana(fecha) {
  const [anio, mes, dia] = fecha.split('-').map(Number);
  const diaJS = new Date(anio, mes - 1, dia).getDay();
  return diaJS === 0 ? 7 : diaJS;
}

function obtenerFechaLocal() {
  const ahora = new Date();
  const anio = ahora.getFullYear();
  const mes = String(ahora.getMonth() + 1).padStart(2, '0');
  const dia = String(ahora.getDate()).padStart(2, '0');
  return `${anio}-${mes}-${dia}`;
}

function generarBloques(disponibilidades, fecha) {
  if (!fecha) return [];

  const dia = obtenerDiaSemana(fecha);
  const bloques = [];

  disponibilidades
    .filter((d) => d.diaSemana === dia)
    .forEach((d) => {
      const inicio = Number(d.horaInicio.slice(0, 2));
      const fin = Number(d.horaFin.slice(0, 2));

      for (let h = inicio; h + 1 <= fin; h++) {
        const horaInicio = `${String(h).padStart(2, '0')}:00:00`;
        const horaFin = `${String(h + 1).padStart(2, '0')}:00:00`;

        bloques.push({
          idDisponibilidad: d.idDisponibilidad,
          horaInicio,
          horaFin
        });
      }
    });

  return bloques;
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

  const bloques = generarBloques(disponibilidades, fecha);

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
              cursoId: cursoId ? Number(cursoId) : null,
              enlaceReunion: 'https://meet.google.com/ere-qoah-awr'
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
        <h2>{reprogramando ? '¡Sesión reprogramada!' : '¡Sesión confirmada!'}</h2>
        <p>{reprogramando ? 'Tu mentoría cambió de horario y avisamos al mentor.' : 'Tu mentoría se registró correctamente.'}</p>

        <p><strong>Mentor:</strong> {mentor.nombres} {mentor.apellidos}</p>
        <p><strong>Fecha:</strong> {confirmacion.fecha}</p>
        <p>
          <strong>Horario:</strong>{' '}
          {horaCorta(confirmacion.horaInicio)} –{' '}
          {horaCorta(confirmacion.horaFin)}
        </p>
        <p><strong>Estado:</strong> {confirmacion.estado}</p>

        <p>Se procesará la notificación por correo electrónico.</p>

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
        <p>Cargando horarios...</p>
      ) : (
        <>
          <p><strong>Horarios del mentor:</strong></p>

          {disponibilidades.map((d) => (
            <p key={d.idDisponibilidad}>
              {DIAS[d.diaSemana] || `Día ${d.diaSemana}`}:{' '}
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
              setBloqueSeleccionado(null);
              setError('');
            }}
          />

          {fecha && (
            <>
              <h3>Horarios para la fecha seleccionada</h3>

              {bloques.length === 0 ? (
                <p className="bm-ayuda">
                  El mentor no tiene horarios para ese día.
                </p>
              ) : (
                <div className="bm-acciones">
                  {bloques.map((b) => {
                    const clave = `${b.idDisponibilidad}-${b.horaInicio}`;
                    const seleccionado =
                      bloqueSeleccionado?.idDisponibilidad ===
                        b.idDisponibilidad &&
                      bloqueSeleccionado?.horaInicio === b.horaInicio;

                    return (
                      <button
                        key={clave}
                        type="button"
                        className={
                          seleccionado ? 'bm-primario' : 'bm-secundario'
                        }
                        onClick={() => {
                          setBloqueSeleccionado(b);
                          setError('');
                        }}
                      >
                        {horaCorta(b.horaInicio)} – {horaCorta(b.horaFin)}
                      </button>
                    );
                  })}
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
