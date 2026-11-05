import { useEffect, useMemo, useState } from 'react';
import { useAuth } from '../auth/authContext';
import AgendarSesion from './AgendarSesion';

const DIAS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
const HORAS = Array.from({ length: 15 }, (_, i) => i + 7); // 07:00 a 21:00 (bloques de 1 hora)
const MAX_CURSOS = 5;
const MAX_NOTA = 500;

const pad = (n) => String(n).padStart(2, '0');
const hora = (n) => `${pad(n)}:00`;
const recortar = (t) => String(t).slice(0, 5);

// Une las horas marcadas de cada día en rangos: [9,10,11] -> 09:00-12:00.
function aRangos(celdas) {
  const rangos = [];
  DIAS.forEach((_, d) => {
    const marcadas = HORAS.filter((h) => celdas.has(`${d + 1}-${h}`));
    let i = 0;
    while (i < marcadas.length) {
      let j = i;
      while (j + 1 < marcadas.length && marcadas[j + 1] === marcadas[j] + 1) j += 1;
      rangos.push({ diaSemana: d + 1, horaInicio: hora(marcadas[i]), horaFin: hora(marcadas[j] + 1) });
      i = j + 1;
    }
  });
  return rangos;
}

// HU-04: el estudiante elige los cursos en los que va mal y su horario libre; el agente de IA propone el top 3.
export default function BuscarMentor() {
  const { authFetch } = useAuth();
  const [paso, setPaso] = useState(1);
  const [catalogo, setCatalogo] = useState([]);
  const [cursos, setCursos] = useState([]);
  const [busqueda, setBusqueda] = useState('');
  const [celdas, setCeldas] = useState(() => new Set());
  const [nota, setNota] = useState('');
  const [cargando, setCargando] = useState(true);
  const [buscando, setBuscando] = useState(false);
  const [error, setError] = useState('');
  const [resultado, setResultado] = useState(null);
  const [mentorSeleccionado, setMentorSeleccionado] = useState(null);

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const [rc, rg] = await Promise.all([
          authFetch('/api/cursos'),
          authFetch('/api/estudiantes/me/cursos-criticos')
        ]);
        if (!rc.ok || !rg.ok) throw new Error();
        const lista = await rc.json();
        const guardados = await rg.json();
        if (!activo) return;
        setCatalogo(lista);
        setCursos(guardados.map((c) => c.id));
      } catch {
        if (activo) setError('No se pudieron cargar los cursos. Intenta de nuevo.');
      } finally {
        if (activo) setCargando(false);
      }
    })();
    return () => { activo = false; };
  }, [authFetch]);

  const nombres = useMemo(() => new Map(catalogo.map((c) => [c.id, c.nombre])), [catalogo]);
  const visibles = catalogo.filter((c) => c.nombre.toLowerCase().includes(busqueda.trim().toLowerCase()));
  const rangos = aRangos(celdas);

  const alternarCurso = (id) => {
    setError('');
    setCursos((act) => {
      if (act.includes(id)) return act.filter((x) => x !== id);
      if (act.length >= MAX_CURSOS) {
        setError(`Puedes elegir hasta ${MAX_CURSOS} cursos.`);
        return act;
      }
      return [...act, id];
    });
  };

  const alternarCelda = (clave) => {
    setCeldas((act) => {
      const nuevo = new Set(act);
      if (nuevo.has(clave)) nuevo.delete(clave); else nuevo.add(clave);
      return nuevo;
    });
  };

  async function continuarACursos() {
    if (cursos.length === 0) {
      setError('Elige al menos un curso.');
      return;
    }
    setError('');
    // Se guardan como cursos críticos para recordarlos la próxima vez.
    try {
      await authFetch('/api/estudiantes/me/cursos-criticos', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ cursos })
      });
    } catch { /* guardar es un extra: no bloquea la búsqueda */ }
    setPaso(2);
  }

  async function buscar() {
    if (rangos.length === 0) {
      setError('Marca al menos un bloque de horario libre.');
      return;
    }
    if (rangos.length > 21) {
      setError('Marcaste demasiados bloques separados. Une algunos y vuelve a intentar.');
      return;
    }
    setError('');
    setBuscando(true);
    try {
      const response = await authFetch('/api/agente/recomendaciones', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ cursos, horarios: rangos, nota: nota.trim() || null })
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        setError(data.error || 'No se pudo completar la búsqueda.');
        return;
      }
      setResultado(data);
      setPaso(3);
    } catch {
      setError('Error al conectar con el servidor.');
    } finally {
      setBuscando(false);
    }
  }

  const reiniciar = () => {
    setResultado(null);
    setPaso(1);
    setError('');
  };

  if (cargando) return <p className="pt-estado">Cargando…</p>;

  if (mentorSeleccionado) {
    return (
      <section className="bm" aria-label="Agendar sesión">
        <AgendarSesion
          mentor={mentorSeleccionado}
          onVolver={() => setMentorSeleccionado(null)}
        />
      </section>
    );
  }
  
  return (
    <section className="bm" aria-label="Buscar mentor">
      <ol className="bm-pasos" aria-label="Pasos">
        {['Cursos', 'Horario', 'Mentores'].map((t, i) => (
          <li key={t} className={paso === i + 1 ? 'activo' : paso > i + 1 ? 'hecho' : ''}>
            <span>{i + 1}</span>{t}
          </li>
        ))}
      </ol>

      {error && <p className="bm-error" role="alert">{error}</p>}

      {paso === 1 && (
        <div className="bm-panel">
          <h2>¿En qué cursos necesitas ayuda?</h2>
          <p className="bm-ayuda">Elige hasta {MAX_CURSOS} cursos de tu malla. Seleccionados: {cursos.length}/{MAX_CURSOS}</p>
          {cursos.length > 0 && (
            <ul className="bm-elegidos">
              {cursos.map((id) => (
                <li key={id}>
                  {nombres.get(id)}
                  <button type="button" onClick={() => alternarCurso(id)} aria-label={`Quitar ${nombres.get(id)}`}>×</button>
                </li>
              ))}
            </ul>
          )}
          <input
            className="bm-buscar"
            type="search"
            placeholder="Buscar un curso…"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            aria-label="Buscar un curso"
          />
          <div className="bm-cursos">
            {visibles.map((c) => (
              <button
                key={c.id}
                type="button"
                className={`bm-curso${cursos.includes(c.id) ? ' marcado' : ''}`}
                aria-pressed={cursos.includes(c.id)}
                onClick={() => alternarCurso(c.id)}
              >
                {c.nombre}
              </button>
            ))}
            {visibles.length === 0 && <p className="bm-ayuda">Ningún curso coincide con la búsqueda.</p>}
          </div>
          <div className="bm-acciones">
            <button type="button" className="bm-primario" onClick={continuarACursos}>Continuar</button>
          </div>
        </div>
      )}

      {paso === 2 && (
        <div className="bm-panel">
          <h2>¿Cuándo tienes tiempo libre?</h2>
          <p className="bm-ayuda">Toca los bloques de 1 hora en que puedes reunirte. Buscaremos un mentor que coincida.</p>
          <div className="bm-tabla-contenedor">
            <table className="bm-tabla">
              <thead>
                <tr><th scope="col"><span className="sr">Hora</span></th>{DIAS.map((d) => <th key={d} scope="col">{d.slice(0, 3)}</th>)}</tr>
              </thead>
              <tbody>
                {HORAS.map((h) => (
                  <tr key={h}>
                    <th scope="row">{hora(h)}</th>
                    {DIAS.map((d, i) => {
                      const clave = `${i + 1}-${h}`;
                      const on = celdas.has(clave);
                      return (
                        <td key={clave}>
                          <button
                            type="button"
                            className={`bm-celda${on ? ' marcada' : ''}`}
                            aria-pressed={on}
                            aria-label={`${d} ${hora(h)}`}
                            onClick={() => alternarCelda(clave)}
                          />
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <label className="bm-etiqueta" htmlFor="bm-nota">Algo más que quieras contarle al mentor (opcional)</label>
          <textarea
            id="bm-nota"
            rows={3}
            maxLength={MAX_NOTA}
            value={nota}
            placeholder="Ej: me cuesta entender las consultas con JOIN"
            onChange={(e) => setNota(e.target.value)}
          />
          <div className="bm-acciones">
            <button type="button" className="bm-secundario" onClick={() => { setError(''); setPaso(1); }}>Atrás</button>
            <button type="button" className="bm-primario" onClick={buscar} disabled={buscando}>
              {buscando ? 'Buscando con IA…' : 'Buscar mentor'}
            </button>
          </div>
        </div>
      )}

      {paso === 3 && resultado && (
        <div className="bm-panel">
          <h2>Tus mentores recomendados</h2>
          {resultado.mentores.length > 0 && (
            <p className={`bm-origen${resultado.generadoConIa ? ' ia' : ''}`}>
              {resultado.generadoConIa ? 'Recomendado por la IA' : 'Cálculo base (sin IA)'}
            </p>
          )}
          {resultado.mensaje && <p className="bm-ayuda">{resultado.mensaje}</p>}
          <ol className="bm-resultados">
            {resultado.mentores.map((m, i) => (
              <li key={m.idMentor} className={`bm-mentor${i === 0 ? ' primero' : ''}`}>
                <div className="bm-mentor-cab">
                  <div>
                    <p className="bm-mentor-nombre">{m.nombres} {m.apellidos}</p>
                    <p className="bm-mentor-esp">{m.especialidad}</p>
                  </div>
                  <p className="bm-pct" aria-label={`${m.porcentaje} por ciento de coincidencia`}>{m.porcentaje}%</p>
                </div>
                <div className="bm-barra" aria-hidden="true"><span style={{ width: `${m.porcentaje}%` }} /></div>
                <p className="bm-razon">{m.razon}</p>
                <dl className="bm-datos">
                  <div><dt>Cursos en común</dt><dd>{m.cursosEnComun.join(', ')}</dd></div>
                  <div>
                    <dt>Horario en común</dt>
                    <dd>{m.horariosEnComun.map((h) => `${DIAS[h.diaSemana - 1] || h.diaSemana} ${recortar(h.horaInicio)}–${recortar(h.horaFin)}`).join(' · ')}</dd>
                  </div>
                  {m.fortalezas?.length > 0 && <div><dt>Fortalezas</dt><dd>{m.fortalezas.join(', ')}</dd></div>}
                </dl>
                <div className="bm-acciones">
                  <button
                    type="button"
                    className="bm-primario"
                    onClick={() => setMentorSeleccionado(m)}
                  >
                    Agendar sesión
                  </button>
                </div>
              </li>
            ))}
          </ol>
          <div className="bm-acciones">
            <button type="button" className="bm-secundario" onClick={reiniciar}>Nueva búsqueda</button>
          </div>
        </div>
      )}
    </section>
  );
}
