import { useEffect, useRef, useState } from 'react';
import './Sala.css';
import { useAuth } from '../../auth/authContext';
import { SalaCliente } from './salaCliente';
import { FONDOS_PREDEFINIDOS } from './fondoVirtual';
import SalaChat from './SalaChat';

const trazo = { fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round', strokeLinejoin: 'round' };

const ICONOS = {
  mic: <><rect x="9" y="3" width="6" height="11" rx="3" /><path d="M5 11a7 7 0 0 0 14 0M12 18v3" /></>,
  micOff: <><path d="M3 3l18 18" /><path d="M9 9v2a3 3 0 0 0 5 2.1M15 9.3V6a3 3 0 0 0-5.7-1.3" /><path d="M5 11a7 7 0 0 0 11 5.7M12 18v3M19 11a7 7 0 0 1-.6 2.8" /></>,
  cam: <><rect x="3" y="6" width="13" height="12" rx="2" /><path d="m16 10 5-3v10l-5-3" /></>,
  camOff: <><path d="M3 3l18 18" /><path d="M16 11V8a2 2 0 0 0-2-2H8M5 6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h9a2 2 0 0 0 1.6-.8" /><path d="m16 10 5-3v10l-3-1.8" /></>,
  pantalla: <><rect x="3" y="4" width="18" height="12" rx="2" /><path d="M8 20h8M12 16v4" /></>,
  fondo: <><rect x="3" y="4" width="18" height="16" rx="2" /><circle cx="12" cy="10" r="2.5" /><path d="M7 19c.8-3 2.6-4.5 5-4.5s4.2 1.5 5 4.5" /></>,
  chat: <><path d="M4 5h16v11H9l-5 4z" /><path d="M8 9h8M8 12h5" /></>,
  personas: <><circle cx="9" cy="8" r="3.5" /><path d="M2.5 20a6.5 6.5 0 0 1 13 0M16 4.5a3.5 3.5 0 0 1 0 7M18 14a6.5 6.5 0 0 1 3.5 6" /></>,
  salir: <><path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3" /><path d="M10 17l-5-5 5-5M5 12h11" /></>
};

function Icono({ nombre }) {
  return <svg width="22" height="22" viewBox="0 0 24 24" aria-hidden="true" {...trazo}>{ICONOS[nombre]}</svg>;
}

const iniciales = (nombre) => (nombre || '?').split(' ').filter(Boolean).slice(0, 2).map((p) => p[0]).join('').toUpperCase();
const horaCorta = (iso) => (iso ? new Date(iso).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' }) : '');

// Un <video> que muestra un MediaStream. Los videos propios van siempre silenciados (si no, se escucharía el eco).
function Video({ flujo, silenciado = false, espejo = false, ajuste = 'cover' }) {
  const ref = useRef(null);
  useEffect(() => {
    if (ref.current && ref.current.srcObject !== (flujo ?? null)) ref.current.srcObject = flujo ?? null;
  }, [flujo]);
  return <video ref={ref} autoPlay playsInline muted={silenciado} className={`sa-video${espejo ? ' espejo' : ''} ${ajuste}`} />;
}

// El cuadro de una persona: su cámara (o sus iniciales si la apagó), su nombre y si tiene el micrófono silenciado.
function Cuadro({ nombre, flujo, camara, microfono, local = false }) {
  return (
    <div className="sa-cuadro">
      {flujo && camara && <Video flujo={flujo} silenciado={local} espejo={local} />}
      {/* Con la cámara apagada igual se deja el video montado (oculto) para que el audio siga sonando. */}
      {flujo && !camara && !local && <div className="sa-oculto"><Video flujo={flujo} /></div>}
      {!camara && <div className="sa-iniciales" aria-hidden="true">{iniciales(nombre)}</div>}
      <span className="sa-cuadro-nombre">
        {!microfono && <span className="sa-silencio" title="Micrófono silenciado"><Icono nombre="micOff" /></span>}
        {local ? 'Tú' : nombre}
      </span>
    </div>
  );
}

// Elegir el fondo de la cámara: ninguno, desenfoque, una imagen incluida o una propia (que no sale de tu equipo).
function SelectorFondo({ s, cliente }) {
  const [miniaturas] = useState(() => FONDOS_PREDEFINIDOS.map((f) => ({ ...f, miniatura: f.crear().toDataURL('image/jpeg', 0.5) })));
  const archivoRef = useRef(null);
  const actual = s.opcionFondo;
  const ocupado = s.cambiandoFondo;

  async function subir(e) {
    const archivo = e.target.files?.[0];
    e.target.value = '';
    if (!archivo) return;
    try {
      const imagen = await createImageBitmap(archivo);
      cliente.cambiarFondo({ tipo: 'imagen', id: 'propia', fuente: imagen });
    } catch {
      cliente.cambiarFondo({ tipo: 'ninguno' });
    }
  }

  if (!s.tieneCamara) {
    return <p className="sa-nota">Necesitas una cámara para usar un fondo.</p>;
  }
  const clase = (activo) => `sa-fondo${activo ? ' activo' : ''}`;
  return (
    <div className="sa-fondos" role="group" aria-label="Fondo de la cámara">
      <button type="button" className={clase(actual.tipo === 'ninguno')} disabled={ocupado} onClick={() => cliente.cambiarFondo({ tipo: 'ninguno' })}>
        <span className="sa-fondo-muestra ninguno">Sin fondo</span>
      </button>
      <button type="button" className={clase(actual.tipo === 'desenfoque')} disabled={ocupado} onClick={() => cliente.cambiarFondo({ tipo: 'desenfoque' })}>
        <span className="sa-fondo-muestra desenfoque">Desenfocar</span>
      </button>
      {miniaturas.map((f) => (
        <button key={f.id} type="button" className={clase(actual.tipo === 'imagen' && actual.id === f.id)} disabled={ocupado}
          onClick={() => cliente.cambiarFondo({ tipo: 'imagen', id: f.id, fuente: f.crear() })}>
          <span className="sa-fondo-muestra" style={{ backgroundImage: `url(${f.miniatura})` }}>{f.nombre}</span>
        </button>
      ))}
      <button type="button" className={clase(actual.tipo === 'imagen' && actual.id === 'propia')} disabled={ocupado} onClick={() => archivoRef.current?.click()}>
        <span className="sa-fondo-muestra subir">Subir imagen…</span>
      </button>
      <input ref={archivoRef} type="file" accept="image/png,image/jpeg,image/webp" hidden onChange={subir} />
      {ocupado && <p className="sa-nota" role="status">Preparando el fondo… la primera vez puede tardar unos segundos.</p>}
    </div>
  );
}

// Pantalla previa: ver cómo sales, elegir fondo, encender o apagar cámara y micrófono, y entrar.
function Previa({ s, cliente, onCerrar }) {
  const info = s.info;
  const reintentando = s.fase === 'conectando';
  return (
    <div className="sa-previa">
      <div className="sa-previa-video">
        {s.flujoLocal && s.camaraEncendida
          ? <Video flujo={s.flujoLocal} silenciado espejo />
          : <div className="sa-iniciales grande" aria-hidden="true">{iniciales(s.yo?.nombre || 'Tú')}</div>}
        <div className="sa-previa-controles">
          <button type="button" className={`sa-redondo${s.microfonoEncendido ? '' : ' apagado'}`} disabled={!s.tieneMicrofono}
            aria-pressed={s.microfonoEncendido} aria-label={s.microfonoEncendido ? 'Silenciar micrófono' : 'Activar micrófono'} onClick={() => cliente.alternarMicrofono()}>
            <Icono nombre={s.microfonoEncendido ? 'mic' : 'micOff'} />
          </button>
          <button type="button" className={`sa-redondo${s.camaraEncendida ? '' : ' apagado'}`} disabled={!s.tieneCamara}
            aria-pressed={s.camaraEncendida} aria-label={s.camaraEncendida ? 'Apagar cámara' : 'Encender cámara'} onClick={() => cliente.alternarCamara()}>
            <Icono nombre={s.camaraEncendida ? 'cam' : 'camOff'} />
          </button>
        </div>
      </div>

      <div className="sa-previa-lado">
        <p className="sa-eyebrow">{info.tipo === 'CHARLA' ? 'Charla virtual' : 'Sesión de mentoría'}</p>
        <h2>{info.titulo}</h2>
        <p className="sa-nota">
          Abierta de {horaCorta(info.abre)} a {horaCorta(info.cierra)}
          {info.tipo === 'CHARLA' ? ` · hasta ${info.maxParticipantes} personas` : ''}
        </p>
        {!info.puedeEntrar && <p className="sa-error" role="status">{info.motivo}</p>}
        {s.error && <p className="sa-error" role="alert">{s.error}</p>}
        {s.avisoMedios && (
          <p className="sa-aviso" role="status">
            {s.avisoMedios}
            {!s.tieneCamara && !s.tieneMicrofono && <> <button type="button" className="sa-enlace" onClick={() => cliente.reintentarMedios()}>Reintentar</button></>}
          </p>
        )}

        <h3 className="sa-subtitulo">Fondo</h3>
        <SelectorFondo s={s} cliente={cliente} />

        <div className="sa-previa-botones">
          <button type="button" className="sa-boton" onClick={onCerrar}>Cancelar</button>
          <button type="button" className="sa-boton primario grande" disabled={!info.puedeEntrar || reintentando} onClick={() => cliente.unirse()}>
            {reintentando ? 'Entrando…' : s.fase === 'cerrada' ? 'Volver a entrar' : 'Unirme a la sala'}
          </button>
        </div>
      </div>
    </div>
  );
}

function Personas({ s }) {
  const filas = [
    { id: s.yo.id, nombre: `${s.yo.nombre} (tú)`, rol: s.yo.rol, camara: s.camaraEncendida && s.tieneCamara, microfono: s.microfonoEncendido && s.tieneMicrofono, pantalla: Boolean(s.pantallaLocal) },
    ...s.participantes.map((p) => ({ id: p.id, nombre: p.nombre, rol: p.rol, camara: p.estado?.camara, microfono: p.estado?.microfono, pantalla: p.estado?.pantalla }))
  ];
  return (
    <ul className="sa-personas" aria-label="Participantes">
      {filas.map((f) => (
        <li key={f.id}>
          <span className="sa-avatar" aria-hidden="true">{iniciales(f.nombre)}</span>
          <span className="sa-persona-nombre">{f.nombre}<small>{f.rol === 'MENTOR' ? 'Mentor' : 'Estudiante'}</small></span>
          <span className="sa-persona-estado">
            {f.pantalla && <span title="Compartiendo pantalla"><Icono nombre="pantalla" /></span>}
            <span className={f.microfono ? '' : 'apagado'} title={f.microfono ? 'Micrófono encendido' : 'Micrófono silenciado'}><Icono nombre={f.microfono ? 'mic' : 'micOff'} /></span>
            <span className={f.camara ? '' : 'apagado'} title={f.camara ? 'Cámara encendida' : 'Cámara apagada'}><Icono nombre={f.camara ? 'cam' : 'camOff'} /></span>
          </span>
        </li>
      ))}
    </ul>
  );
}

// El flujo de video de la cámara de alguien y el de su pantalla compartida llegan por separado: se distinguen por el id que
// cada persona anuncia en su estado.
const flujoPantalla = (p) => (p.estado?.pantalla ? p.streams.find((f) => f.id === p.estado.pantallaStream) ?? null : null);
const flujoCamara = (p) => p.streams.find((f) => f.id !== p.estado?.pantallaStream) ?? null;

function Llamada({ s, cliente, onCerrar }) {
  const [panel, setPanel] = useState('chat'); // chat | personas | null
  const [verFondo, setVerFondo] = useState(false);

  const compartiendo = s.participantes.find((p) => flujoPantalla(p));
  const cuadros = [
    <Cuadro key="yo" nombre={s.yo.nombre} flujo={s.flujoLocal} camara={s.camaraEncendida && s.tieneCamara} microfono={s.microfonoEncendido && s.tieneMicrofono} local />,
    ...s.participantes.map((p) => (
      <Cuadro key={p.id} nombre={p.nombre} flujo={flujoCamara(p)} camara={Boolean(p.estado?.camara)} microfono={Boolean(p.estado?.microfono)} />
    ))
  ];
  const alternarPanel = (nombre) => setPanel((actual) => (actual === nombre ? null : nombre));

  function salir() {
    cliente.salir();
    onCerrar();
  }

  return (
    <div className="sa-llamada">
      <header className="sa-barra">
        <div>
          <strong>{s.sala.titulo}</strong>
          <span className="sa-barra-sub">{s.participantes.length + 1} {s.participantes.length === 0 ? 'persona' : 'personas'} · cierra a las {horaCorta(s.sala.cierra)}</span>
        </div>
      </header>

      <div className={`sa-cuerpo${panel ? ' con-panel' : ''}`}>
        <main className="sa-escenario">
          {compartiendo && (
            <div className="sa-principal">
              <Video flujo={flujoPantalla(compartiendo)} silenciado ajuste="contain" />
              <span className="sa-cuadro-nombre">Pantalla de {compartiendo.nombre}</span>
            </div>
          )}
          {s.pantallaLocal && !compartiendo && (
            <div className="sa-principal aviso-compartiendo">
              <p><Icono nombre="pantalla" /> Estás compartiendo tu pantalla</p>
              <p className="sa-nota">Los demás ven tu pantalla. Aquí no se muestra para evitar el efecto espejo.</p>
              <button type="button" className="sa-boton peligro" onClick={() => cliente.dejarDeCompartir()}>Dejar de compartir</button>
            </div>
          )}
          <div className={`sa-cuadros${compartiendo || s.pantallaLocal ? ' tira' : ` n${Math.min(cuadros.length, 6)}`}`}>{cuadros}</div>
        </main>

        {panel && (
          <aside className="sa-panel" aria-label={panel === 'chat' ? 'Chat' : 'Participantes'}>
            <div className="sa-panel-cab">
              <strong>{panel === 'chat' ? 'Chat' : 'Participantes'}</strong>
              <button type="button" className="sa-enlace" onClick={() => setPanel(null)} aria-label="Cerrar panel">Cerrar</button>
            </div>
            {panel === 'chat'
              ? <SalaChat mensajes={s.mensajes} yoId={s.yo.id} aviso={s.avisoChat} onTexto={(t) => cliente.enviarTexto(t)} onCodigo={(l, c) => cliente.enviarCodigo(l, c)} />
              : <Personas s={s} />}
          </aside>
        )}
      </div>

      {(s.avisoMedios) && <p className="sa-aviso flotante" role="status">{s.avisoMedios}</p>}
      {verFondo && (
        <div className="sa-popover" role="dialog" aria-label="Fondo de la cámara">
          <div className="sa-panel-cab"><strong>Fondo de la cámara</strong><button type="button" className="sa-enlace" onClick={() => setVerFondo(false)}>Cerrar</button></div>
          <SelectorFondo s={s} cliente={cliente} />
        </div>
      )}

      <footer className="sa-controles">
        <button type="button" className={`sa-control${s.microfonoEncendido ? '' : ' apagado'}`} disabled={!s.tieneMicrofono} aria-pressed={s.microfonoEncendido} onClick={() => cliente.alternarMicrofono()}>
          <Icono nombre={s.microfonoEncendido ? 'mic' : 'micOff'} /><span>{s.microfonoEncendido ? 'Silenciar' : 'Activar'}</span>
        </button>
        <button type="button" className={`sa-control${s.camaraEncendida ? '' : ' apagado'}`} disabled={!s.tieneCamara} aria-pressed={s.camaraEncendida} onClick={() => cliente.alternarCamara()}>
          <Icono nombre={s.camaraEncendida ? 'cam' : 'camOff'} /><span>{s.camaraEncendida ? 'Cámara' : 'Encender'}</span>
        </button>
        <button type="button" className={`sa-control${s.pantallaLocal ? ' activo' : ''}`} aria-pressed={Boolean(s.pantallaLocal)}
          onClick={() => (s.pantallaLocal ? cliente.dejarDeCompartir() : cliente.compartirPantalla())}>
          <Icono nombre="pantalla" /><span>{s.pantallaLocal ? 'Dejar de compartir' : 'Compartir'}</span>
        </button>
        <button type="button" className={`sa-control${verFondo ? ' activo' : ''}`} disabled={!s.tieneCamara} aria-expanded={verFondo} onClick={() => setVerFondo((v) => !v)}>
          <Icono nombre="fondo" /><span>Fondo</span>
        </button>
        <button type="button" className={`sa-control${panel === 'chat' ? ' activo' : ''}`} aria-pressed={panel === 'chat'} onClick={() => alternarPanel('chat')}>
          <Icono nombre="chat" /><span>Chat</span>
        </button>
        <button type="button" className={`sa-control${panel === 'personas' ? ' activo' : ''}`} aria-pressed={panel === 'personas'} onClick={() => alternarPanel('personas')}>
          <Icono nombre="personas" /><span>Personas</span>
        </button>
        <button type="button" className="sa-control salir" onClick={salir}>
          <Icono nombre="salir" /><span>Salir</span>
        </button>
      </footer>
    </div>
  );
}

// HU-08: sala virtual de una sesión de mentoría o de una charla. Pantalla completa con videollamada, pantalla compartida,
// fondo virtual y un chat donde se comparten mensajes y fragmentos de código. tipo: "sesion" o "charla".
export default function Sala({ tipo, id, onCerrar }) {
  const { authFetch } = useAuth();
  // El cliente y su última instantánea van juntos: cada aviso del cliente trae las dos cosas.
  const [estado, setEstado] = useState(null);
  const s = estado?.snap ?? null;
  const cliente = estado?.cliente ?? null;

  useEffect(() => {
    const nuevo = new SalaCliente({ authFetch, tipo, id, alCambio: (snap) => setEstado({ snap, cliente: nuevo }) });
    nuevo.iniciar();
    return () => nuevo.destruir();
  }, [authFetch, tipo, id]);

  // Con la sala abierta no se debe desplazar la página de atrás.
  useEffect(() => {
    const antes = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = antes; };
  }, []);

  return (
    <div className="sa" role="dialog" aria-modal="true" aria-label="Sala virtual">
      {(!s || !cliente || s.fase === 'cargando') && <p className="sa-centro" role="status">Preparando la sala…</p>}
      {s?.fase === 'error' && (
        <div className="sa-centro">
          <p className="sa-error" role="alert">{s.error}</p>
          <button type="button" className="sa-boton primario" onClick={onCerrar}>Cerrar</button>
        </div>
      )}
      {cliente && s && (s.fase === 'previa' || s.fase === 'conectando' || s.fase === 'cerrada') && <Previa s={s} cliente={cliente} onCerrar={onCerrar} />}
      {cliente && s && s.fase === 'dentro' && <Llamada s={s} cliente={cliente} onCerrar={onCerrar} />}
    </div>
  );
}
