import { useEffect, useMemo, useRef, useState } from 'react';
import { useAuth } from '../auth/authContext';
import AgendarSesion from './AgendarSesion';
import { CATEGORIAS, categoriaDe } from './cursosCategorias';
import IconoCategoria from './IconoCategoria';

const DIAS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
const HORAS = Array.from({ length: 15 }, (_, i) => i + 7); // 07:00 a 21:00 (bloques de 1 hora)
const MAX_CURSOS = 5;
const MAX_NOTA = 500;
const ATAJOS = [
  { id: 'manana', nombre: 'Mañanas', desde: 7, hasta: 12 },
  { id: 'tarde', nombre: 'Tardes', desde: 13, hasta: 18 },
  { id: 'noche', nombre: 'Noches', desde: 18, hasta: 22 }
];

const pad = (n) => String(n).padStart(2, '0');
const hora = (n) => `${pad(n)}:00`;
const recortar = (t) => String(t).slice(0, 5);
const ordenCategoria = new Map(CATEGORIAS.map((c, i) => [c.id, i]));

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

// Fechas de lunes a sábado de la semana actual, para mostrar el calendario como una semana real.
function semanaActual() {
  const hoy = new Date();
  const lunes = new Date(hoy);
  lunes.setDate(hoy.getDate() - ((hoy.getDay() + 6) % 7));
  return DIAS.map((_, i) => {
    const f = new Date(lunes);
    f.setDate(lunes.getDate() + i);
    return { dia: f.getDate(), esHoy: f.toDateString() === hoy.toDateString() };
  });
}

function iniciales(nombres, apellidos) {
  return [nombres, apellidos].filter(Boolean).map((p) => p.trim()[0]).join('').slice(0, 2).toUpperCase();
}

function Avatar({ mentor }) {
  const [fallo, setFallo] = useState(false);
  const foto = mentor.foto || mentor.fotoUrl;
  if (foto && !fallo) {
    return <img className="bm-avatar" src={foto} alt="" onError={() => setFallo(true)} />;
  }
  return <span className="bm-avatar" aria-hidden="true">{iniciales(mentor.nombres, mentor.apellidos)}</span>;
}

// HU-04: el estudiante elige los cursos en los que va mal y su horario libre; el agente de IA propone el top 3.
export default function BuscarMentor() {
  const { authFetch } = useAuth();
  const [paso, setPaso] = useState(1);
  const [catalogo, setCatalogo] = useState([]);
  const [cursos, setCursos] = useState([]);
  const [busqueda, setBusqueda] = useState('');
  const [categoria, setCategoria] = useState('todos');
  const [celdas, setCeldas] = useState(() => new Set());
  const [nota, setNota] = useState('');
  const [cargando, setCargando] = useState(true);
  const [buscando, setBuscando] = useState(false);
  const [error, setError] = useState('');
  const [resultado, setResultado] = useState(null);
  const [mentorSeleccionado, setMentorSeleccionado] = useState(null);
  const [bordes, setBordes] = useState({ arriba: false, abajo: false });
  const listaRef = useRef(null);
  const arrastre = useRef(null);
  const semana = useMemo(() => semanaActual(), []);

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

  // Al soltar el mouse o el dedo en cualquier parte, termina el arrastre sobre el calendario.
  useEffect(() => {
    const soltar = () => { arrastre.current = null; };
    document.addEventListener('pointerup', soltar);
    document.addEventListener('pointercancel', soltar);
    return () => {
      document.removeEventListener('pointerup', soltar);
      document.removeEventListener('pointercancel', soltar);
    };
  }, []);

  // Catálogo ordenado por categoría y luego por nombre.
  const catalogoOrdenado = useMemo(() => catalogo
    .map((c) => ({ ...c, categoria: categoriaDe(c.nombre) }))
    .sort((a, b) => (ordenCategoria.get(a.categoria) - ordenCategoria.get(b.categoria)) || a.nombre.localeCompare(b.nombre, 'es')),
  [catalogo]);

  const conteo = useMemo(() => {
    const m = new Map([['todos', catalogoOrdenado.length]]);
    catalogoOrdenado.forEach((c) => m.set(c.categoria, (m.get(c.categoria) || 0) + 1));
    return m;
  }, [catalogoOrdenado]);

  const cursoPorId = useMemo(() => new Map(catalogoOrdenado.map((c) => [c.id, c])), [catalogoOrdenado]);
  const cursoPorNombre = useMemo(() => new Map(catalogoOrdenado.map((c) => [c.nombre, c])), [catalogoOrdenado]);
  const texto = busqueda.trim().toLowerCase();
  const visibles = catalogoOrdenado.filter((c) =>
    (categoria === 'todos' || c.categoria === categoria) && c.nombre.toLowerCase().includes(texto));
  const rangos = aRangos(celdas);
  const totalHoras = celdas.size;

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

  const marcarCelda = (clave, encender) => {
    setCeldas((act) => {
      if (act.has(clave) === encender) return act;
      const nuevo = new Set(act);
      if (encender) nuevo.add(clave); else nuevo.delete(clave);
      return nuevo;
    });
  };

  // Arrastrar sobre el calendario pinta (o borra) varias horas seguidas.
  const empezarArrastre = (e, clave) => {
    if (e.button !== 0) return;
    e.preventDefault();
    e.currentTarget.releasePointerCapture?.(e.pointerId);
    const encender = !celdas.has(clave);
    arrastre.current = encender;
    setError('');
    marcarCelda(clave, encender);
  };
  const pasarPorCelda = (clave) => {
    if (arrastre.current !== null) marcarCelda(clave, arrastre.current);
  };

  const aplicarAtajo = (atajo) => {
    setError('');
    setCeldas((act) => {
      const claves = [];
      DIAS.forEach((_, d) => {
        for (let h = atajo.desde; h < atajo.hasta; h += 1) claves.push(`${d + 1}-${h}`);
      });
      const yaEstan = claves.every((k) => act.has(k));
      const nuevo = new Set(act);
      claves.forEach((k) => (yaEstan ? nuevo.delete(k) : nuevo.add(k)));
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

  // Sombras de desvanecido arriba/abajo de la lista según cuánto se haya deslizado.
  const actualizarBordes = () => {
    const el = listaRef.current;
    if (!el) return;
    setBordes({
      arriba: el.scrollTop > 4,
      abajo: el.scrollTop + el.clientHeight < el.scrollHeight - 4
    });
  };

  useEffect(() => {
    if (paso === 1 && !cargando) actualizarBordes();
  }, [paso, cargando, visibles.length]);

  if (cargando) {
    return (
      <div className="bm-cargando" aria-busy="true" aria-label="Cargando cursos">
        {Array.from({ length: 6 }, (_, i) => <span key={i} style={{ '--i': i }} />)}
      </div>
    );
  }

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
      <ol className="bm-pasos" aria-label="Pasos" style={{ '--avance': (paso - 1) / 2 }}>
        {['Cursos', 'Horario', 'Mentores'].map((t, i) => (
          <li key={t} className={paso === i + 1 ? 'activo' : paso > i + 1 ? 'hecho' : ''} aria-current={paso === i + 1 ? 'step' : undefined}>
            <span className="bm-paso-num">{paso > i + 1 ? '✓' : i + 1}</span>
            <span className="bm-paso-txt">{t}</span>
          </li>
        ))}
      </ol>

      {error && <p key={error} className="bm-error" role="alert">{error}</p>}

      {paso === 1 && (
        <div key="p1" className="bm-panel">
          <div className="bm-panel-cab">
            <div>
              <h2>¿En qué cursos necesitas ayuda?</h2>
              <p className="bm-ayuda">Elige hasta {MAX_CURSOS} cursos de tu malla.</p>
            </div>
            <div className="bm-contador" aria-live="polite" aria-label={`${cursos.length} de ${MAX_CURSOS} cursos seleccionados`}>
              <div className="bm-contador-puntos" aria-hidden="true">
                {Array.from({ length: MAX_CURSOS }, (_, i) => <span key={i} className={i < cursos.length ? 'lleno' : ''} />)}
              </div>
              <span aria-hidden="true">{cursos.length}/{MAX_CURSOS}</span>
            </div>
          </div>

          {cursos.length > 0 && (
            <ul className="bm-elegidos">
              {cursos.map((id) => {
                const c = cursoPorId.get(id);
                return (
                  <li key={id}>
                    {c && <IconoCategoria id={c.categoria} size={14} />}
                    {c?.nombre}
                    <button type="button" onClick={() => alternarCurso(id)} aria-label={`Quitar ${c?.nombre}`}>×</button>
                  </li>
                );
              })}
            </ul>
          )}

          <div className="bm-buscar-caja">
            <svg className="bm-lupa" width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="11" cy="11" r="7" fill="none" stroke="currentColor" strokeWidth="2" />
              <path d="m20 20-3.5-3.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
            </svg>
            <input
              className="bm-buscar"
              type="search"
              placeholder="Buscar un curso…"
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
              aria-label="Buscar un curso"
            />
            <span className="bm-buscar-total" aria-hidden="true">{visibles.length} cursos</span>
          </div>

          <div className="bm-filtros" role="tablist" aria-label="Categorías de cursos">
            {CATEGORIAS.filter((c) => conteo.get(c.id)).map((c) => (
              <button
                key={c.id}
                type="button"
                role="tab"
                aria-selected={categoria === c.id}
                className={`bm-filtro${categoria === c.id ? ' activo' : ''}`}
                onClick={() => { setCategoria(c.id); listaRef.current?.scrollTo({ top: 0 }); }}
              >
                <IconoCategoria id={c.id} size={16} />
                {c.nombre}
                <span className="bm-filtro-n">{conteo.get(c.id)}</span>
              </button>
            ))}
          </div>

          <div className={`bm-cursos-marco${bordes.arriba ? ' sombra-arriba' : ''}${bordes.abajo ? ' sombra-abajo' : ''}`}>
            <div className="bm-cursos" ref={listaRef} onScroll={actualizarBordes} key={categoria}>
              {visibles.map((c, i) => {
                const marcado = cursos.includes(c.id);
                const bloqueado = !marcado && cursos.length >= MAX_CURSOS;
                return (
                  <button
                    key={c.id}
                    type="button"
                    style={{ '--i': Math.min(i, 14) }}
                    className={`bm-curso${marcado ? ' marcado' : ''}${bloqueado ? ' bloqueado' : ''}`}
                    aria-pressed={marcado}
                    onClick={() => alternarCurso(c.id)}
                  >
                    <span className="bm-curso-icono"><IconoCategoria id={c.categoria} /></span>
                    <span className="bm-curso-texto">
                      <span className="bm-curso-nombre">{c.nombre}</span>
                      <span className="bm-curso-cat">{CATEGORIAS[ordenCategoria.get(c.categoria)].nombre}</span>
                    </span>
                    <span className="bm-check" aria-hidden="true">
                      <svg width="12" height="12" viewBox="0 0 24 24"><path d="M5 12.5 10 17l9-10" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" /></svg>
                    </span>
                  </button>
                );
              })}
              {visibles.length === 0 && <p className="bm-vacio">Ningún curso coincide con la búsqueda.</p>}
            </div>
            {bordes.abajo && (
              <button
                type="button"
                className="bm-mas"
                onClick={() => listaRef.current?.scrollBy({ top: 240, behavior: 'smooth' })}
              >
                Desliza para ver más <span aria-hidden="true">↓</span>
              </button>
            )}
          </div>

          <div className="bm-acciones">
            <button type="button" className="bm-primario" onClick={continuarACursos} disabled={cursos.length === 0}>
              Continuar <span aria-hidden="true">→</span>
            </button>
          </div>
        </div>
      )}

      {paso === 2 && (
        <div key="p2" className="bm-panel">
          <div className="bm-panel-cab">
            <div>
              <h2>¿Cuándo tienes tiempo libre?</h2>
              <p className="bm-ayuda">Haz clic o arrastra sobre el calendario para marcar las horas en que puedes reunirte.</p>
            </div>
          </div>

          <div className="bm-atajos">
            <span className="bm-atajos-txt">Atajos:</span>
            {ATAJOS.map((a) => (
              <button key={a.id} type="button" className="bm-atajo" onClick={() => aplicarAtajo(a)}>
                {a.nombre} <small>{pad(a.desde)}–{pad(a.hasta)} h</small>
              </button>
            ))}
            {totalHoras > 0 && (
              <button type="button" className="bm-atajo limpiar" onClick={() => setCeldas(new Set())}>Limpiar</button>
            )}
          </div>

          <div className="bm-horario">
            <div className="bm-cal-scroll">
              <div className="bm-cal" role="grid" aria-label="Calendario semanal de horario libre">
                <div className="bm-cal-esquina" />
                {DIAS.map((d, i) => (
                  <div key={d} className={`bm-cal-dia${semana[i].esHoy ? ' hoy' : ''}`} role="columnheader">
                    <span>{d.slice(0, 3)}</span>
                    <strong>{semana[i].dia}</strong>
                  </div>
                ))}
                {HORAS.map((h) => (
                  <div key={h} className="bm-cal-fila" role="row">
                    <div className="bm-cal-hora" role="rowheader">{hora(h)}</div>
                    {DIAS.map((d, i) => {
                      const clave = `${i + 1}-${h}`;
                      const on = celdas.has(clave);
                      const unirArriba = on && celdas.has(`${i + 1}-${h - 1}`);
                      const unirAbajo = on && celdas.has(`${i + 1}-${h + 1}`);
                      let fin = h + 1;
                      if (on && !unirArriba) while (celdas.has(`${i + 1}-${fin}`)) fin += 1;
                      return (
                        <button
                          key={clave}
                          type="button"
                          role="gridcell"
                          className={`bm-celda${on ? ' marcada' : ''}${unirArriba ? ' une-arriba' : ''}${unirAbajo ? ' une-abajo' : ''}${semana[i].esHoy ? ' hoy' : ''}`}
                          aria-pressed={on}
                          aria-label={`${d} ${hora(h)}`}
                          onPointerDown={(e) => empezarArrastre(e, clave)}
                          onPointerEnter={() => pasarPorCelda(clave)}
                          onClick={(e) => { if (e.detail === 0) marcarCelda(clave, !on); }}
                        >
                          {on && !unirArriba && <span className="bm-celda-txt">{hora(h)}–{hora(fin)}</span>}
                        </button>
                      );
                    })}
                  </div>
                ))}
              </div>
            </div>

            <aside className="bm-resumen" aria-live="polite">
              <p className="bm-resumen-tit">Tu disponibilidad</p>
              <p className="bm-resumen-total"><strong>{totalHoras}</strong> {totalHoras === 1 ? 'hora' : 'horas'} a la semana</p>
              {rangos.length === 0 ? (
                <p className="bm-resumen-vacio">Aún no marcas ningún horario.</p>
              ) : (
                <ul className="bm-resumen-lista">
                  {DIAS.map((d, i) => {
                    const delDia = rangos.filter((r) => r.diaSemana === i + 1);
                    if (delDia.length === 0) return null;
                    return (
                      <li key={d}>
                        <span className="bm-resumen-dia">{d}</span>
                        <span className="bm-resumen-rangos">
                          {delDia.map((r) => <span key={r.horaInicio}>{r.horaInicio}–{r.horaFin}</span>)}
                        </span>
                      </li>
                    );
                  })}
                </ul>
              )}
            </aside>
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
          <p className="bm-nota-cont">{nota.length}/{MAX_NOTA}</p>
          <div className="bm-acciones">
            <button type="button" className="bm-secundario" onClick={() => { setError(''); setPaso(1); }}>Atrás</button>
            <button type="button" className="bm-primario" onClick={buscar} disabled={buscando}>
              {buscando ? <><span className="bm-girando" aria-hidden="true" /> Buscando con IA…</> : <>Buscar mentor <span aria-hidden="true">→</span></>}
            </button>
          </div>
        </div>
      )}

      {paso === 3 && resultado && (
        <div key="p3" className="bm-panel">
          <div className="bm-panel-cab">
            <div>
              <h2>Tus mentores recomendados</h2>
              {resultado.mensaje && <p className="bm-ayuda">{resultado.mensaje}</p>}
            </div>
            {resultado.mentores.length > 0 && (
              <p className={`bm-origen${resultado.generadoConIa ? ' ia' : ''}`}>
                {resultado.generadoConIa ? '✦ Recomendado por la IA' : 'Cálculo base (sin IA)'}
              </p>
            )}
          </div>

          <ol className="bm-resultados">
            {resultado.mentores.map((m, i) => (
              <li key={m.idMentor} className={`bm-mentor${i === 0 ? ' primero' : ''}`} style={{ '--i': i }}>
                <div className="bm-mentor-cab">
                  <div className="bm-mentor-perfil">
                    <div className="bm-avatar-marco">
                      <Avatar mentor={m} />
                      <span className="bm-puesto" aria-label={`Puesto ${i + 1}`}>{i + 1}</span>
                    </div>
                    <div>
                      {i === 0 && <p className="bm-mejor">Mejor coincidencia</p>}
                      <p className="bm-mentor-nombre">{m.nombres} {m.apellidos}</p>
                      <p className="bm-mentor-esp">{m.especialidad}</p>
                    </div>
                  </div>
                  <div className="bm-anillo" style={{ '--pct': m.porcentaje }} role="img" aria-label={`${m.porcentaje} por ciento de coincidencia`}>
                    <svg viewBox="0 0 44 44" aria-hidden="true">
                      <circle cx="22" cy="22" r="19" className="bm-anillo-fondo" />
                      <circle cx="22" cy="22" r="19" className="bm-anillo-valor" pathLength="100" />
                    </svg>
                    <span>{m.porcentaje}<small>%</small></span>
                  </div>
                </div>

                <p className="bm-razon">{m.razon}</p>

                <div className="bm-datos">
                  <div>
                    <p className="bm-datos-tit">Cursos en común</p>
                    <ul className="bm-chips">
                      {m.cursosEnComun.map((nombre) => (
                        <li key={nombre}><IconoCategoria id={cursoPorNombre.get(nombre)?.categoria || categoriaDe(nombre)} size={14} />{nombre}</li>
                      ))}
                    </ul>
                  </div>
                  <div>
                    <p className="bm-datos-tit">Horario en común</p>
                    <ul className="bm-chips horario">
                      {m.horariosEnComun.map((h) => (
                        <li key={`${h.diaSemana}-${h.horaInicio}`}>
                          <strong>{(DIAS[h.diaSemana - 1] || String(h.diaSemana)).slice(0, 3)}</strong> {recortar(h.horaInicio)}–{recortar(h.horaFin)}
                        </li>
                      ))}
                    </ul>
                  </div>

{m.fortalezas?.length > 0 && (
  <div>
    <p className="bm-datos-tit">Fortalezas</p>
    <ul className="bm-chips suave">
      {m.fortalezas.map((f) => <li key={f}>{f}</li>)}
    </ul>
  </div>
)}

{m.areas?.length > 0 && (
  <div>
    <p className="bm-datos-tit">Áreas</p>
    <ul className="bm-chips suave">
      {m.areas.map((a) => <li key={a}>{a}</li>)}
    </ul>
  </div>
)}

</div>

<div className="bm-acciones">
  <button
    type="button"
    className="bm-primario"
    onClick={() => setMentorSeleccionado(m)}
  >
    Agendar sesión
  </button>
</div>

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
