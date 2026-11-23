import { useEffect, useState } from 'react';
import './Comunidad.css';
import { useAuth } from '../auth/authContext';
import { hora12 } from './horas';

// Medias horas del día, de 6:00 AM a 11:30 PM: [minutos desde la medianoche].
const PUNTOS = Array.from({ length: 36 }, (_, i) => 360 + i * 30);
const deMinutos = (m) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}:00`;
const hoy = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

// Después de la primera reunión, el mentor agenda la siguiente con ese estudiante en el día y la hora que acuerden, aunque no estén en su
// horario semanal. Solo aparecen los estudiantes con los que ya tuvo una sesión.
export default function AgendarSeguimiento({ onAgendada }) {
  const { authFetch } = useAuth();
  const [abierto, setAbierto] = useState(false);
  const [estudiantes, setEstudiantes] = useState(null);
  const [estudianteId, setEstudianteId] = useState('');
  const [fecha, setFecha] = useState('');
  const [desde, setDesde] = useState('');
  const [hasta, setHasta] = useState('');
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    if (!abierto || estudiantes) return undefined;
    let activo = true;
    (async () => {
      try {
        const response = await authFetch('/api/sesiones/seguimiento/estudiantes');
        if (response.ok && activo) setEstudiantes(await response.json());
        else if (activo) setEstudiantes([]);
      } catch {
        if (activo) setEstudiantes([]);
      }
    })();
    return () => { activo = false; };
  }, [authFetch, abierto, estudiantes]);

  async function agendar(e) {
    e.preventDefault();
    setError('');
    if (!estudianteId || !fecha || desde === '' || hasta === '') {
      setError('Elige el estudiante, la fecha y las horas de inicio y de fin.');
      return;
    }
    setEnviando(true);
    try {
      const response = await authFetch('/api/sesiones/seguimiento', {
        method: 'POST',
        body: JSON.stringify({
          estudianteId: Number(estudianteId), fecha, horaInicio: deMinutos(Number(desde)), horaFin: deMinutos(Number(hasta))
        })
      });
      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        setError(data.error || data.message || 'No se pudo agendar la sesión.');
        return;
      }
      setAviso('¡Sesión agendada! Al estudiante le llegó el aviso.');
      setEstudianteId('');
      setFecha('');
      setDesde('');
      setHasta('');
      setAbierto(false);
      onAgendada?.();
    } catch {
      setError('No se pudo conectar con el servidor.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="co-seguimiento">
      {aviso && <p className="co-ok" role="status">{aviso}</p>}
      {!abierto ? (
        <button type="button" className="co-boton primario" onClick={() => { setAviso(''); setAbierto(true); }}>
          + Agendar otra sesión con un estudiante
        </button>
      ) : (
        <form className="co-tarjeta" onSubmit={agendar} noValidate>
          <h2 className="co-titulo-tarjeta">Agendar otra sesión</h2>
          <p className="co-sub">
            Para la siguiente reunión con un estudiante con quien ya tuviste una sesión. Puedes elegir cualquier día y hora, aunque no
            estén en tu horario semanal.
          </p>
          {estudiantes && estudiantes.length === 0 && (
            <p className="co-error" role="alert">Todavía no tienes estudiantes con una sesión agendada.</p>
          )}
          <label htmlFor="seg-est">Estudiante</label>
          <select id="seg-est" value={estudianteId} onChange={(e) => setEstudianteId(e.target.value)}>
            <option value="" disabled>Elige un estudiante</option>
            {(estudiantes || []).map((x) => <option key={x.idEstudiante} value={x.idEstudiante}>{x.nombre}</option>)}
          </select>
          <label htmlFor="seg-fecha">Fecha</label>
          <input id="seg-fecha" type="date" min={hoy()} value={fecha} onChange={(e) => setFecha(e.target.value)} />
          <div className="seg-horas">
            <div>
              <label htmlFor="seg-desde">Desde</label>
              <select id="seg-desde" value={desde} onChange={(e) => { setDesde(e.target.value); setHasta(''); }}>
                <option value="" disabled>Elige la hora</option>
                {PUNTOS.slice(0, -1).map((t) => <option key={t} value={t}>{hora12(deMinutos(t))}</option>)}
              </select>
            </div>
            <div>
              <label htmlFor="seg-hasta">Hasta</label>
              <select id="seg-hasta" value={hasta} disabled={desde === ''} onChange={(e) => setHasta(e.target.value)}>
                <option value="" disabled>Elige la hora</option>
                {desde !== '' && PUNTOS.filter((t) => t > Number(desde)).map((t) => <option key={t} value={t}>{hora12(deMinutos(t))}</option>)}
              </select>
            </div>
          </div>
          {error && <p className="co-error" role="alert">{error}</p>}
          <div className="co-soporte-acciones">
            <button type="submit" className="co-boton primario" disabled={enviando}>{enviando ? 'Agendando…' : 'Agendar sesión'}</button>
            <button type="button" className="co-boton" onClick={() => { setAbierto(false); setError(''); }}>Cancelar</button>
          </div>
        </form>
      )}
    </div>
  );
}
