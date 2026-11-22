import { FondoVirtual } from './fondoVirtual';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

// Códigos con los que el servidor cierra la conexión (ver SalaRegistro en el backend).
const CERRADA_POR_OTRA_PESTANA = 4001;
const SALA_LLENA = 4003;

const MENSAJES_DE_CIERRE = {
  [CERRADA_POR_OTRA_PESTANA]: 'Entraste a esta sala desde otra pestaña, así que esta se cerró.',
  [SALA_LLENA]: 'La sala está llena.'
};

// HU-08: cliente de la sala virtual. Junta tres cosas:
//  - la conexión WebSocket con el servidor (chat, código, quién entra o sale y las señales de la llamada),
//  - la videollamada directa entre navegadores (WebRTC): cámara, micrófono y pantalla compartida,
//  - el fondo virtual de la cámara (desenfoque o imagen).
// No es un componente de React: guarda su estado y avisa de cada cambio con alCambio(instantánea).
// La negociación de WebRTC sigue el patrón "perfect negotiation": como cualquiera de los dos lados puede ofrecer (por
// ejemplo, al empezar a compartir pantalla), uno de cada pareja es "cortés" y cede si las ofertas chocan.
export class SalaCliente {
  constructor({ authFetch, tipo, id, alCambio }) {
    this.authFetch = authFetch;
    this.tipo = tipo;
    this.id = id;
    this.alCambio = alCambio;

    this.destruido = false;
    this.fase = 'cargando'; // cargando | previa | conectando | dentro | cerrada | error
    this.error = '';
    this.info = null;
    this.avisoMedios = '';
    this.avisoChat = '';
    this.yo = null;
    this.sala = null;
    this.mensajes = [];
    this.participantes = new Map(); // id -> { id, nombre, rol, estado }
    this.peers = new Map(); // id -> { pc, cortes, haciendoOferta, ignorarOferta, streams, senders }
    this.ice = [];
    this.ws = null;

    this.camara = null; // pista de la cámara tal como sale del dispositivo
    this.audio = null; // pista del micrófono
    this.fondo = null; // FondoVirtual activo, si hay fondo
    this.opcionFondo = { tipo: 'ninguno' };
    this.videoSalida = null; // la pista de video que se envía (cámara o cámara con fondo)
    this.flujoLocal = null; // MediaStream con lo que se envía, para la vista previa
    this.pantalla = null; // MediaStream de la pantalla compartida
    this.camaraEncendida = true;
    this.microfonoEncendido = true;
    this.cambiandoFondo = false;

    this.alVisibilidad = () => this.#alCambiarVisibilidad();
  }

  // ---------- Estado que ve la pantalla ----------

  #emitir() {
    if (this.destruido) return;
    this.alCambio({
      fase: this.fase,
      error: this.error,
      info: this.info,
      avisoMedios: this.avisoMedios,
      avisoChat: this.avisoChat,
      yo: this.yo,
      sala: this.sala,
      mensajes: this.mensajes,
      participantes: [...this.participantes.values()].map((p) => ({
        ...p,
        streams: [...(this.peers.get(p.id)?.streams.values() ?? [])]
      })),
      flujoLocal: this.flujoLocal,
      pantallaLocal: this.pantalla,
      tieneCamara: Boolean(this.camara),
      tieneMicrofono: Boolean(this.audio),
      camaraEncendida: this.camaraEncendida,
      microfonoEncendido: this.microfonoEncendido,
      opcionFondo: this.opcionFondo,
      cambiandoFondo: this.cambiandoFondo
    });
  }

  // ---------- Entrada: datos de la sala y medios ----------

  async iniciar() {
    document.addEventListener('visibilitychange', this.alVisibilidad);
    try {
      const response = await this.authFetch(`/api/salas/${this.tipo}/${this.id}`);
      const data = await response.json().catch(() => null);
      if (!response.ok) throw new Error(data?.error || 'No se pudo abrir la sala.');
      this.info = data;
      if (this.destruido) return;
    } catch (e) {
      this.fase = 'error';
      this.error = e.message || 'No se pudo abrir la sala.';
      this.#emitir();
      return;
    }
    await this.pedirMedios();
    if (this.destruido) return;
    this.fase = 'previa';
    this.#emitir();
  }

  // Pide la cámara y el micrófono. Si falta alguno de los dos, se puede entrar igual con lo que haya (o solo con el chat).
  async pedirMedios() {
    this.avisoMedios = '';
    if (!navigator.mediaDevices?.getUserMedia) {
      this.avisoMedios = 'Tu navegador no permite usar la cámara ni el micrófono. Puedes participar por el chat.';
      return;
    }
    const intentos = [
      { video: { width: { ideal: 1280 }, height: { ideal: 720 }, frameRate: { ideal: 30 } }, audio: { echoCancellation: true, noiseSuppression: true } },
      { video: true, audio: false },
      { video: false, audio: { echoCancellation: true, noiseSuppression: true } }
    ];
    for (const restricciones of intentos) {
      try {
        const flujo = await navigator.mediaDevices.getUserMedia(restricciones);
        // Si la sala se cerró mientras el navegador pedía permiso, la cámara no debe quedar encendida.
        if (this.destruido) {
          flujo.getTracks().forEach((t) => t.stop());
          return;
        }
        this.#detenerFondo();
        this.#detenerPista(this.camara);
        this.#detenerPista(this.audio);
        this.camara = flujo.getVideoTracks()[0] ?? null;
        this.audio = flujo.getAudioTracks()[0] ?? null;
        break;
      } catch {
        // Se prueba con menos.
      }
    }
    if (!this.camara && !this.audio) {
      this.avisoMedios = 'No se pudo usar la cámara ni el micrófono. Revisa los permisos del navegador y pulsa "Reintentar", o participa por el chat.';
    } else if (!this.camara) {
      this.avisoMedios = 'No se pudo usar la cámara: participarás solo con audio.';
    } else if (!this.audio) {
      this.avisoMedios = 'No se pudo usar el micrófono: participarás solo con video.';
    }
    this.videoSalida = this.camara;
    this.camaraEncendida = Boolean(this.camara);
    this.microfonoEncendido = Boolean(this.audio);
    this.#armarFlujoLocal();
    if (this.opcionFondo.tipo !== 'ninguno' && this.camara) {
      await this.cambiarFondo(this.opcionFondo);
    }
  }

  async reintentarMedios() {
    await this.pedirMedios();
    this.#emitir();
  }

  #armarFlujoLocal() {
    const pistas = [this.videoSalida, this.audio].filter(Boolean);
    this.flujoLocal = pistas.length > 0 ? new MediaStream(pistas) : null;
  }

  #detenerPista(pista) {
    if (pista) pista.stop();
  }

  // ---------- Cámara, micrófono y fondo ----------

  alternarCamara() {
    if (!this.camara) return;
    this.camaraEncendida = !this.camaraEncendida;
    this.camara.enabled = this.camaraEncendida;
    if (this.videoSalida) this.videoSalida.enabled = this.camaraEncendida;
    this.#enviarEstado();
    this.#emitir();
  }

  alternarMicrofono() {
    if (!this.audio) return;
    this.microfonoEncendido = !this.microfonoEncendido;
    this.audio.enabled = this.microfonoEncendido;
    this.#enviarEstado();
    this.#emitir();
  }

  // opcion: { tipo: 'ninguno' } | { tipo: 'desenfoque' } | { tipo: 'imagen', id, fuente } (fuente: canvas o imagen).
  async cambiarFondo(opcion) {
    if (!this.camara || this.cambiandoFondo) return;
    this.avisoMedios = '';
    this.cambiandoFondo = true;
    this.#emitir();
    try {
      if (opcion.tipo === 'ninguno') {
        this.#detenerFondo();
        this.#usarVideoSalida(this.camara);
      } else {
        if (!this.fondo) this.fondo = new FondoVirtual(this.camara);
        const fondo = this.fondo;
        await fondo.iniciar(opcion);
        if (this.destruido || this.fondo !== fondo) {
          fondo.detener();
          return;
        }
        this.#usarVideoSalida(fondo.pista);
      }
      this.opcionFondo = opcion;
    } catch (e) {
      this.#detenerFondo();
      this.#usarVideoSalida(this.camara);
      this.opcionFondo = { tipo: 'ninguno' };
      this.avisoMedios = e.message || 'No se pudo activar el fondo virtual.';
    } finally {
      this.cambiandoFondo = false;
      this.#emitir();
    }
  }

  #detenerFondo() {
    if (this.fondo) {
      this.fondo.detener();
      this.fondo = null;
    }
  }

  // Cambia la pista de video que se envía, sin volver a negociar con nadie (replaceTrack).
  #usarVideoSalida(pista) {
    this.videoSalida = pista;
    if (pista) pista.enabled = this.camaraEncendida;
    this.#armarFlujoLocal();
    for (const peer of this.peers.values()) {
      if (peer.senders.video) peer.senders.video.replaceTrack(pista).catch(() => {});
    }
  }

  // El navegador congela el dibujo del fondo cuando la pestaña está oculta: mientras tanto se envía la cámara tal cual,
  // para que los demás no vean la imagen congelada.
  #alCambiarVisibilidad() {
    if (!this.fondo || !this.camara) return;
    const pista = document.hidden ? this.camara : this.fondo.pista;
    this.videoSalida = pista;
    pista.enabled = this.camaraEncendida;
    for (const peer of this.peers.values()) {
      if (peer.senders.video) peer.senders.video.replaceTrack(pista).catch(() => {});
    }
  }

  // ---------- Unirse a la sala ----------

  async unirse() {
    if (this.fase !== 'previa' && this.fase !== 'cerrada') return;
    this.fase = 'conectando';
    this.error = '';
    this.#emitir();
    try {
      const response = await this.authFetch(`/api/salas/${this.tipo}/${this.id}/ticket`, { method: 'POST' });
      const data = await response.json().catch(() => null);
      if (!response.ok) throw new Error(data?.error || 'No se pudo entrar a la sala.');
      this.ice = data.ice ?? [];
      if (this.destruido) return;
      await this.#conectar(data.ticket);
    } catch (e) {
      this.fase = 'previa';
      this.error = e.message || 'No se pudo entrar a la sala.';
      this.#emitir();
    }
  }

  #conectar(ticket) {
    const base = API_URL.replace(/^http/, 'ws');
    return new Promise((resolver, rechazar) => {
      const ws = new WebSocket(`${base}/ws/sala?ticket=${encodeURIComponent(ticket)}`);
      this.ws = ws;
      let abierta = false;
      ws.onopen = () => {
        abierta = true;
        resolver();
      };
      ws.onmessage = (evento) => this.#alMensaje(evento.data);
      ws.onerror = () => {
        if (!abierta) rechazar(new Error('No se pudo conectar con el servidor de la sala.'));
      };
      ws.onclose = (evento) => {
        if (!abierta) {
          rechazar(new Error('No se pudo conectar con el servidor de la sala.'));
          return;
        }
        this.#alCerrarse(evento);
      };
    });
  }

  // El servidor cerró la conexión (sala llena, otra pestaña, la sala cerró) o se perdió la red. Salir a propósito no pasa
  // por aquí: salir() quita este aviso antes de cerrar.
  #alCerrarse(evento) {
    const motivo = MENSAJES_DE_CIERRE[evento.code] || (evento.reason ? evento.reason : null);
    this.#cerrarLlamada();
    this.ws = null;
    this.fase = 'cerrada';
    this.error = motivo || 'Se perdió la conexión con la sala.';
    this.#emitir();
  }

  salir() {
    this.#cerrarLlamada();
    if (this.ws) {
      this.ws.onclose = null;
      this.ws.close(1000, 'salir');
      this.ws = null;
    }
    this.fase = 'previa';
    this.#emitir();
  }

  // Cierra las conexiones con los demás y deja de compartir pantalla (la cámara sigue encendida en la vista previa).
  #cerrarLlamada() {
    this.dejarDeCompartir(false);
    for (const peer of this.peers.values()) peer.pc.close();
    this.peers.clear();
    this.participantes.clear();
    this.yo = null;
    this.mensajes = [];
  }

  destruir() {
    this.destruido = true;
    document.removeEventListener('visibilitychange', this.alVisibilidad);
    if (this.ws) {
      this.ws.onclose = null;
      this.ws.close(1000, 'salir');
      this.ws = null;
    }
    for (const peer of this.peers.values()) peer.pc.close();
    this.peers.clear();
    this.#detenerPista(this.pantalla?.getTracks()[0]);
    this.#detenerFondo();
    this.#detenerPista(this.camara);
    this.#detenerPista(this.audio);
  }

  // ---------- Mensajes del servidor ----------

  #alMensaje(texto) {
    let m;
    try {
      m = JSON.parse(texto);
    } catch {
      return;
    }
    switch (m.t) {
      case 'bienvenida':
        this.#alBienvenida(m);
        break;
      case 'entra':
        this.participantes.set(m.participante.id, m.participante);
        // Al preparar la conexión con el que llega, ambos pueden ofrecer a la vez: el patrón de negociación lo resuelve.
        this.#crearPeer(m.participante.id);
        this.#emitir();
        break;
      case 'sale':
        this.#quitarParticipante(m.id);
        this.#emitir();
        break;
      case 'estado': {
        const p = this.participantes.get(m.id);
        if (p) this.participantes.set(m.id, { ...p, estado: m.estado });
        this.#emitir();
        break;
      }
      case 'mensaje':
        this.mensajes = [...this.mensajes, m.mensaje].slice(-300);
        this.#emitir();
        break;
      case 'senal':
        this.#alSenal(m.de, m.datos);
        break;
      case 'error':
        this.avisoChat = m.mensaje;
        this.#emitir();
        break;
      default:
    }
  }

  #alBienvenida(m) {
    this.yo = m.yo;
    this.sala = m.sala;
    this.mensajes = m.historial ?? [];
    this.participantes.clear();
    for (const p of m.participantes) this.participantes.set(p.id, p);
    this.fase = 'dentro';
    this.avisoChat = '';
    // El que llega es quien empieza la llamada con cada uno de los que ya estaban.
    for (const p of m.participantes) this.#crearPeer(p.id);
    this.#enviarEstado();
    this.#emitir();
  }

  #quitarParticipante(id) {
    this.participantes.delete(id);
    const peer = this.peers.get(id);
    if (peer) {
      peer.pc.close();
      this.peers.delete(id);
    }
  }

  #enviar(objeto) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) this.ws.send(JSON.stringify(objeto));
  }

  #enviarEstado() {
    this.#enviar({
      t: 'estado',
      camara: this.camaraEncendida && Boolean(this.camara),
      microfono: this.microfonoEncendido && Boolean(this.audio),
      pantalla: Boolean(this.pantalla),
      pantallaStream: this.pantalla?.id ?? ''
    });
  }

  // ---------- Chat ----------

  enviarTexto(contenido) {
    this.avisoChat = '';
    this.#enviar({ t: 'chat', contenido });
  }

  enviarCodigo(lenguaje, contenido) {
    this.avisoChat = '';
    this.#enviar({ t: 'codigo', lenguaje, contenido });
  }

  // ---------- Videollamada (WebRTC) ----------

  #crearPeer(id) {
    const existente = this.peers.get(id);
    if (existente) return existente;
    const pc = new RTCPeerConnection({ iceServers: this.ice });
    const peer = {
      id,
      pc,
      // De cada pareja, uno cede cuando las ofertas chocan: el de id mayor.
      cortes: (this.yo?.id ?? 0) > id,
      haciendoOferta: false,
      ignorarOferta: false,
      streams: new Map(),
      senders: { video: null, audio: null, pantalla: null }
    };
    this.peers.set(id, peer);

    pc.onicecandidate = ({ candidate }) => {
      if (candidate) this.#enviar({ t: 'senal', para: id, datos: { candidate } });
    };
    pc.onnegotiationneeded = async () => {
      try {
        peer.haciendoOferta = true;
        await pc.setLocalDescription();
        this.#enviar({ t: 'senal', para: id, datos: { description: pc.localDescription } });
      } catch {
        // Se vuelve a intentar con el siguiente cambio.
      } finally {
        peer.haciendoOferta = false;
      }
    };
    pc.ontrack = ({ track, streams }) => {
      const flujo = streams[0] ?? new MediaStream([track]);
      peer.streams.set(flujo.id, flujo);
      flujo.onremovetrack = () => {
        if (flujo.getTracks().length === 0) {
          peer.streams.delete(flujo.id);
          this.#emitir();
        }
      };
      this.#emitir();
    };
    pc.onconnectionstatechange = () => {
      if (pc.connectionState === 'failed') pc.restartIce();
    };

    // Se envía lo que se tiene (si no hay nada, solo se recibe).
    if (this.videoSalida) peer.senders.video = pc.addTrack(this.videoSalida, this.flujoLocal);
    if (this.audio) peer.senders.audio = pc.addTrack(this.audio, this.flujoLocal);
    if (this.pantalla) {
      const pista = this.pantalla.getVideoTracks()[0];
      if (pista) peer.senders.pantalla = pc.addTrack(pista, this.pantalla);
    }
    return peer;
  }

  async #alSenal(de, datos) {
    const peer = this.#crearPeer(de);
    const pc = peer.pc;
    try {
      if (datos.description) {
        const choque = datos.description.type === 'offer' && (peer.haciendoOferta || pc.signalingState !== 'stable');
        peer.ignorarOferta = !peer.cortes && choque;
        if (peer.ignorarOferta) return;
        await pc.setRemoteDescription(datos.description);
        if (datos.description.type === 'offer') {
          await pc.setLocalDescription();
          this.#enviar({ t: 'senal', para: de, datos: { description: pc.localDescription } });
        }
      } else if (datos.candidate) {
        try {
          await pc.addIceCandidate(datos.candidate);
        } catch (e) {
          if (!peer.ignorarOferta) throw e;
        }
      }
    } catch {
      // Una señal que no se pudo aplicar no debe tumbar la sala; la conexión se reintenta sola.
    }
  }

  // ---------- Pantalla compartida ----------

  async compartirPantalla() {
    if (this.pantalla || this.fase !== 'dentro') return;
    if (!navigator.mediaDevices?.getDisplayMedia) {
      this.avisoMedios = 'Tu navegador no permite compartir pantalla.';
      this.#emitir();
      return;
    }
    try {
      const flujo = await navigator.mediaDevices.getDisplayMedia({ video: { frameRate: { ideal: 15, max: 30 } }, audio: false });
      const pista = flujo.getVideoTracks()[0];
      // "detail": el navegador prioriza la nitidez del texto sobre la fluidez (importa al compartir código).
      pista.contentHint = 'detail';
      // El botón "Dejar de compartir" del navegador también debe avisar.
      pista.onended = () => this.dejarDeCompartir(true);
      this.pantalla = flujo;
      this.avisoMedios = '';
      for (const peer of this.peers.values()) {
        peer.senders.pantalla = peer.pc.addTrack(pista, flujo);
      }
      this.#enviarEstado();
    } catch (e) {
      // Cancelar el selector de pantalla no es un error.
      if (e?.name !== 'NotAllowedError' && e?.name !== 'AbortError') {
        this.avisoMedios = 'No se pudo compartir la pantalla.';
      }
    }
    this.#emitir();
  }

  dejarDeCompartir(emitir = true) {
    if (!this.pantalla) return;
    const flujo = this.pantalla;
    this.pantalla = null;
    for (const peer of this.peers.values()) {
      if (peer.senders.pantalla) {
        try {
          peer.pc.removeTrack(peer.senders.pantalla);
        } catch {
          // La conexión ya estaba cerrada.
        }
        peer.senders.pantalla = null;
      }
    }
    flujo.getTracks().forEach((t) => {
      t.onended = null;
      t.stop();
    });
    if (emitir) {
      this.#enviarEstado();
      this.#emitir();
    }
  }
}
