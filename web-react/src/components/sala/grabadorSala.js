const ANCHO = 1280;
const ALTO = 720;
const CUADROS_POR_SEGUNDO = 15;
const MS_POR_PARTE = 10000; // cada 10 segundos se manda una parte al servidor

const TIPOS = ['video/webm;codecs=vp8,opus', 'video/webm'];

// Graba la reunión desde el navegador del mentor: arma un solo video (la pantalla compartida o un mosaico con las cámaras) y mezcla el audio de
// todos, y lo va mandando al servidor por partes. Así no hace falta esperar al final para subir un archivo enorme, y si se cierra la pestaña
// queda guardado lo anterior.
//   fuentes() -> { pantalla: MediaStream|null, camaras: [{ id, nombre, stream, activa }], audios: [MediaStream] }
//   alParte(n, blob) -> promesa que sube la parte número n
//   alFallar(error) -> se llama si no se pudo subir una parte
export class GrabadorSala {
  constructor({ fuentes, alParte, alFallar }) {
    this.fuentes = fuentes;
    this.alParte = alParte;
    this.alFallar = alFallar;
    this.videos = new Map(); // id del stream -> <video> oculto desde donde se dibuja
    this.nodos = new Map(); // id del stream -> nodo de audio conectado a la mezcla
    this.cola = Promise.resolve();
    this.n = 0;
    this.fallo = false;
    this.inicio = 0;
    this.pausadoDesde = 0;
    this.msPausado = 0; // tiempo total en pausa, que no cuenta en la duración
  }

  static disponible() {
    return typeof MediaRecorder !== 'undefined' && TIPOS.some((t) => MediaRecorder.isTypeSupported(t));
  }

  iniciar() {
    this.canvas = document.createElement('canvas');
    this.canvas.width = ANCHO;
    this.canvas.height = ALTO;
    this.pincel = this.canvas.getContext('2d');

    this.audioCtx = new AudioContext();
    this.audioCtx.resume();
    this.destino = this.audioCtx.createMediaStreamDestination();

    const flujo = new MediaStream([
      ...this.canvas.captureStream(CUADROS_POR_SEGUNDO).getVideoTracks(),
      ...this.destino.stream.getAudioTracks()
    ]);
    const mimeType = TIPOS.find((t) => MediaRecorder.isTypeSupported(t));
    this.grabadora = new MediaRecorder(flujo, { mimeType, videoBitsPerSecond: 1000000, audioBitsPerSecond: 96000 });
    this.grabadora.ondataavailable = (e) => { if (e.data && e.data.size > 0) this.#encolar(e.data); };
    this.dibujar();
    this.reloj = setInterval(() => this.dibujar(), 1000 / CUADROS_POR_SEGUNDO);
    this.inicio = Date.now();
    this.grabadora.start(MS_POR_PARTE);
  }

  #encolar(blob) {
    const n = this.n;
    this.n += 1;
    this.cola = this.cola.then(() => (this.fallo ? undefined : this.alParte(n, blob))).catch((e) => {
      this.fallo = true;
      this.alFallar(e);
    });
  }

  // Pausa y reanuda: el video no avanza mientras está en pausa (no queda un hueco en la grabación).
  pausar() {
    if (this.grabadora.state !== 'recording') return;
    this.grabadora.pause();
    this.pausadoDesde = Date.now();
  }

  reanudar() {
    if (this.grabadora.state !== 'paused') return;
    this.grabadora.resume();
    this.msPausado += Date.now() - this.pausadoDesde;
    this.pausadoDesde = 0;
  }

  // Termina la grabación, espera a que se suban todas las partes y devuelve la duración en segundos.
  async detener() {
    clearInterval(this.reloj);
    const parada = new Promise((resolver) => { this.grabadora.onstop = resolver; });
    if (this.grabadora.state !== 'inactive') this.grabadora.stop();
    await parada;
    await this.cola;
    for (const v of this.videos.values()) { v.srcObject = null; }
    this.videos.clear();
    this.nodos.clear();
    if (this.audioCtx.state !== 'closed') this.audioCtx.close();
    const enPausa = this.pausadoDesde ? Date.now() - this.pausadoDesde : 0;
    return Math.max(0, Math.round((Date.now() - this.inicio - this.msPausado - enPausa) / 1000));
  }

  // ---------- Audio ----------

  #mezclarAudios(audios) {
    const vigentes = new Set();
    for (const flujo of audios) {
      if (flujo.getAudioTracks().length === 0) continue;
      vigentes.add(flujo.id);
      if (!this.nodos.has(flujo.id)) {
        const nodo = this.audioCtx.createMediaStreamSource(flujo);
        nodo.connect(this.destino);
        this.nodos.set(flujo.id, nodo);
      }
    }
    for (const [id, nodo] of this.nodos) {
      if (!vigentes.has(id)) { nodo.disconnect(); this.nodos.delete(id); }
    }
  }

  // ---------- Video ----------

  #videoDe(flujo) {
    let v = this.videos.get(flujo.id);
    if (!v) {
      v = document.createElement('video');
      v.muted = true;
      v.playsInline = true;
      v.srcObject = flujo;
      v.play().catch(() => {});
      this.videos.set(flujo.id, v);
    }
    return v;
  }

  // Dibuja un cuadro de video dentro del rectángulo (x, y, ancho, alto), entero y sin deformarse.
  #cuadro(flujo, activa, nombre, x, y, w, h) {
    const p = this.pincel;
    p.fillStyle = '#16161a';
    p.fillRect(x, y, w, h);
    const v = flujo && activa ? this.#videoDe(flujo) : null;
    if (v && v.readyState >= 2 && v.videoWidth > 0) {
      const escala = Math.min(w / v.videoWidth, h / v.videoHeight);
      const dw = v.videoWidth * escala;
      const dh = v.videoHeight * escala;
      p.drawImage(v, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh);
    } else if (nombre) {
      p.fillStyle = '#e9e9e6';
      p.font = `700 ${Math.round(Math.min(w, h) / 6)}px sans-serif`;
      p.textAlign = 'center';
      p.textBaseline = 'middle';
      p.fillText(nombre.split(' ').slice(0, 2).map((t) => t[0]).join('').toUpperCase(), x + w / 2, y + h / 2);
    }
    if (nombre) {
      p.font = '600 18px sans-serif';
      p.textAlign = 'left';
      p.textBaseline = 'alphabetic';
      p.fillStyle = 'rgba(0,0,0,0.55)';
      const ancho = Math.min(w - 12, p.measureText(nombre).width + 16);
      p.fillRect(x + 6, y + h - 32, ancho, 26);
      p.fillStyle = '#fff';
      p.fillText(nombre, x + 14, y + h - 13, w - 28);
    }
  }

  dibujar() {
    const { pantalla, camaras, audios } = this.fuentes();
    this.#mezclarAudios(audios);
    const p = this.pincel;
    p.fillStyle = '#0b0b0c';
    p.fillRect(0, 0, ANCHO, ALTO);

    if (pantalla) {
      // Pantalla compartida grande y las cámaras en una fila abajo.
      const altoFila = camaras.length > 0 ? 150 : 0;
      this.#cuadro(pantalla, true, '', 0, 0, ANCHO, ALTO - altoFila);
      const ancho = camaras.length > 0 ? Math.min(240, (ANCHO - 8 * (camaras.length + 1)) / camaras.length) : 0;
      camaras.forEach((c, i) => this.#cuadro(c.stream, c.activa, c.nombre, 8 + i * (ancho + 8), ALTO - altoFila + 4, ancho, altoFila - 8));
      return;
    }
    const total = Math.max(1, camaras.length);
    const columnas = total <= 1 ? 1 : total <= 4 ? 2 : 3;
    const filas = Math.ceil(total / columnas);
    const w = ANCHO / columnas;
    const h = ALTO / filas;
    camaras.forEach((c, i) => this.#cuadro(c.stream, c.activa, c.nombre, (i % columnas) * w + 2, Math.floor(i / columnas) * h + 2, w - 4, h - 4));
  }
}
