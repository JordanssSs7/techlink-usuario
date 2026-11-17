
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

export default function AgendarSesion({ mentor, onVolver }) {
  const { authFetch } = useAuth();

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

  const bloques = generarBloques(disponibilidades, fecha);

  async function confirmarReserva() {
    if (!fecha || !bloqueSeleccionado) {
      setError('Selecciona una fecha y un horario.');
      return;
    }

    setError('');
    setGuardando(true);

    try {
      const respuesta = await authFetch('/api/sesiones', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          mentorId: mentor.idMentor,
          disponibilidadId: bloqueSeleccionado.idDisponibilidad,
          fecha,
          horaInicio: bloqueSeleccionado.horaInicio,
          horaFin: bloqueSeleccionado.horaFin,
          enlaceReunion: 'https://meet.google.com/ere-qoah-awr'
        })
      });

      if (respuesta.status === 409) {
        setError('Ese horario ya está reservado. Selecciona otro.');
        setBloqueSeleccionado(null);
        return;
      }

      if (!respuesta.ok) {
        setError('No se pudo registrar la sesión. Revisa los datos.');
        return;
      }

      const datos = await respuesta.json();
      setConfirmacion(datos);
    } catch {
      setError('No se pudo conectar con el servidor.');
    } finally {
      setGuardando(false);
    }
  }

  if (confirmacion) {
    return (
      <section className="bm-panel">
        <h2>¡Sesión confirmada!</h2>
        <p>Tu mentoría se registró correctamente.</p>

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
          Volver a los mentores
        </button>
      </section>
    );
  }

  return (
    <section className="bm-panel">
      <h2>Agendar sesión</h2>

      <p>
        <strong>Mentor:</strong> {mentor.nombres} {mentor.apellidos}
      </p>

      <p className="bm-ayuda">
        Selecciona una fecha y un horario disponible para tu mentoría.
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
              {guardando ? 'Confirmando...' : 'Confirmar reserva'}
            </button>
          </div>
        </>
      )}
    </section>
  );
}
