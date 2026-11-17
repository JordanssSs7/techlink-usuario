import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import './AsistenteChat.css';
import { useAuth } from '../auth/authContext';
import { LLAMA } from './llamaImagenes';
import { callar, escucharFrase, hablar, hayReconocimiento } from './voz';

const DIAS = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo'];
const MAX_MENSAJES = 12; // los que se mandan al agente en cada turno
const MAX_CHATS = 30;
const CLAVE_TAMANO = 'techlink_chat_tamano';
// Frases que no vale la pena mandar al agente: ruido, muletillas o palabras sueltas muy cortas.
const RELLENOS = /^(eh+|mm+|ah+|uh+|um+|hm+|ok|oh+)$/i;
function utilizable(frase, confianza) {
  const limpia = (frase || '').trim();
  const palabras = limpia.split(/\s+/).filter(Boolean);
  if (confianza > 0 && confianza < 0.4) return false;
  if (palabras.length === 1 && (limpia.length < 4 || RELLENOS.test(limpia))) return false;
  return limpia.length >= 2;
}

const SUGERENCIAS = ['Voy mal en Base de Datos', 'Tengo libres los lunes por la mañana', '¿Cómo funciona TechLink?'];

const recortar = (t) => String(t).slice(0, 5);
const nuevoId = () => `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`;

// Las conversaciones se guardan en este navegador, una lista por usuario.
const clave = (id) => `techlink_chats_${id}`;
function cargarChats(idUsuario) {
  try {
    const lista = JSON.parse(localStorage.getItem(clave(idUsuario)) || '[]');
    return Array.isArray(lista) ? lista : [];
  } catch {
    return [];
  }
}
function guardarChats(idUsuario, lista) {
  try {
    localStorage.setItem(clave(idUsuario), JSON.stringify(lista.slice(0, MAX_CHATS)));
  } catch { /* sin almacenamiento: el chat sigue funcionando, solo no se recuerda */ }
}

// ---------- íconos de la barra lateral ----------
function LogoTechLink() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M3 3h8l-3 9H3z" fill="currentColor" />
      <path d="M13 3h8v9h-5l-3 9H9z" fill="currentColor" />
    </svg>
  );
}

const ICONOS = {
  panel: <><rect x="3" y="4" width="18" height="16" rx="3" /><path d="M9 4v16" /></>,
  nuevo: <><path d="M12 20h9" /><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z" /></>,
  lupa: <><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></>,
  pin: <><path d="M12 17v5" /><path d="M9 3h6l-1 6 3 3v2H7v-2l3-3z" /></>,
  lapiz: <><path d="M12 20h9" /><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z" /></>,
  basura: <><path d="M3 6h18" /><path d="M8 6V4h8v2" /><path d="M6 6l1 14h10l1-14" /></>
};

function Icono({ nombre, tam = 19 }) {
  return (
    <svg width={tam} height={tam} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {ICONOS[nombre]}
    </svg>
  );
}

// Botón de solo ícono: su nombre aparece en un globo al pasar el mouse.
function BotonRiel({ etiqueta, onClick, clase = '', children }) {
  return (
    <span className="ac-riel-item">
      <button type="button" className={`ac-riel-boton ${clase}`} onClick={onClick} aria-label={etiqueta}>
        {children}
      </button>
      <span className="ac-tip" role="tooltip">{etiqueta}</span>
    </span>
  );
}

// Posición del botón flotante (el usuario puede arrastrarlo). null = posición por defecto.
const CLAVE_POSICION = 'techlink_chat_posicion';
const TAM_BOTON = 72;
function limitar(x, y) {
  const m = 12;
  return {
    x: Math.min(Math.max(m, x), window.innerWidth - TAM_BOTON - m),
    y: Math.min(Math.max(m, y), window.innerHeight - TAM_BOTON - m)
  };
}
// Tamaño del chat pequeño (igual que en el CSS) y cómo mantenerlo dentro de la pantalla.
function tamanoCompacto() {
  return { w: Math.min(400, window.innerWidth - 32), h: Math.min(620, window.innerHeight - 48) };
}
function limitarVentana(x, y, t) {
  const m = 8;
  return {
    x: Math.min(Math.max(m, x), window.innerWidth - t.w - m),
    y: Math.min(Math.max(m, y), window.innerHeight - t.h - m)
  };
}
function cargarPosicion() {
  try {
    const p = JSON.parse(localStorage.getItem(CLAVE_POSICION) || 'null');
    return p && Number.isFinite(p.x) && Number.isFinite(p.y) ? limitar(p.x, p.y) : null;
  } catch {
    return null;
  }
}
function guardarPosicion(p) {
  try { localStorage.setItem(CLAVE_POSICION, JSON.stringify(p)); } catch { /* sin almacenamiento */ }
}

function grupoDe(fecha) {
  const hoy = new Date(); hoy.setHours(0, 0, 0, 0);
  const dia = 24 * 60 * 60 * 1000;
  if (fecha >= hoy.getTime()) return 'Hoy';
  if (fecha >= hoy.getTime() - dia) return 'Ayer';
  return 'Anteriores';
}

// Lo que el agente dice en voz alta: su respuesta y, si ya encontró mentores, sus nombres con el porcentaje.
function textoHablado(data) {
  const mentores = data.recomendacion?.mentores ?? [];
  if (mentores.length === 0) return data.respuesta;
  const lista = mentores.map((m) => `${m.nombres} ${m.apellidos}, con ${m.porcentaje} por ciento de coincidencia`).join('; ');
  return `${data.respuesta} Te recomiendo a: ${lista}.`;
}

function ListaMentores({ recomendacion, conIa }) {
  if (!recomendacion?.mentores?.length) return null;
  return (
    <>
      <ol className="ac-mentores">
        {recomendacion.mentores.map((mt) => (
          <li key={mt.idMentor} className="ac-mentor">
            <div className="ac-mentor-cab">
              <strong>{mt.nombres} {mt.apellidos}</strong>
              <span className="ac-pct">{mt.porcentaje}%</span>
            </div>
            <p className="ac-mentor-esp">{mt.especialidad}</p>
            <p className="ac-mentor-razon">{mt.razon}</p>
            <p className="ac-mentor-horas">
              {mt.horariosEnComun.map((h) => `${DIAS[h.diaSemana - 1]} ${recortar(h.horaInicio)}–${recortar(h.horaFin)}`).join(' · ')}
            </p>
          </li>
        ))}
      </ol>
      <p className="ac-origen">{conIa && recomendacion.generadoConIa ? 'Recomendado por la IA' : 'Cálculo base (sin IA)'}</p>
    </>
  );
}

const ESTADOS_VOZ = {
  escuchando: { cara: 'escuchando', texto: 'Te escucho…' },
  pensando: { cara: 'pensando', texto: 'Pensando…' },
  hablando: { cara: 'feliz', texto: 'Hablando…' },
  inactivo: { cara: 'feliz', texto: 'Toca el micrófono para hablar' },
  error: { cara: 'duda', texto: '' }
};

// HU-04: asistente de chat grande (historial de conversaciones, escritura y voz).
// El estudiante conversa con sus palabras; el agente entiende cursos y horario y, cuando los tiene, muestra el top 3.
export default function AsistenteChat() {
  const { authFetch, usuario } = useAuth();
  const idUsuario = usuario?.id ?? usuario?.correoInstitucional ?? 'anonimo';
  const primerNombre = (usuario?.nombres || '').split(' ')[0];

  const [abierto, setAbierto] = useState(false);
  const [chats, setChats] = useState(() => cargarChats(idUsuario));
  const [activaId, setActivaId] = useState(null);
  const [busqueda, setBusqueda] = useState('');
  const [listaAbierta, setListaAbierta] = useState(false);
  const [texto, setTexto] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [dictando, setDictando] = useState(false);
  const [voz, setVoz] = useState(null); // null = modo voz apagado
  const [porBorrar, setPorBorrar] = useState(null); // id de la conversación que se pide confirmar
  const [menuChat, setMenuChat] = useState(null); // id del chat con el menú ⋯ abierto
  const [editando, setEditando] = useState(null); // { id, texto } mientras se renombra un chat
  const [posicion, setPosicion] = useState(cargarPosicion); // dónde dejó el usuario el botón flotante
  const [grande, setGrande] = useState(() => {
    try { return localStorage.getItem(CLAVE_TAMANO) === 'grande'; } catch { return false; }
  });

  const activaRef = useRef(null);
  const vozActiva = useRef(false);
  const cancelarEscucha = useRef(() => {});
  const cancelarDictado = useRef(() => {});
  const fraseFinal = useRef('');
  const confianzaFrase = useRef(1);
  const errorVoz = useRef(null);
  const intentosSinHabla = useRef(0);
  const finRef = useRef(null);
  const finVozRef = useRef(null);
  const campoRef = useRef(null);
  const buscarRef = useRef(null);
  const arrastre = useRef(null); // { dx, dy, x0, y0, movido } mientras se arrastra el botón
  const seArrastro = useRef(false);
  const ventanaRef = useRef(null);
  const arrastreVentana = useRef(null);
  const [ventanaPos, setVentanaPos] = useState(null); // posición del chat pequeño si el usuario lo movió

  useEffect(() => { activaRef.current = activaId; }, [activaId]);
  useEffect(() => { guardarChats(idUsuario, chats); }, [chats, idUsuario]);

  const activa = chats.find((c) => c.id === activaId) ?? null;
  const mensajes = activa?.mensajes ?? [];

  useEffect(() => {
    finRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [mensajes.length, enviando, activaId, abierto]);

  useEffect(() => {
    finVozRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [voz?.parcial, voz?.estado, mensajes.length]);

  useEffect(() => {
    if (abierto && !voz) campoRef.current?.focus();
  }, [abierto, activaId, voz]);

  const terminarVoz = useCallback(() => {
    vozActiva.current = false;
    cancelarEscucha.current();
    callar();
    setVoz(null);
  }, []);

  const cerrar = useCallback(() => {
    terminarVoz();
    cancelarDictado.current();
    setDictando(false);
    // El botón flotante reaparece donde estaba el chat (esquina inferior derecha de la ventana).
    const v = ventanaRef.current;
    if (v && v.classList.contains('compacto') && window.innerWidth > 820) {
      const r = v.getBoundingClientRect();
      const p = limitar(r.right - TAM_BOTON, r.bottom - TAM_BOTON);
      setPosicion(p);
      guardarPosicion(p);
    }
    setAbierto(false);
  }, [terminarVoz]);

  useEffect(() => {
    if (!abierto) return undefined;
    const escape = (e) => {
      if (e.key !== 'Escape') return;
      if (porBorrar) setPorBorrar(null);
      else if (menuChat) setMenuChat(null);
      else cerrar();
    };
    document.addEventListener('keydown', escape);
    return () => document.removeEventListener('keydown', escape);
  }, [abierto, cerrar, porBorrar, menuChat]);

  // El menú ⋯ de un chat se cierra al hacer clic fuera de él.
  useEffect(() => {
    if (!menuChat) return undefined;
    const fuera = (e) => { if (!e.target.closest?.('.ac-item-menu, .ac-item-mas')) setMenuChat(null); };
    document.addEventListener('mousedown', fuera);
    return () => document.removeEventListener('mousedown', fuera);
  }, [menuChat]);

  // Si la ventana se achica, el botón flotante vuelve a quedar dentro de la pantalla.
  useEffect(() => {
    if (!posicion) return undefined;
    const ajustar = () => setPosicion((p) => (p ? limitar(p.x, p.y) : p));
    window.addEventListener('resize', ajustar);
    return () => window.removeEventListener('resize', ajustar);
  }, [posicion]);

  useEffect(() => () => { callar(); }, []);

  // ---------- conversaciones ----------
  const agregarMensaje = (id, mensaje) => {
    setChats((lista) => lista.map((c) => (c.id === id
      ? { ...c, mensajes: [...c.mensajes, mensaje], actualizado: Date.now() }
      : c)));
  };

  const nuevoChat = () => {
    terminarVoz();
    setActivaId(null);
    setTexto('');
    if (!grande || window.innerWidth <= 820) setListaAbierta(false);
  };

  const abrirChat = (id) => {
    terminarVoz();
    setActivaId(id);
    // En el panel pequeño (o en celular) la lista tapa el chat, así que se cierra; en grande queda abierta.
    if (!grande || window.innerWidth <= 820) setListaAbierta(false);
  };

  // Pide confirmar dentro de la propia página (no con el cuadro del navegador).
  const borrarChat = (id) => { setMenuChat(null); setPorBorrar(id); };

  const alternarFijado = (id) => {
    setMenuChat(null);
    setChats((lista) => lista.map((c) => (c.id === id ? { ...c, fijado: !c.fijado } : c)));
  };

  // Desde la barra angosta: abre la lista y pone el cursor en la búsqueda.
  const abrirBusqueda = () => {
    setListaAbierta(true);
    setTimeout(() => buscarRef.current?.focus(), 50);
  };

  const empezarRenombre = (c) => { setMenuChat(null); setEditando({ id: c.id, texto: c.titulo }); };
  const guardarRenombre = () => {
    if (!editando) return;
    const titulo = editando.texto.trim();
    if (titulo) setChats((lista) => lista.map((c) => (c.id === editando.id ? { ...c, titulo: titulo.slice(0, 60) } : c)));
    setEditando(null);
  };

  // ---------- botón flotante arrastrable ----------
  const alPresionarBoton = (e) => {
    if (e.button !== 0) return;
    const r = e.currentTarget.getBoundingClientRect();
    arrastre.current = { dx: e.clientX - r.left, dy: e.clientY - r.top, x0: e.clientX, y0: e.clientY, movido: false };
    e.currentTarget.setPointerCapture?.(e.pointerId);
  };
  const alMoverBoton = (e) => {
    const a = arrastre.current;
    if (!a) return;
    if (!a.movido && Math.hypot(e.clientX - a.x0, e.clientY - a.y0) < 5) return;
    a.movido = true;
    a.ultima = limitar(e.clientX - a.dx, e.clientY - a.dy);
    setPosicion(a.ultima);
  };
  const alSoltarBoton = () => {
    const a = arrastre.current;
    arrastre.current = null;
    if (a?.movido) {
      seArrastro.current = true;
      guardarPosicion(a.ultima);
    }
  };
  const alClicBoton = () => {
    if (seArrastro.current) { seArrastro.current = false; return; }
    // El chat se abre junto al botón: su esquina inferior derecha coincide con la del botón.
    if (posicion) {
      const t = tamanoCompacto();
      setVentanaPos(limitarVentana(posicion.x + TAM_BOTON - t.w, posicion.y + TAM_BOTON - t.h, t));
    } else {
      setVentanaPos(null);
    }
    setAbierto(true);
  };

  // ---------- mover el chat abierto arrastrando su barra superior ----------
  const alPresionarCab = (e) => {
    if (e.button !== 0 || e.target.closest('button') || !ventanaRef.current?.classList.contains('compacto') || window.innerWidth <= 820) return;
    const r = ventanaRef.current.getBoundingClientRect();
    arrastreVentana.current = { dx: e.clientX - r.left, dy: e.clientY - r.top, t: { w: r.width, h: r.height } };
    e.currentTarget.setPointerCapture?.(e.pointerId);
  };
  const alMoverCab = (e) => {
    const a = arrastreVentana.current;
    if (!a) return;
    setVentanaPos(limitarVentana(e.clientX - a.dx, e.clientY - a.dy, a.t));
  };
  const alSoltarCab = () => { arrastreVentana.current = null; };

  const confirmarBorrado = () => {
    const id = porBorrar;
    setPorBorrar(null);
    terminarVoz();
    setChats((lista) => lista.filter((c) => c.id !== id));
    if (activaId === id) setActivaId(null);
  };

  const alternarTamano = () => {
    setGrande((g) => {
      try { localStorage.setItem(CLAVE_TAMANO, g ? 'chico' : 'grande'); } catch { /* sin almacenamiento */ }
      return !g;
    });
  };

  // Manda un mensaje al agente y guarda la respuesta. Devuelve { ok, respuesta, recomendacion, generadoConIa }.
  async function enviarTexto(contenido) {
    const limpio = contenido.trim();
    if (!limpio) return { ok: false };

    let id = activaRef.current;
    const previos = chats.find((c) => c.id === id)?.mensajes ?? [];
    const nuevos = [...previos, { rol: 'usuario', texto: limpio }];
    if (id) {
      agregarMensaje(id, { rol: 'usuario', texto: limpio });
    } else {
      id = nuevoId();
      activaRef.current = id;
      setActivaId(id);
      setChats((lista) => [{
        id, titulo: limpio.length > 40 ? `${limpio.slice(0, 40)}…` : limpio,
        actualizado: Date.now(), mensajes: nuevos
      }, ...lista]);
    }

    setEnviando(true);
    try {
      const response = await authFetch('/api/agente/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mensajes: nuevos.slice(-MAX_MENSAJES).map((m) => ({ rol: m.rol, texto: m.texto })) })
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        const detalle = data.campos ? Object.values(data.campos)[0] : data.error;
        const msg = detalle || 'No pude responder ahora. Intenta de nuevo.';
        agregarMensaje(id, { rol: 'agente', texto: msg, error: true });
        return { ok: false, respuesta: msg };
      }
      agregarMensaje(id, {
        rol: 'agente', texto: data.respuesta, conIa: data.generadoConIa, recomendacion: data.recomendacion
      });
      return { ok: true, respuesta: data.respuesta, recomendacion: data.recomendacion, generadoConIa: data.generadoConIa };
    } catch {
      const msg = 'No pude conectar con el servidor.';
      agregarMensaje(id, { rol: 'agente', texto: msg, error: true });
      return { ok: false, respuesta: msg };
    } finally {
      setEnviando(false);
    }
  }

  const alEnviar = (e) => {
    e.preventDefault();
    if (enviando || !texto.trim()) return;
    const t = texto;
    setTexto('');
    enviarTexto(t);
  };

  // ---------- dictado (micrófono pequeño): habla y se escribe en el cuadro ----------
  const alternarDictado = () => {
    if (dictando) {
      cancelarDictado.current();
      setDictando(false);
      return;
    }
    if (!hayReconocimiento) {
      setVoz({ estado: 'error', mensaje: 'Tu navegador no permite voz. Usa Chrome o Edge.' });
      return;
    }
    setDictando(true);
    cancelarDictado.current = escucharFrase({
      alParcial: (t) => setTexto(t),
      alFinal: (t) => setTexto(t),
      alError: () => setDictando(false),
      alTerminar: () => setDictando(false)
    });
  };

  // ---------- modo voz (botón de ondas): conversación hablada ----------
  function empezarEscucha(nota) {
    if (!vozActiva.current) return;
    fraseFinal.current = '';
    confianzaFrase.current = 1;
    errorVoz.current = null;
    setVoz({ estado: 'escuchando', parcial: '', nota });
    cancelarEscucha.current = escucharFrase({
      alParcial: (t) => setVoz((v) => (v && v.estado === 'escuchando' ? { ...v, parcial: t } : v)),
      alFinal: (t, c) => { fraseFinal.current = t; confianzaFrase.current = c; },
      alError: (c) => { errorVoz.current = c; },
      alTerminar: () => {
        if (!vozActiva.current) return;
        const frase = fraseFinal.current;
        const error = errorVoz.current;
        if (frase && utilizable(frase, confianzaFrase.current)) {
          intentosSinHabla.current = 0;
          procesarVoz(frase);
        } else if (['not-allowed', 'service-not-allowed', 'no-soportado', 'audio-capture'].includes(error)) {
          vozActiva.current = false;
          setVoz({ estado: 'error', mensaje: 'No pude usar el micrófono. Permite el acceso al micrófono en tu navegador.' });
        } else {
          intentosSinHabla.current += 1;
          // No se entendió nada útil: no se le manda nada al agente ni habla; solo sigue escuchando en silencio.
          if (intentosSinHabla.current < 4) setTimeout(() => empezarEscucha(frase ? 'No te escuché bien…' : undefined), 250);
          else setVoz({ estado: 'inactivo' });
        }
      }
    });
  }

  async function procesarVoz(frase) {
    setVoz({ estado: 'pensando', parcial: frase });
    const data = await enviarTexto(frase);
    if (!vozActiva.current) return;
    const hayMentores = (data.recomendacion?.mentores?.length ?? 0) > 0;
    setVoz({
      estado: 'hablando',
      parcial: '',
      cara: data.ok ? (hayMentores ? 'confirmacion' : 'feliz') : 'duda',
      respuesta: data.respuesta,
      recomendacion: data.recomendacion,
      conIa: data.generadoConIa
    });
    await hablar(data.ok ? textoHablado(data) : data.respuesta);
    if (vozActiva.current) empezarEscucha();
  }

  const iniciarVoz = () => {
    cancelarDictado.current();
    setDictando(false);
    if (!hayReconocimiento) {
      setVoz({ estado: 'error', mensaje: 'Tu navegador no permite voz. Usa Chrome o Edge.' });
      return;
    }
    vozActiva.current = true;
    intentosSinHabla.current = 0;
    empezarEscucha();
  };

  // Pausa/reanuda la escucha, o interrumpe al agente si está hablando.
  const alternarMicrofono = () => {
    if (!voz) return;
    if (voz.estado === 'escuchando') {
      cancelarEscucha.current();
      setVoz({ estado: 'inactivo' });
    } else if (voz.estado === 'hablando') {
      callar(); // al callar, el ciclo vuelve a escuchar solo
    } else if (voz.estado === 'inactivo') {
      vozActiva.current = true;
      intentosSinHabla.current = 0;
      empezarEscucha();
    }
  };

  // ---------- lista de conversaciones ----------
  const filtrados = useMemo(() => {
    const q = busqueda.trim().toLowerCase();
    return chats.filter((c) => !q || c.titulo.toLowerCase().includes(q));
  }, [chats, busqueda]);

  const fijados = useMemo(() => filtrados.filter((c) => c.fijado), [filtrados]);

  const grupos = useMemo(() => {
    const mapa = new Map();
    filtrados.filter((c) => !c.fijado).forEach((c) => {
      const g = grupoDe(c.actualizado);
      if (!mapa.has(g)) mapa.set(g, []);
      mapa.get(g).push(c);
    });
    return ['Hoy', 'Ayer', 'Anteriores'].filter((g) => mapa.has(g)).map((g) => [g, mapa.get(g)]);
  }, [filtrados]);

  // Un chat de la lista: título (o campo para renombrarlo) y su menú ⋯.
  const itemChat = (c) => (
    <div key={c.id} className={`ac-item${c.id === activaId ? ' activo' : ''}${menuChat === c.id ? ' con-menu' : ''}`}>
      {editando?.id === c.id ? (
        <input
          className="ac-item-editar"
          value={editando.texto}
          maxLength={60}
          autoFocus
          aria-label="Nuevo nombre del chat"
          onChange={(e) => setEditando({ id: c.id, texto: e.target.value })}
          onBlur={guardarRenombre}
          onKeyDown={(e) => {
            if (e.key === 'Enter') guardarRenombre();
            if (e.key === 'Escape') { e.stopPropagation(); setEditando(null); }
          }}
        />
      ) : (
        <button type="button" className="ac-item-abrir" onClick={() => abrirChat(c.id)} title={c.titulo}>
          {c.titulo}
        </button>
      )}
      <button
        type="button"
        className="ac-item-mas"
        onClick={() => setMenuChat((m) => (m === c.id ? null : c.id))}
        aria-label={`Opciones de ${c.titulo}`}
        aria-haspopup="menu"
        aria-expanded={menuChat === c.id}
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><circle cx="5" cy="12" r="1.8" /><circle cx="12" cy="12" r="1.8" /><circle cx="19" cy="12" r="1.8" /></svg>
      </button>
      {menuChat === c.id && (
        <div className="ac-item-menu" role="menu">
          <button type="button" role="menuitem" onClick={() => alternarFijado(c.id)}>
            <Icono nombre="pin" tam={15} /> {c.fijado ? 'Quitar de fijados' : 'Fijar chat'}
          </button>
          <button type="button" role="menuitem" onClick={() => empezarRenombre(c)}>
            <Icono nombre="lapiz" tam={15} /> Editar nombre
          </button>
          <button type="button" role="menuitem" className="peligro" onClick={() => borrarChat(c.id)}>
            <Icono nombre="basura" tam={15} /> Borrar chat
          </button>
        </div>
      )}
    </div>
  );

  const estadoVoz = voz ? ESTADOS_VOZ[voz.estado] : null;
  const caraVoz = voz ? (voz.cara || estadoVoz.cara) : 'feliz';

  return (
    <>
      {abierto && (
        <div
          className={`ac-velo${grande ? '' : ' compacto'}${!grande && ventanaPos ? ' movido' : ''}`}
          style={!grande && ventanaPos ? { left: ventanaPos.x, top: ventanaPos.y } : undefined}
        >
          <section ref={ventanaRef} className={`ac-ventana${grande ? '' : ' compacto'}${listaAbierta ? ' lista-abierta' : ''}`} role="dialog" aria-label="Asistente LlamaLink">
            <aside className={`ac-lateral${listaAbierta ? ' abierta' : ''}`} aria-label="Conversaciones">
              {/* Barra angosta (lista cerrada): solo íconos; el nombre aparece al pasar el mouse */}
              <div className="ac-riel">
                <BotonRiel etiqueta="Mostrar barra lateral" clase="ac-riel-logo" onClick={() => setListaAbierta(true)}>
                  <span className="ac-logo-normal"><LogoTechLink /></span>
                  <span className="ac-logo-hover"><Icono nombre="panel" /></span>
                </BotonRiel>
                <BotonRiel etiqueta="Nuevo chat" onClick={nuevoChat}><Icono nombre="nuevo" /></BotonRiel>
                <BotonRiel etiqueta="Buscar chats" onClick={abrirBusqueda}><Icono nombre="lupa" /></BotonRiel>
                <BotonRiel etiqueta="Chats fijados" onClick={() => setListaAbierta(true)}><Icono nombre="pin" /></BotonRiel>
              </div>

              {/* Barra completa (lista abierta): nuevo chat, búsqueda, fijados e historial */}
              <div className="ac-panel">
                <div className="ac-panel-cab">
                  <span className="ac-panel-logo"><LogoTechLink /></span>
                  <span className="ac-panel-nombre">LlamaLink</span>
                  <BotonRiel etiqueta="Cerrar barra lateral" clase="ac-panel-cerrar" onClick={() => setListaAbierta(false)}>
                    <Icono nombre="panel" />
                  </BotonRiel>
                </div>

                <button type="button" className="ac-accion" onClick={nuevoChat}>
                  <Icono nombre="nuevo" /> Nuevo chat
                </button>
                <label className="ac-accion ac-buscar-caja">
                  <Icono nombre="lupa" />
                  <input
                    ref={buscarRef}
                    type="search"
                    placeholder="Buscar chats"
                    aria-label="Buscar chats"
                    value={busqueda}
                    onChange={(e) => setBusqueda(e.target.value)}
                  />
                </label>

                <div className="ac-lista">
                  {fijados.length > 0 && (
                    <div>
                      <p className="ac-grupo"><Icono nombre="pin" tam={12} /> Fijados</p>
                      {fijados.map(itemChat)}
                    </div>
                  )}
                  {grupos.map(([nombre, items]) => (
                    <div key={nombre}>
                      <p className="ac-grupo">{nombre}</p>
                      {items.map(itemChat)}
                    </div>
                  ))}
                  {fijados.length === 0 && grupos.length === 0 && (
                    <p className="ac-vacio">{chats.length === 0 ? 'Aquí aparecerán tus conversaciones.' : 'Nada coincide con la búsqueda.'}</p>
                  )}
                </div>
              </div>
            </aside>

            {/* Zona oscura detrás de la lista (panel pequeño o celular): tocarla cierra la lista */}
            {listaAbierta && <div className="ac-lateral-velo" onClick={() => setListaAbierta(false)} aria-hidden="true" />}

            <div className="ac-principal">
              <header
                className="ac-cab"
                onPointerDown={alPresionarCab}
                onPointerMove={alMoverCab}
                onPointerUp={alSoltarCab}
                onPointerCancel={alSoltarCab}
                title={grande ? undefined : 'Arrastra para mover el chat'}
              >
                <h2 className="ac-titulo">{activa ? activa.titulo : 'Nueva conversación'}</h2>
                {activa && (
                  <button type="button" className="ac-icono" onClick={() => borrarChat(activa.id)} aria-label="Borrar conversación">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M3 6h18" /><path d="M8 6V4h8v2" /><path d="M6 6l1 14h10l1-14" /></svg>
                  </button>
                )}
                <button type="button" className="ac-icono ac-tamano" onClick={alternarTamano} aria-label={grande ? 'Reducir chat' : 'Agrandar chat'} title={grande ? 'Reducir chat' : 'Agrandar chat'}>
                  {grande ? (
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M4 14h6v6" /><path d="M20 10h-6V4" /><path d="M14 10l7-7" /><path d="M3 21l7-7" /></svg>
                  ) : (
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M15 3h6v6" /><path d="M9 21H3v-6" /><path d="M21 3l-7 7" /><path d="M3 21l7-7" /></svg>
                  )}
                </button>
                <button type="button" className="ac-icono" onClick={cerrar} aria-label="Cerrar asistente">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" /></svg>
                </button>
              </header>

              <div className="ac-mensajes" aria-live="polite">
                {mensajes.length === 0 && !enviando && (
                  <div className="ac-bienvenida">
                    <h3>¡Hola{primerNombre ? `, ${primerNombre}` : ''}! Soy LlamaLink</h3>
                    <p>Cuéntame en qué cursos necesitas ayuda y cuándo tienes tiempo libre, y te busco al mejor mentor. Puedes escribirme o hablarme.</p>
                    <div className="ac-sugerencias">
                      {SUGERENCIAS.map((s) => (
                        <button key={s} type="button" onClick={() => enviarTexto(s)}>{s}</button>
                      ))}
                    </div>
                  </div>
                )}

                {mensajes.map((m, i) => (
                  <div key={i} className={`ac-fila ${m.rol}`}>
                    {m.rol === 'agente' && <img className="ac-fila-avatar" src={m.error ? LLAMA.duda : LLAMA.avatar} alt="" />}
                    <div className="ac-fila-cuerpo">
                      <p className={`ac-burbuja${m.error ? ' error' : ''}`}>{m.texto}</p>
                      <ListaMentores recomendacion={m.recomendacion} conIa={m.conIa} />
                    </div>
                  </div>
                ))}

                {enviando && (
                  <div className="ac-fila agente">
                    <img className="ac-fila-avatar" src={LLAMA.escribiendo} alt="" />
                    <div className="ac-fila-cuerpo">
                      <p className="ac-burbuja ac-escribiendo" aria-label="Escribiendo"><span /><span /><span /></p>
                    </div>
                  </div>
                )}
                <div ref={finRef} />
              </div>

              <form className="ac-form" onSubmit={alEnviar}>
                <input
                  ref={campoRef}
                  type="text"
                  value={texto}
                  maxLength={500}
                  placeholder={dictando ? 'Te escucho…' : 'Escribe un mensaje…'}
                  aria-label="Escribe un mensaje"
                  onChange={(e) => setTexto(e.target.value)}
                />
                <button type="button" className={`ac-mic${dictando ? ' activo' : ''}`} onClick={alternarDictado} aria-label={dictando ? 'Dejar de dictar' : 'Dictar con el micrófono'} aria-pressed={dictando}>
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><rect x="9" y="3" width="6" height="12" rx="3" /><path d="M5 11a7 7 0 0 0 14 0" /><path d="M12 18v3" /></svg>
                </button>
                {texto.trim() ? (
                  <button type="submit" className="ac-enviar" disabled={enviando} aria-label="Enviar">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M22 2 11 13" /><path d="M22 2 15 22l-4-9-9-4z" /></svg>
                  </button>
                ) : (
                  <button type="button" className="ac-enviar ac-ondas" onClick={iniciarVoz} aria-label="Conversar por voz">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><path d="M4 10v4M8 7v10M12 4v16M16 8v8M20 11v2" /></svg>
                  </button>
                )}
              </form>

              {voz && (
                <div className="ac-voz" role="region" aria-label="Conversación por voz">
                  <div className={`ac-voz-cara ${voz.estado}`}>
                    <img src={LLAMA[caraVoz]} alt="" />
                  </div>
                  <p className="ac-voz-estado" aria-live="polite">
                    {voz.estado === 'error' ? voz.mensaje : (voz.estado === 'escuchando' && voz.nota) || estadoVoz.texto}
                  </p>
                  {voz.estado !== 'error' && (
                    <div className="ac-voz-hilo">
                      {mensajes.slice(-4).map((m, i) => (
                        <div key={i} className={`ac-voz-linea ${m.rol}`}>
                          <p>{m.texto}</p>
                          <ListaMentores recomendacion={m.recomendacion} conIa={m.conIa} />
                        </div>
                      ))}
                      {voz.estado === 'escuchando' && (
                        <div className="ac-voz-linea usuario vivo">
                          {voz.parcial ? <p>{voz.parcial}</p> : <p className="ac-escribiendo" aria-label="Escuchando"><span /><span /><span /></p>}
                        </div>
                      )}
                      <div ref={finVozRef} />
                    </div>
                  )}
                  <div className="ac-voz-botones">
                    {voz.estado !== 'error' && (
                      <button type="button" className="ac-voz-mic" onClick={alternarMicrofono} disabled={voz.estado === 'pensando'}>
                        {voz.estado === 'escuchando' ? 'Pausar' : voz.estado === 'hablando' ? 'Interrumpir' : 'Hablar'}
                      </button>
                    )}
                    <button type="button" className="ac-voz-fin" onClick={terminarVoz}>Terminar</button>
                  </div>
                </div>
              )}
            </div>

            {porBorrar && (
              <div className="ac-confirmar" role="alertdialog" aria-labelledby="ac-conf-tit" aria-describedby="ac-conf-txt">
                <div className="ac-confirmar-caja">
                  <h3 id="ac-conf-tit">¿Borrar esta conversación?</h3>
                  <p id="ac-conf-txt">
                    “{chats.find((c) => c.id === porBorrar)?.titulo}” se eliminará de este navegador y no se puede recuperar.
                  </p>
                  <div className="ac-confirmar-botones">
                    <button type="button" className="ac-conf-cancelar" onClick={() => setPorBorrar(null)} autoFocus>Cancelar</button>
                    <button type="button" className="ac-conf-borrar" onClick={confirmarBorrado}>Borrar</button>
                  </div>
                </div>
              </div>
            )}
          </section>
        </div>
      )}

      {!abierto && (
        <div
          className={`ac-flotante${posicion ? ' movido' : ''}${posicion && posicion.x < 260 ? ' aviso-derecha' : ''}`}
          style={posicion ? { left: posicion.x, top: posicion.y } : undefined}
        >
          {/* El aviso solo aparece al pasar el mouse por el botón */}
          <p className="ac-aviso" aria-hidden="true">
            ¿Necesitas ayuda?<br /><strong>Pregunta a LlamaLink</strong>
          </p>
          <button
            type="button"
            className="ac-boton"
            onPointerDown={alPresionarBoton}
            onPointerMove={alMoverBoton}
            onPointerUp={alSoltarBoton}
            onPointerCancel={alSoltarBoton}
            onClick={alClicBoton}
            aria-label="Abrir asistente LlamaLink (puedes arrastrarlo)"
            title="Arrástrame para cambiarme de lugar"
          >
            <img src={LLAMA.avatar} alt="" draggable="false" />
          </button>
        </div>
      )}
    </>
  );
}
