import { useEffect, useRef, useState } from 'react';
import './Calendario.css';
import { useAuth } from '../auth/authContext';
import Cargando from './Cargando';

const DIAS_CORTOS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];

const hora = (t) => String(t).slice(0, 5);
const aIso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const deIso = (iso) => { const [a, m, d] = iso.split('-').map(Number); return new Date(a, m - 1, d); };
const sumarDias = (d, n) => { const x = new Date(d.getFullYear(), d.getMonth(), d.getDate()); x.setDate(x.getDate() + n); return x; };
// La semana empieza el lunes.
const inicioSemana = (d) => sumarDias(d, -((d.getDay() + 6) % 7));
const mayuscula = (t) => t.charAt(0).toUpperCase() + t.slice(1);
const largo = (d) => mayuscula(d.toLocaleDateString('es-PE', { weekday: 'long', day: 'numeric', month: 'long' }));

function tituloRango(vista, desde, hasta) {
  if (vista === 'dia') return `${largo(desde)} de ${desde.getFullYear()}`;
  const mes = (d) => d.toLocaleDateString('es-PE', { month: 'short' }).replace('.', '');
  if (desde.getMonth() === hasta.getMonth()) return `${desde.getDate()} – ${hasta.getDate()} ${mes(hasta)} ${hasta.getFullYear()}`;
  return `${desde.getDate()} ${mes(desde)} – ${hasta.getDate()} ${mes(hasta)} ${hasta.getFullYear()}`;
}

// Ventana con el detalle de un evento. Escape o tocar el fondo la cierra.
function DetalleEvento({ evento, onCerrar }) {
  const caja = useRef(null);
  const cerrarRef = useRef(onCerrar);
  useEffect(() => { cerrarRef.current = onCerrar; });
  useEffect(() => {
    const anterior = document.activeElement;
    caja.current?.focus();
    const escape = (e) => { if (e.key === 'Escape') cerrarRef.current(); };
    document.addEventListener('keydown', escape);
    return () => {
      document.removeEventListener('keydown', escape);
      anterior?.focus?.();
    };
  }, []);

  const esSesion = evento.tipo === 'SESION';
  return (
    <div className="ca-velo" onMouseDown={(e) => { if (e.target === e.currentTarget) onCerrar(); }}>
      <div className="ca-modal" role="dialog" aria-modal="true" aria-label={evento.titulo} tabIndex={-1} ref={caja}>
        <span className={`ca-tipo ${esSesion ? 'sesion' : 'charla'}`}>{esSesion ? 'Sesión de mentoría' : 'Charla virtual'}</span>
        <h2>{evento.titulo}</h2>
        <p className="ca-modal-dato">{evento.detalle}</p>
        <p className="ca-modal-dato">{largo(deIso(evento.fecha))} · {hora(evento.horaInicio)} – {hora(evento.horaFin)}</p>
        {evento.enlace && (
          <a className="ca-modal-enlace" href={evento.enlace} target="_blank" rel="noreferrer">
            {esSesion ? 'Unirme a la reunión' : 'Abrir el enlace de la charla'}
          </a>
        )}
        {evento.enlace && <p className="ca-modal-url">{evento.enlace}</p>}
        <div className="ca-modal-botones">
          <button type="button" className="ca-boton" onClick={onCerrar}>Cerrar</button>
        </div>
      </div>
    </div>
  );
}

// HU-26: calendario con las sesiones agendadas y las charlas inscritas, por semana o por día. Al elegir un evento se ve
// su detalle y el enlace de la reunión. Las sesiones van rellenas en negro y las charlas con borde punteado.
export default function Calendario() {
  const { authFetch, usuario } = useAuth();
  const esEstudiante = usuario.rol === 'ESTUDIANTE';
  const [vista, setVista] = useState('semana');
  const [ancla, setAncla] = useState(() => { const h = new Date(); return new Date(h.getFullYear(), h.getMonth(), h.getDate()); });
  const [eventos, setEventos] = useState(null);
  const [error, setError] = useState('');
  const [seleccionado, setSeleccionado] = useState(null);

  const desde = vista === 'semana' ? inicioSemana(ancla) : ancla;
  const hasta = vista === 'semana' ? sumarDias(desde, 6) : ancla;
  const dias = Array.from({ length: vista === 'semana' ? 7 : 1 }, (_, i) => sumarDias(desde, i));
  const desdeIso = aIso(desde);
  const hastaIso = aIso(hasta);
  const hoyIso = aIso(new Date());

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const response = await authFetch(`/api/calendario?desde=${desdeIso}&hasta=${hastaIso}`);
        const data = await response.json().catch(() => null);
        if (!response.ok) throw new Error(data?.error || '');
        if (!activo) return;
        setEventos(data);
        setError('');
      } catch (e) {
        if (!activo) return;
        setError(e.message || 'No se pudo cargar el calendario.');
        setEventos((actual) => actual ?? []);
      }
    })();
    return () => { activo = false; };
  }, [authFetch, desdeIso, hastaIso]);

  const mover = (n) => { setEventos(null); setAncla(sumarDias(ancla, n * (vista === 'semana' ? 7 : 1))); };
  const irAHoy = () => { const h = new Date(); setEventos(null); setAncla(new Date(h.getFullYear(), h.getMonth(), h.getDate())); };
  const cambiarVista = (v) => { if (v !== vista) { setEventos(null); setVista(v); } };

  const deldia = (iso) => (eventos ?? []).filter((e) => e.fecha === iso);

  return (
    <section className="ca" aria-label="Calendario">
      <div className="ca-barra">
        <div className="ca-navegar">
          <button type="button" className="ca-boton icono" onClick={() => mover(-1)} aria-label={vista === 'semana' ? 'Semana anterior' : 'Día anterior'}>‹</button>
          <button type="button" className="ca-boton" onClick={irAHoy}>Hoy</button>
          <button type="button" className="ca-boton icono" onClick={() => mover(1)} aria-label={vista === 'semana' ? 'Semana siguiente' : 'Día siguiente'}>›</button>
        </div>
        <h2 className="ca-rango">{tituloRango(vista, desde, hasta)}</h2>
        <div className="ca-vistas" role="tablist" aria-label="Vista del calendario">
          <button type="button" role="tab" aria-selected={vista === 'semana'} className={`ca-vista${vista === 'semana' ? ' activo' : ''}`} onClick={() => cambiarVista('semana')}>Semana</button>
          <button type="button" role="tab" aria-selected={vista === 'dia'} className={`ca-vista${vista === 'dia' ? ' activo' : ''}`} onClick={() => cambiarVista('dia')}>Día</button>
        </div>
      </div>

      <ul className="ca-leyenda" aria-label="Leyenda">
        <li><span className="ca-muestra sesion" aria-hidden="true" /> Sesión de mentoría</li>
        {esEstudiante && <li><span className="ca-muestra charla" aria-hidden="true" /> Charla virtual</li>}
      </ul>

      {error && <p className="ca-error" role="alert">{error}</p>}
      {eventos === null && <Cargando texto="Cargando tu calendario…" />}

      {eventos !== null && (
        <ol className={`ca-dias ${vista}`}>
          {dias.map((d) => {
            const iso = aIso(d);
            const lista = deldia(iso);
            return (
              <li key={iso} className={`ca-dia${iso === hoyIso ? ' hoy' : ''}`}>
                <div className="ca-dia-cab">
                  <span className="ca-dia-nombre">{DIAS_CORTOS[(d.getDay() + 6) % 7]}</span>
                  <span className="ca-dia-numero">{d.getDate()}</span>
                </div>
                {lista.length === 0 ? (
                  <p className="ca-libre">{vista === 'dia' ? 'Sin sesiones ni charlas este día.' : '—'}</p>
                ) : (
                  <ul className="ca-eventos">
                    {lista.map((e) => (
                      <li key={`${e.tipo}-${e.id}`}>
                        <button type="button" className={`ca-evento ${e.tipo === 'SESION' ? 'sesion' : 'charla'}`} onClick={() => setSeleccionado(e)}>
                          <span className="ca-hora">{hora(e.horaInicio)} – {hora(e.horaFin)}</span>
                          <span className="ca-titulo">{e.titulo}</span>
                          <span className="ca-detalle">{e.detalle}</span>
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </li>
            );
          })}
        </ol>
      )}

      {eventos !== null && eventos.length === 0 && !error && (
        <p className="ca-vacio">
          No tienes {esEstudiante ? 'sesiones ni charlas' : 'sesiones'} {vista === 'semana' ? 'esta semana' : 'este día'}.
          {esEstudiante && ' Agenda una mentoría o inscríbete a una charla para verla aquí.'}
        </p>
      )}

      {seleccionado && <DetalleEvento evento={seleccionado} onCerrar={() => setSeleccionado(null)} />}
    </section>
  );
}
