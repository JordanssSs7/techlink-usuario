import { FondoVirtual } from './fondoVirtual';
import { GrabadorSala } from './grabadorSala';

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
  // invitado: { token, nombre } cuando quien entra es el invitado de una charla (sin cuenta): usa su enlace secreto en lugar del token.
  constructor({ authFetch, tipo, id, alCambio, invitado = null }) {
    this.invitado = invitado;
    // Los asistentes de una charla escuchan: entran con el micrófono apagado y piden la palabra levantando la mano.
    this.esAsistente = tipo === 'charla' && !invitado;
    this.mano = false;
    this.puedeHablar = false;
    this.avisoPalabra = '';
    this.avisoSala = '';
    this.authFetch = invitado
      ? (ruta, opciones = {}) => fetch(`${API_URL}${ruta}`, { ...opciones, headers: { 'Content-Type': 'application/json', ...(opciones.headers ?? {}) } })
      : authFetch;
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
    this.grabador = null; // GrabadorSala mientras el mentor graba
    this.grabando = false;
    this.grabacionPausada = false;
    this.avisoGrabacion = '';

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
      cambiandoFondo: this.cambiandoFondo,
      esInvitado: Boolean(this.invitado),
      esAsistente: this.esAsistente,
      mano: this.mano,
      puedeHablar: this.puedeHablar,
      avisoPalabra: this.avisoPalabra,
      avisoSala: this.avisoSala,
      grabando: this.grabando,
      grabacionPausada: this.grabacionPausada,
      puedeGrabar: this.tipo === 'sesion' && this.yo?.rol === 'MENTOR' && GrabadorSala.disponible(),
      avisoGrabacion: this.avisoGrabacion
    });
  }

  // ---------- Entrada: datos de la sala y medios ----------

  async iniciar() {
    document.addEventListener('visibilitychange', this.alVisibilidad);
    try {
      const response = await this.authFetch(this.invitado ? `/api/charlas/invitado/${this.invitado.token}` : `/api/salas/${this.tipo}/${this.id}`);
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
      const response = this.invitado
        ? await this.authFetch(`/api/charlas/invitado/${this.invitado.token}/ticket`, { method: 'POST', body: JSON.stringify({ nombre: this.invitado.nombre }) })
        : await this.authFetch(`/api/salas/${this.tipo}/${this.id}/ticket`, { method: 'POST' });
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
    this.#cerrarGrabacion();
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
    this.mano = false;
    this.puedeHablar = false;
    this.avisoPalabra = '';
  }

  destruir() {
    this.#cerrarGrabacion();
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
        if (m.participante.rol === 'INVITADO') this.avisoSala = '';
        // Al preparar la conexión con el que llega, ambos pueden ofrecer a la vez: el patrón de negociación lo resuelve.
        if (this.#conectaCon(m.participante)) this.#crearPeer(m.participante.id);
        this.#emitir();
        break;
      case 'sale': {
        const salio = this.participantes.get(m.id);
        if (salio?.rol === 'INVITADO' && !this.invitado) this.avisoSala = 'El invitado salió de la sala. Si vuelve, la charla continúa.';
        this.#quitarParticipante(m.id);
        this.#emitir();
        break;
      }
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
      case 'palabra':
        this.#alRecibirPalabra(m.permitir);
        break;
      case 'fin':
        this.#alTerminarCharla();
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
    for (const p of m.participantes) {
      if (this.#conectaCon(p)) this.#crearPeer(p.id);
    }
    this.#enviarEstado();
    this.#emitir();
  }

  // En una charla con mucha gente cada asistente se conecta solo con el invitado (no con los demás asistentes): así la llamada aguanta
  // decenas de personas. En sesiones de mentoría todos se conectan con todos.
  #conectaCon(p) {
    if (this.tipo !== 'charla') return true;
    return this.yo?.rol === 'INVITADO' || p.rol === 'INVITADO';
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
      pantallaStream: this.pantalla?.id ?? '',
      grabando: this.grabando,
      pausada: this.grabacionPausada,
      mano: this.mano
    });
  }

  // ---------- Charlas: levantar la mano y dar la palabra ----------

  levantarMano() {
    if (!this.esAsistente) return;
    this.mano = !this.mano;
    this.avisoPalabra = '';
    this.#enviarEstado();
    this.#emitir();
  }

  // Solo el invitado puede: da la palabra a quien levantó la mano. A esa persona le llega el aviso y se le baja la mano; cada quien decide
  // cuándo prender su micrófono y su cámara.
  darPalabra(id, permitir) {
    if (!this.invitado) return;
    this.#enviar({ t: 'palabra', para: id, permitir });
  }

  // El invitado terminó la charla: se cierra la llamada y se muestra que finalizó (la pantalla ya no queda esperando).
  #alTerminarCharla() {
    if (this.invitado) return;
    this.#cerrarLlamada();
    if (this.ws) {
      this.ws.onclose = null;
      this.ws.close(1000, 'fin');
      this.ws = null;
    }
    this.fase = 'finalizada';
    this.#emitir();
  }

  // Solo el invitado: da por terminada la charla para todos y sale.
  terminarCharla() {
    if (!this.invitado) return;
    this.#enviar({ t: 'fin' });
    this.salir();
  }

  #alRecibirPalabra(permitir) {
    if (!this.esAsistente) return;
    this.puedeHablar = Boolean(permitir);
    this.mano = false;
    this.avisoPalabra = this.puedeHablar ? 'Te dieron la palabra: ya puedes hacer tu pregunta.' : 'Terminó tu turno.';
    this.#enviarEstado();
    this.#emitir();
  }

  // ---------- Grabación (solo el mentor de la sesión) ----------

  // Lo que se ve y se oye ahora mismo en la reunión, para armar el video: pantalla compartida, cámaras de todos y el audio de todos.
  #fuentesGrabacion() {
    const camaras = [{
      id: 'yo', nombre: this.yo?.nombre ?? '', stream: this.flujoLocal, activa: this.camaraEncendida && Boolean(this.camara)
    }];
    const audios = [];
    if (this.audio) {
      this.flujoMicrofono ??= new MediaStream([this.audio]);
      audios.push(this.flujoMicrofono);
    }
    let pantalla = this.pantalla ?? null;
    for (const p of this.participantes.values()) {
      const streams = [...(this.peers.get(p.id)?.streams.values() ?? [])];
      const flujoPantalla = p.estado?.pantalla ? streams.find((f) => f.id === p.estado.pantallaStream) : null;
      const flujoCamara = streams.find((f) => f.id !== p.estado?.pantallaStream) ?? null;
      if (flujoPantalla && !pantalla) pantalla = flujoPantalla;
      camaras.push({ id: p.id, nombre: p.nombre, stream: flujoCamara, activa: Boolean(p.estado?.camara) });
      if (flujoCamara) audios.push(flujoCamara);
    }
    return { pantalla, camaras, audios };
  }

  // Sube una parte del video (con hasta 3 intentos). Si el servidor dice que no se puede, se detiene la grabación.
  async #subirParte(n, blob) {
    let ultimo = null;
    for (let intento = 0; intento < 3; intento += 1) {
      try {
        const response = await this.authFetch(`/api/sesiones/${this.id}/grabacion/parte?n=${n}`, {
          method: 'POST', headers: { 'Content-Type': 'application/octet-stream' }, body: blob
        });
        if (response.ok) return;
        const data = await response.json().catch(() => ({}));
        ultimo = new Error(data.error || 'No se pudo guardar la grabación.');
        if (response.status >= 400 && response.status < 500) break; // un error del servidor que no se arregla reintentando
      } catch (e) {
        ultimo = e;
      }
      await new Promise((r) => setTimeout(r, 1500));
    }
    throw ultimo;
  }

  iniciarGrabacion() {
    if (this.grabando || this.tipo !== 'sesion' || this.yo?.rol !== 'MENTOR' || !GrabadorSala.disponible()) return;
    this.grabador = new GrabadorSala({
      fuentes: () => this.#fuentesGrabacion(),
      alParte: (n, blob) => this.#subirParte(n, blob),
      alFallar: (e) => {
        this.avisoGrabacion = `${e.message || 'No se pudo guardar la grabación.'} La grabación se detuvo.`;
        this.detenerGrabacion();
      }
    });
    try {
      this.grabador.iniciar();
    } catch {
      this.grabador = null;
      this.avisoGrabacion = 'Tu navegador no pudo iniciar la grabación.';
      this.#emitir();
      return;
    }
    this.grabando = true;
    this.avisoGrabacion = '';
    this.#enviarEstado(); // los demás ven el aviso «Se está grabando»
    this.#emitir();
  }

  pausarGrabacion() {
    if (!this.grabador || this.grabacionPausada) return;
    this.grabador.pausar();
    this.grabacionPausada = true;
    this.#enviarEstado();
    this.#emitir();
  }

  reanudarGrabacion() {
    if (!this.grabador || !this.grabacionPausada) return;
    this.grabador.reanudar();
    this.grabacionPausada = false;
    this.#enviarEstado();
    this.#emitir();
  }

  async detenerGrabacion() {
    const grabador = this.grabador;
    if (!grabador) return;
    this.grabador = null;
    this.grabando = false;
    this.grabacionPausada = false;
    this.#enviarEstado();
    this.avisoGrabacion = this.avisoGrabacion || 'Guardando la grabación…';
    this.#emitir();
    try {
      const duracion = await grabador.detener();
      if (grabador.n > 0 && !grabador.fallo) {
        const response = await this.authFetch(`/api/sesiones/${this.id}/grabacion/terminar?duracion=${duracion}`, { method: 'POST' });
        this.avisoGrabacion = response.ok ? 'Grabación guardada. La verás en tu historial de sesiones.' : 'No se pudo terminar de guardar la grabación.';
      }
    } catch {
      this.avisoGrabacion = 'No se pudo terminar de guardar la grabación.';
    }
    this.#emitir();
  }

  // Si el mentor sale con la grabación en marcha, se termina de guardar lo grabado hasta ese momento.
  #cerrarGrabacion() {
    if (this.grabador) this.detenerGrabacion();
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
