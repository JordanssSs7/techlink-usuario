import { lazy, Suspense, useCallback, useEffect, useState } from 'react';
import './Charlas.css';
import { useAuth } from '../auth/authContext';
import Cargando from './Cargando';

// HU-08: la sala virtual (con el resaltado de código y el fondo virtual) se baja solo cuando alguien la abre.
const Sala = lazy(() => import('./sala/Sala'));

const fechaLarga = (iso) => {
  const [a, m, d] = iso.split('-').map(Number);
  const texto = new Date(a, m - 1, d).toLocaleDateString('es-PE', { weekday: 'long', day: 'numeric', month: 'long' });
  return texto.charAt(0).toUpperCase() + texto.slice(1);
};
const hora = (t) => String(t).slice(0, 5);
const MESES = ['ENE', 'FEB', 'MAR', 'ABR', 'MAY', 'JUN', 'JUL', 'AGO', 'SEP', 'OCT', 'NOV', 'DIC'];

// Una charla: nombre, temática, fecha y hora (HU-06), con el botón para inscribirse o anular (HU-25).
// onInscribir y onAnular devuelven el texto de error del servidor, o una cadena vacía si todo salió bien.
function TarjetaCharla({ charla, onInscribir, onAnular, onEntrar }) {
  const [error, setError] = useState('');
  const [trabajando, setTrabajando] = useState(false);

  async function ejecutar(accion) {
    setTrabajando(true);
    setError('');
    setError(await accion(charla));
    setTrabajando(false);
  }

  const terminada = !charla.vigente;
  const [a, m, d] = charla.fecha.split('-').map(Number);
  const ahora = new Date();
  const inicio = new Date(a, m - 1, d, ...charla.horaInicio.split(':').map(Number).slice(0, 2));
  // Sigue «en vivo» aunque el invitado se pase un poco de la hora de fin: el servidor la mantiene vigente mientras él esté en la sala.
  const enVivo = !terminada && ahora >= inicio;
  const hoy = !terminada && !enVivo && inicio.toDateString() === ahora.toDateString();

  return (
    <li className={`ch-tarjeta${terminada ? ' terminada' : ''}${enVivo ? ' en-vivo' : ''}${charla.imagen ? ' con-imagen' : ''}`}
      style={charla.imagen ? { '--ch-foto': `url(${charla.imagen})` } : undefined}>
      <div className="ch-fecha" aria-hidden="true">
        <strong>{d}</strong>
        <span>{MESES[m - 1]}</span>
      </div>

      <div className="ch-contenido">
        <div className="ch-cabecera">
          <h3>{charla.nombre}</h3>
          {enVivo && <span className="ch-sello vivo"><i aria-hidden="true" /> En vivo</span>}
          {hoy && <span className="ch-sello hoy">Hoy</span>}
          {charla.inscrito && !terminada && !enVivo && <span className="ch-sello">✓ Inscrito</span>}
          {terminada && <span className="ch-sello apagado">{charla.activa ? 'Finalizada' : 'Desactivada'}</span>}
        </div>

        <p className="ch-etiquetas">
          <span className="ch-chip">{charla.tematica}</span>
          {charla.curso && <span className="ch-chip suave">Refuerza {charla.curso}</span>}
        </p>
        {charla.razon && <p className="ch-razon">★ {charla.razon}</p>}
        {charla.descripcion && <p className="ch-descripcion">{charla.descripcion}</p>}

        <p className="ch-cuando">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" />
          </svg>
          {fechaLarga(charla.fecha)} · {hora(charla.horaInicio)} – {hora(charla.horaFin)}
        </p>

        {error && <p className="ch-error" role="alert">{error}</p>}
        {!terminada && (
          <div className="ch-acciones">
            {charla.inscrito ? (
              <>
                {/* HU-08: sala virtual de la charla: el invitado habla y quien quiera preguntar levanta la mano. */}
                <button type="button" className="ch-boton primario" onClick={() => onEntrar(charla)}>
                  {enVivo ? 'Entrar ahora' : 'Entrar a la sala'}
                </button>
                <button type="button" className="ch-boton" disabled={trabajando} onClick={() => ejecutar(onAnular)}>Anular inscripción</button>
              </>
            ) : (
              <button type="button" className="ch-boton primario" disabled={trabajando} onClick={() => ejecutar(onInscribir)}>
                {trabajando ? 'Inscribiendo…' : 'Inscribirme'}
              </button>
            )}
          </div>
        )}
      </div>
    </li>
  );
}

// HU-06: charlas recomendadas según los intereses y los cursos críticos del estudiante.
// HU-25: inscribirse a una charla vigente y ver "Mis charlas".
export default function Charlas({ salaInicial = null }) {
  const { authFetch } = useAuth();
  const [pestana, setPestana] = useState('recomendadas');
  const [recomendadas, setRecomendadas] = useState(null);
  const [mias, setMias] = useState(null);
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');
  const [version, setVersion] = useState(0);
  const [salaCharla, setSalaCharla] = useState(salaInicial); // HU-08: id de la charla cuya sala virtual está abierta
  const recargar = useCallback(() => setVersion((v) => v + 1), []);

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const [r1, r2] = await Promise.all([authFetch('/api/charlas/recomendadas'), authFetch('/api/charlas/mias')]);
        const d1 = await r1.json().catch(() => null);
        const d2 = await r2.json().catch(() => null);
        if (!r1.ok) throw new Error(d1?.error || '');
        if (!r2.ok) throw new Error(d2?.error || '');
        if (!activo) return;
        setRecomendadas(d1);
        setMias(d2);
        setError('');
      } catch (e) {
        if (!activo) return;
        setError(e.message || 'No se pudieron cargar las charlas.');
        setRecomendadas((actual) => actual ?? { recomendadas: [], otras: [], perfilCompleto: true });
        setMias((actual) => actual ?? []);
      }
    })();
    return () => { activo = false; };
  }, [authFetch, version]);

  useEffect(() => {
    if (!aviso) return undefined;
    const t = setTimeout(() => setAviso(''), 5000);
    return () => clearTimeout(t);
  }, [aviso]);

  async function inscribir(charla) {
    try {
      const response = await authFetch(`/api/charlas/${charla.id}/inscripcion`, { method: 'POST' });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        // 409: ya estaba inscrito (otra pestaña) o la charla terminó. Se actualiza la lista para mostrar la verdad.
        if (response.status === 409) recargar();
        return data.error || 'No se pudo completar la inscripción.';
      }
      setAviso(`Te inscribiste a "${charla.nombre}". La verás en tu calendario.`);
      recargar();
      return '';
    } catch {
      return 'No se pudo conectar con el servidor.';
    }
  }

  async function anular(charla) {
    try {
      const response = await authFetch(`/api/charlas/${charla.id}/inscripcion`, { method: 'DELETE' });
      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        if (response.status === 404 || response.status === 409) recargar();
        return data.error || 'No se pudo anular la inscripción.';
      }
      setAviso(`Anulaste tu inscripción a "${charla.nombre}".`);
      recargar();
      return '';
    } catch {
      return 'No se pudo conectar con el servidor.';
    }
  }

  const cargando = recomendadas === null || mias === null;
  const proximas = (mias ?? []).filter((c) => c.vigente).length;

  return (
    <section className="ch" aria-label="Charlas virtuales">
      <div className="ch-pestanas" role="tablist" aria-label="Charlas">
        <button type="button" role="tab" aria-selected={pestana === 'recomendadas'} className={`ch-pestana${pestana === 'recomendadas' ? ' activo' : ''}`}
          onClick={() => setPestana('recomendadas')}>Para ti</button>
        <button type="button" role="tab" aria-selected={pestana === 'mias'} className={`ch-pestana${pestana === 'mias' ? ' activo' : ''}`}
          onClick={() => setPestana('mias')}>
          Mis charlas{proximas > 0 && <span className="ch-contador">{proximas}</span>}
        </button>
      </div>

      {aviso && <p className="ch-aviso" role="status">{aviso}</p>}
      {error && <p className="ch-error" role="alert">{error}</p>}
      {cargando && <Cargando texto="Cargando charlas…" />}

      {!cargando && pestana === 'recomendadas' && (
        <>
          {!recomendadas.perfilCompleto && (
            <p className="ch-nota">
              Todavía no podemos recomendarte charlas: agrega tus intereses en <strong>Mi cuenta</strong> o marca tus cursos críticos
              en <strong>Buscar mentor</strong>. Mientras tanto, estas son todas las charlas próximas.
            </p>
          )}
          {recomendadas.recomendadas.length > 0 && (
            <>
              <h2 className="ch-titulo">Recomendadas para ti</h2>
              <ul className="ch-lista">
                {recomendadas.recomendadas.map((c) => <TarjetaCharla key={c.id} charla={c} onInscribir={inscribir} onAnular={anular} onEntrar={(ch) => setSalaCharla(ch.id)} />)}
              </ul>
            </>
          )}
          {recomendadas.otras.length > 0 && (
            <>
              <h2 className="ch-titulo">{recomendadas.recomendadas.length > 0 ? 'Otras charlas próximas' : 'Charlas próximas'}</h2>
              <ul className="ch-lista">
                {recomendadas.otras.map((c) => <TarjetaCharla key={c.id} charla={c} onInscribir={inscribir} onAnular={anular} onEntrar={(ch) => setSalaCharla(ch.id)} />)}
              </ul>
            </>
          )}
          {recomendadas.recomendadas.length === 0 && recomendadas.otras.length === 0 && !error && (
            <div className="ch-vacio">
              <strong>No hay charlas próximas por ahora</strong>
              <p>Cuando el equipo de TechLink programe una con un invitado, la verás aquí y podrás inscribirte.</p>
            </div>
          )}
        </>
      )}

      {!cargando && pestana === 'mias' && (
        <>
          {mias.length === 0 && !error && (
            <div className="ch-vacio">
              <strong>Todavía no te inscribiste a ninguna charla</strong>
              <p>Elige una en la pestaña «Para ti» y aparecerá aquí.</p>
            </div>
          )}
          <ul className="ch-lista">
            {mias.map((c) => <TarjetaCharla key={c.id} charla={c} onInscribir={inscribir} onAnular={anular} onEntrar={(ch) => setSalaCharla(ch.id)} />)}
          </ul>
        </>
      )}
      {salaCharla && <Suspense fallback={null}><Sala tipo="charla" id={salaCharla} onCerrar={() => setSalaCharla(null)} /></Suspense>}
    </section>
  );
}
