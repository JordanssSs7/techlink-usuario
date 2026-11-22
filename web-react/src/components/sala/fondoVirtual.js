// HU-08: fondo virtual de la cámara. Separa a la persona del fondo con el modelo de segmentación de MediaPipe (corre en el
// navegador: la imagen de la cámara no sale del equipo) y dibuja a la persona sobre un fondo desenfocado o una imagen.
// El resultado es una pista de video (canvas.captureStream) que se envía a la videollamada en lugar de la cámara.
//
// El modelo y los archivos WebAssembly se bajan de internet la primera vez que se activa un fondo. Si no se pueden bajar
// (sin conexión, red restringida), se lanza un error y la cámara sigue funcionando sin fondo.

// Debe coincidir con la versión instalada de @mediapipe/tasks-vision (package.json).
const VERSION_MEDIAPIPE = '1.2.3';
const WASM = `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@${VERSION_MEDIAPIPE}/wasm`;
const MODELO = 'https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/latest/selfie_segmenter.tflite';

const ANCHO_MAXIMO = 960;
const CUADROS_POR_SEGUNDO = 24;

let segmentadorGlobal = null;

// El segmentador se crea una sola vez y se reutiliza entre llamadas y cambios de fondo.
async function obtenerSegmentador() {
  if (segmentadorGlobal) return segmentadorGlobal;
  const { FilesetResolver, ImageSegmenter } = await import('@mediapipe/tasks-vision');
  const vision = await FilesetResolver.forVisionTasks(WASM);
  const opciones = (delegado) => ({
    baseOptions: { modelAssetPath: MODELO, delegate: delegado },
    runningMode: 'VIDEO',
    outputConfidenceMasks: true,
    outputCategoryMask: false
  });
  try {
    segmentadorGlobal = await ImageSegmenter.createFromOptions(vision, opciones('GPU'));
  } catch {
    segmentadorGlobal = await ImageSegmenter.createFromOptions(vision, opciones('CPU'));
  }
  return segmentadorGlobal;
}

// Imágenes de fondo que se dibujan en el momento: no hay que bajar ni guardar archivos.
function lienzo(ancho, alto, dibujar) {
  const c = document.createElement('canvas');
  c.width = ancho;
  c.height = alto;
  dibujar(c.getContext('2d'), ancho, alto);
  return c;
}

function degradado(ctx, ancho, alto, colores) {
  const g = ctx.createLinearGradient(0, 0, ancho, alto);
  colores.forEach(([posicion, color]) => g.addColorStop(posicion, color));
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, ancho, alto);
}

export const FONDOS_PREDEFINIDOS = [
  {
    id: 'medianoche',
    nombre: 'Medianoche',
    crear: () => lienzo(1280, 720, (ctx, w, h) => degradado(ctx, w, h, [[0, '#122C4F'], [1, '#5B88B2']]))
  },
  {
    id: 'perla',
    nombre: 'Perla',
    crear: () => lienzo(1280, 720, (ctx, w, h) => degradado(ctx, w, h, [[0, '#FBF9E4'], [1, '#d9e4f0']]))
  },
  {
    id: 'oceano',
    nombre: 'Océano',
    crear: () => lienzo(1280, 720, (ctx, w, h) => {
      degradado(ctx, w, h, [[0, '#5B88B2'], [1, '#0b1f3a']]);
      ctx.fillStyle = 'rgba(255,255,255,0.06)';
      for (let i = 0; i < 6; i += 1) {
        ctx.beginPath();
        ctx.arc(w * (0.1 + i * 0.17), h * (0.25 + (i % 3) * 0.25), 90 + i * 25, 0, Math.PI * 2);
        ctx.fill();
      }
    })
  },
  {
    id: 'pizarra',
    nombre: 'Pizarra',
    crear: () => lienzo(1280, 720, (ctx, w, h) => {
      ctx.fillStyle = '#1c1f24';
      ctx.fillRect(0, 0, w, h);
      ctx.strokeStyle = 'rgba(255,255,255,0.07)';
      ctx.lineWidth = 1;
      for (let x = 0; x <= w; x += 40) {
        ctx.beginPath();
        ctx.moveTo(x, 0);
        ctx.lineTo(x, h);
        ctx.stroke();
      }
      for (let y = 0; y <= h; y += 40) {
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(w, y);
        ctx.stroke();
      }
    })
  }
];

// Dibuja "origen" cubriendo todo el lienzo sin deformarlo (recorta lo que sobre), como object-fit: cover.
function dibujarCubriendo(ctx, origen, ancho, alto) {
  const ow = origen.videoWidth || origen.naturalWidth || origen.width;
  const oh = origen.videoHeight || origen.naturalHeight || origen.height;
  if (!ow || !oh) return;
  const escala = Math.max(ancho / ow, alto / oh);
  const w = ow * escala;
  const h = oh * escala;
  ctx.drawImage(origen, (ancho - w) / 2, (alto - h) / 2, w, h);
}

export class FondoVirtual {
  constructor(pistaCamara) {
    this.pistaCamara = pistaCamara;
    this.opcion = { tipo: 'desenfoque' };
    this.video = null;
    this.lienzoSalida = null;
    this.lienzoPersona = null;
    this.lienzoMascara = null;
    this.lienzoBorroso = null;
    this.pistaSalida = null;
    this.activo = false;
    this.ultimoCuadro = 0;
    this.polaridad = null; // true si la máscara marca a la persona; se decide mirando los primeros cuadros
    this.votos = { persona: 0, fondo: 0 };
    this.indicePersona = 1;
    this.solicitud = null;
  }

  get pista() {
    return this.pistaSalida;
  }

  // Prepara todo la primera vez y deja la opción de fondo elegida. Lanza un error con un texto para el usuario si falla.
  async iniciar(opcion) {
    this.opcion = opcion;
    if (this.activo) return;
    let segmentador;
    try {
      segmentador = await obtenerSegmentador();
    } catch {
      throw new Error('No se pudo cargar el fondo virtual. Revisa tu conexión a internet e inténtalo de nuevo.');
    }
    this.segmentador = segmentador;
    // Dos máscaras (fondo y persona) o una sola: se toma la de la persona si el modelo trae etiquetas.
    try {
      const etiquetas = segmentador.getLabels?.() ?? [];
      const i = etiquetas.findIndex((e) => /person|selfie|foreground|persona/i.test(e));
      this.indicePersona = i >= 0 ? i : 1;
    } catch {
      this.indicePersona = 1;
    }

    this.video = document.createElement('video');
    this.video.muted = true;
    this.video.playsInline = true;
    this.video.srcObject = new MediaStream([this.pistaCamara]);
    await this.video.play();
    if (!this.video.videoWidth) {
      await new Promise((resolver) => this.video.addEventListener('loadedmetadata', resolver, { once: true }));
    }

    const escala = Math.min(1, ANCHO_MAXIMO / this.video.videoWidth);
    const ancho = Math.round(this.video.videoWidth * escala);
    const alto = Math.round(this.video.videoHeight * escala);
    this.lienzoSalida = Object.assign(document.createElement('canvas'), { width: ancho, height: alto });
    this.lienzoPersona = Object.assign(document.createElement('canvas'), { width: ancho, height: alto });
    this.lienzoMascara = document.createElement('canvas');
    this.lienzoBorroso = Object.assign(document.createElement('canvas'), {
      width: Math.max(16, Math.round(ancho / 14)),
      height: Math.max(9, Math.round(alto / 14))
    });
    this.ctxSalida = this.lienzoSalida.getContext('2d');
    this.ctxPersona = this.lienzoPersona.getContext('2d');
    this.ctxMascara = this.lienzoMascara.getContext('2d');
    this.ctxBorroso = this.lienzoBorroso.getContext('2d');

    this.pistaSalida = this.lienzoSalida.captureStream(CUADROS_POR_SEGUNDO).getVideoTracks()[0];
    this.activo = true;
    this.#siguienteCuadro();
  }

  detener() {
    this.activo = false;
    if (this.solicitud) cancelAnimationFrame(this.solicitud);
    if (this.pistaSalida) this.pistaSalida.stop();
    if (this.video) {
      this.video.pause();
      this.video.srcObject = null;
    }
  }

  #siguienteCuadro() {
    if (!this.activo) return;
    this.solicitud = requestAnimationFrame((t) => {
      if (t - this.ultimoCuadro >= 1000 / CUADROS_POR_SEGUNDO - 2 && this.video.readyState >= 2) {
        this.ultimoCuadro = t;
        try {
          this.#procesar();
        } catch {
          // Un cuadro que falla se salta.
        }
      }
      this.#siguienteCuadro();
    });
  }

  #procesar() {
    this.segmentador.segmentForVideo(this.video, performance.now(), (resultado) => {
      const mascaras = resultado.confidenceMasks;
      if (!mascaras || mascaras.length === 0) return;
      const mascara = mascaras[Math.min(this.indicePersona, mascaras.length - 1)];
      this.#dibujar(mascara.getAsFloat32Array(), mascara.width, mascara.height);
    });
  }

  // Decide si los valores altos de la máscara son la persona o el fondo: la persona suele estar en el centro del cuadro y
  // el fondo en los bordes. Se vota con los primeros cuadros y se queda con el resultado.
  #decidirPolaridad(datos, ancho, alto) {
    let centro = 0;
    let nCentro = 0;
    let borde = 0;
    let nBorde = 0;
    for (let y = 0; y < alto; y += 6) {
      for (let x = 0; x < ancho; x += 6) {
        const v = datos[y * ancho + x];
        const dentro = x > ancho * 0.3 && x < ancho * 0.7 && y > alto * 0.3 && y < alto * 0.8;
        const enBorde = x < ancho * 0.1 || x > ancho * 0.9 || y < alto * 0.1;
        if (dentro) {
          centro += v;
          nCentro += 1;
        } else if (enBorde) {
          borde += v;
          nBorde += 1;
        }
      }
    }
    if (nCentro && nBorde) {
      if (centro / nCentro >= borde / nBorde) this.votos.persona += 1;
      else this.votos.fondo += 1;
    }
    if (this.votos.persona + this.votos.fondo >= 12) {
      this.polaridad = this.votos.persona >= this.votos.fondo;
    }
  }

  #dibujar(datos, ancho, alto) {
    if (this.polaridad === null) this.#decidirPolaridad(datos, ancho, alto);
    const invertir = this.polaridad === null ? this.votos.fondo > this.votos.persona : !this.polaridad;

    // Máscara: la opacidad de cada punto es qué tan seguro está el modelo de que es la persona.
    if (this.lienzoMascara.width !== ancho || this.lienzoMascara.height !== alto) {
      this.lienzoMascara.width = ancho;
      this.lienzoMascara.height = alto;
      this.imagenMascara = this.ctxMascara.createImageData(ancho, alto);
    }
    const pixeles = this.imagenMascara.data;
    for (let i = 0, n = ancho * alto; i < n; i += 1) {
      const p = invertir ? 1 - datos[i] : datos[i];
      // Se endurece el borde para que no quede un halo: por debajo de 0,35 es fondo y por encima de 0,65 es persona.
      const a = p <= 0.35 ? 0 : p >= 0.65 ? 255 : ((p - 0.35) / 0.3) * 255;
      pixeles[i * 4 + 3] = a;
    }
    this.ctxMascara.putImageData(this.imagenMascara, 0, 0);

    const w = this.lienzoSalida.width;
    const h = this.lienzoSalida.height;

    // 1) El fondo.
    const ctx = this.ctxSalida;
    ctx.globalCompositeOperation = 'source-over';
    if (this.opcion.tipo === 'imagen' && this.opcion.fuente) {
      dibujarCubriendo(ctx, this.opcion.fuente, w, h);
    } else if ('filter' in ctx) {
      ctx.filter = 'blur(14px)';
      ctx.drawImage(this.video, 0, 0, w, h);
      ctx.filter = 'none';
    } else {
      // Navegadores sin filtros de lienzo (Safari): se baja y se vuelve a subir la imagen, que la difumina.
      this.ctxBorroso.drawImage(this.video, 0, 0, this.lienzoBorroso.width, this.lienzoBorroso.height);
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(this.lienzoBorroso, 0, 0, w, h);
    }

    // 2) La persona: la imagen de la cámara recortada con la máscara.
    const pctx = this.ctxPersona;
    pctx.globalCompositeOperation = 'source-over';
    pctx.clearRect(0, 0, w, h);
    pctx.drawImage(this.video, 0, 0, w, h);
    pctx.globalCompositeOperation = 'destination-in';
    if ('filter' in pctx) pctx.filter = 'blur(2px)';
    pctx.drawImage(this.lienzoMascara, 0, 0, w, h);
    if ('filter' in pctx) pctx.filter = 'none';
    pctx.globalCompositeOperation = 'source-over';

    // 3) La persona encima del fondo.
    ctx.drawImage(this.lienzoPersona, 0, 0);
  }
}
