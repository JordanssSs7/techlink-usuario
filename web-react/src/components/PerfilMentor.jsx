import { useEffect, useState } from 'react';
import './LoginForm.css';
import './PerfilMentor.css';
import { useAuth } from '../auth/authContext';

const DIAS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];

// "09:00:00" -> "09:00" (lo que entiende <input type="time">)
const aHora = (valor) => (valor ? String(valor).slice(0, 5) : '');

// Lista de etiquetas: se escribe y se agrega con Enter o con el botón "Agregar".
function EtiquetasInput({ id, etiqueta, valores, onChange, placeholder, error }) {
  const [texto, setTexto] = useState('');

  const agregar = () => {
    const limpio = texto.trim();
    if (limpio.length < 2) return;
    if (!valores.some((v) => v.toLowerCase() === limpio.toLowerCase())) {
      onChange([...valores, limpio]);
    }
    setTexto('');
  };

  return (
    <div className="login-field">
      <label htmlFor={id}>{etiqueta}</label>
      <div className="mentor-etiquetas-fila">
        <input
          id={id}
          type="text"
          value={texto}
          maxLength={100}
          placeholder={placeholder}
          onChange={(e) => setTexto(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              agregar();
            }
          }}
        />
        <button type="button" className="mentor-boton-chico" onClick={agregar}>Agregar</button>
      </div>
      {valores.length > 0 && (
        <ul className="mentor-etiquetas">
          {valores.map((v) => (
            <li key={v} className="mentor-etiqueta">
              {v}
              <button
                type="button"
                aria-label={`Quitar ${v}`}
                onClick={() => onChange(valores.filter((x) => x !== v))}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
      {error && <span className="login-error">{error}</span>}
    </div>
  );
}

// Pantalla del mentor (HU-05): fortalezas, cursos dominados, áreas de conocimiento y disponibilidad semanal.
export default function PerfilMentor() {
  const { usuario, authFetch, logout } = useAuth();
  const [catalogo, setCatalogo] = useState([]);
  const [especialidad, setEspecialidad] = useState('');
  const [fortalezas, setFortalezas] = useState([]);
  const [areas, setAreas] = useState([]);
  const [cursoIds, setCursoIds] = useState([]);
  const [horarios, setHorarios] = useState([]);
  const [errors, setErrors] = useState({});
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);
  const [guardado, setGuardado] = useState(false);

  // Al abrir: catálogo de cursos y perfil que ya tenía guardado.
  useEffect(() => {
    let activo = true;
    async function cargar() {
      try {
        const [respCursos, respPerfil] = await Promise.all([
          authFetch('/api/cursos'),
          authFetch('/api/mentor/perfil')
        ]);
        if (!respCursos.ok || !respPerfil.ok) throw new Error();
        const cursos = await respCursos.json();
        const perfil = await respPerfil.json();
        if (!activo) return;
        setCatalogo(cursos);
        setEspecialidad(perfil.especialidad ?? '');
        setFortalezas(perfil.fortalezas);
        setAreas(perfil.areas);
        setCursoIds(perfil.cursos.map((c) => c.id));
        setHorarios(perfil.disponibilidad.map((h) => ({
          diaSemana: h.diaSemana,
          horaInicio: aHora(h.horaInicio),
          horaFin: aHora(h.horaFin),
          activo: h.activo
        })));
      } catch {
        if (activo) setErrors({ global: 'No se pudo cargar tu perfil. Revisa tu conexión e inténtalo de nuevo.' });
      } finally {
        if (activo) setCargando(false);
      }
    }
    cargar();
    return () => { activo = false; };
    // authFetch cambia con la sesión; solo se carga una vez al abrir la pantalla.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const marcarCambio = () => { setGuardado(false); setErrors({}); };

  const alternarCurso = (id) => {
    marcarCambio();
    setCursoIds((actuales) => (actuales.includes(id) ? actuales.filter((c) => c !== id) : [...actuales, id]));
  };

  const cambiarHorario = (indice, cambios) => {
    marcarCambio();
    setHorarios((actuales) => actuales.map((h, i) => (i === indice ? { ...h, ...cambios } : h)));
  };

  const agregarHorario = () => {
    marcarCambio();
    setHorarios((actuales) => [...actuales, { diaSemana: 1, horaInicio: '09:00', horaFin: '11:00', activo: true }]);
  };

  const quitarHorario = (indice) => {
    marcarCambio();
    setHorarios((actuales) => actuales.filter((_, i) => i !== indice));
  };

  // Validación en el navegador; el backend vuelve a validar todo.
  const validar = () => {
    const nuevos = {};
    horarios.forEach((h, i) => {
      if (!h.horaInicio || !h.horaFin) nuevos[`disponibilidad[${i}].horaFin`] = 'Indica la hora de inicio y de fin.';
      else if (h.horaFin <= h.horaInicio) nuevos[`disponibilidad[${i}].horaFin`] = 'La hora de fin debe ser posterior a la de inicio.';
    });
    return nuevos;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const invalidos = validar();
    if (Object.keys(invalidos).length > 0) {
      setErrors(invalidos);
      return;
    }

    setGuardando(true);
    setErrors({});
    setGuardado(false);
    try {
      const response = await authFetch('/api/mentor/perfil', {
        method: 'PUT',
        body: JSON.stringify({ especialidad, fortalezas, areas, cursoIds, disponibilidad: horarios })
      });
      if (response.ok) {
        setGuardado(true);
        return;
      }
      const data = await response.json().catch(() => ({}));
      if (response.status === 400 && data.campos) setErrors(data.campos);
      else if (response.status === 403) setErrors({ global: 'Solo los mentores pueden editar este perfil.' });
      else setErrors({ global: data.error || 'No se pudo guardar tu perfil. Inténtalo nuevamente.' });
    } catch {
      setErrors({ global: 'Error al conectar con el servidor.' });
    } finally {
      setGuardando(false);
    }
  };

  // Errores de los elementos de una lista, por ejemplo "fortalezas[0]".
  const errorDeLista = (nombre) => {
    const clave = Object.keys(errors).find((k) => k === nombre || k.startsWith(`${nombre}[`));
    return clave ? errors[clave] : null;
  };

  return (
    <div className="login-page mentor-page">
      <section className="mentor-panel">
        <div className="mentor-caja">
          <svg className="login-logo" width="22" height="22" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M3 3h8l-3 9H3z" fill="currentColor" />
            <path d="M13 3h8v9h-5l-3 9H9z" fill="currentColor" />
          </svg>

          <h1 className="login-title">Mi perfil de mentor</h1>
          <p className="login-subtitle">
            Hola, {usuario.nombres}. Lo que registres aquí se usa para recomendarte a los estudiantes y para que agenden contigo.
          </p>

          {cargando ? (
            <p className="login-subtitle" role="status">Cargando tu perfil...</p>
          ) : (
            <form onSubmit={handleSubmit} noValidate onChange={marcarCambio}>
              {errors.global && <div className="login-alert" role="alert">{errors.global}</div>}
              {guardado && <div className="login-success" role="status">Perfil guardado correctamente.</div>}

              <div className="login-field">
                <label htmlFor="especialidad">Especialidad (opcional)</label>
                <input
                  id="especialidad"
                  type="text"
                  value={especialidad}
                  maxLength={150}
                  placeholder="Ej. Backend con Java y Spring"
                  onChange={(e) => setEspecialidad(e.target.value)}
                />
                {errors.especialidad && <span className="login-error">{errors.especialidad}</span>}
              </div>

              <EtiquetasInput
                id="fortalezas"
                etiqueta="Fortalezas técnicas"
                valores={fortalezas}
                onChange={(v) => { marcarCambio(); setFortalezas(v); }}
                placeholder="Ej. Java, React, MySQL"
                error={errorDeLista('fortalezas')}
              />

              <EtiquetasInput
                id="areas"
                etiqueta="Áreas de conocimiento"
                valores={areas}
                onChange={(v) => { marcarCambio(); setAreas(v); }}
                placeholder="Ej. Backend, Bases de datos"
                error={errorDeLista('areas')}
              />

              <fieldset className="mentor-grupo">
                <legend>Cursos que dominas</legend>
                <div className="mentor-cursos">
                  {catalogo.map((curso) => (
                    <label
                      key={curso.id}
                      className={`mentor-curso${cursoIds.includes(curso.id) ? ' mentor-curso-activo' : ''}`}
                    >
                      <input
                        type="checkbox"
                        checked={cursoIds.includes(curso.id)}
                        onChange={() => alternarCurso(curso.id)}
                      />
                      {curso.nombre}
                    </label>
                  ))}
                </div>
                {errors.cursoIds && <span className="login-error">{errors.cursoIds}</span>}
              </fieldset>

              <fieldset className="mentor-grupo">
                <legend>Disponibilidad semanal</legend>
                {horarios.length === 0 && (
                  <p className="login-hint">Aún no tienes horarios. Agrega los días y horas en que puedes dar mentoría.</p>
                )}
                {horarios.map((h, i) => (
                  <div key={i} className="mentor-horario">
                    <select
                      aria-label={`Día del horario ${i + 1}`}
                      value={h.diaSemana}
                      onChange={(e) => cambiarHorario(i, { diaSemana: Number(e.target.value) })}
                    >
                      {DIAS.map((dia, d) => <option key={dia} value={d + 1}>{dia}</option>)}
                    </select>
                    <input
                      type="time"
                      aria-label={`Hora de inicio del horario ${i + 1}`}
                      value={h.horaInicio}
                      onChange={(e) => cambiarHorario(i, { horaInicio: e.target.value })}
                    />
                    <input
                      type="time"
                      aria-label={`Hora de fin del horario ${i + 1}`}
                      value={h.horaFin}
                      onChange={(e) => cambiarHorario(i, { horaFin: e.target.value })}
                    />
                    <label className="mentor-activo">
                      <input
                        type="checkbox"
                        checked={h.activo}
                        onChange={(e) => cambiarHorario(i, { activo: e.target.checked })}
                      />
                      Activo
                    </label>
                    <button type="button" className="mentor-boton-chico" onClick={() => quitarHorario(i)}>Quitar</button>
                    {errors[`disponibilidad[${i}].horaFin`] && (
                      <span className="login-error mentor-horario-error">{errors[`disponibilidad[${i}].horaFin`]}</span>
                    )}
                  </div>
                ))}
                {errors.disponibilidad && <span className="login-error">{errors.disponibilidad}</span>}
                <button type="button" className="mentor-boton-chico mentor-agregar" onClick={agregarHorario}>
                  + Agregar horario
                </button>
                <p className="login-hint">Solo los horarios marcados como activos se muestran a los estudiantes.</p>
              </fieldset>

              <button type="submit" className="login-submit" disabled={guardando}>
                {guardando ? 'Guardando...' : 'Guardar perfil'}
              </button>
            </form>
          )}

          <p className="mentor-salir">
            <a href="#salir" onClick={(e) => { e.preventDefault(); logout(); }}>Cerrar sesión</a>
          </p>
        </div>
      </section>
    </div>
  );
}
