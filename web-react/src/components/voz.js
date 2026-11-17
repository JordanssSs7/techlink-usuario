// Voz del asistente con las funciones del propio navegador (gratis, sin servidor):
// - SpeechRecognition convierte lo que dices en texto (Chrome y Edge; Firefox no lo trae).
// - speechSynthesis lee la respuesta en voz alta.

const Reconocimiento = typeof window !== 'undefined'
  ? window.SpeechRecognition || window.webkitSpeechRecognition
  : undefined;

export const hayReconocimiento = Boolean(Reconocimiento);
export const hayVozSintetica = typeof window !== 'undefined' && 'speechSynthesis' in window;

// Empieza a escuchar. Sigue escuchando mientras la persona habla y termina cuando hace una pausa larga (silencioMs),
// así no corta las frases a la mitad. Devuelve una función para cancelar.
// alParcial(texto): lo que se va entendiendo. alFinal(texto, confianza): la frase terminada (confianza de 0 a 1,
// 1 si el navegador no la informa). alError(codigo). alTerminar(): cerró el micrófono.
export function escucharFrase({ alParcial, alFinal, alError, alTerminar, silencioMs = 1600, maxMs = 30000 }) {
  if (!Reconocimiento) {
    alError?.('no-soportado');
    alTerminar?.();
    return () => {};
  }
  const rec = new Reconocimiento();
  rec.lang = 'es-PE';
  rec.interimResults = true;
  rec.continuous = true;
  rec.maxAlternatives = 1;
  let final = '';
  let confianza = 1;
  let huboError = false;
  let silencio = null;
  const limite = setTimeout(() => { try { rec.stop(); } catch { /* ya estaba detenido */ } }, maxMs);

  rec.onresult = (e) => {
    let fijo = '';
    let parcial = '';
    let suma = 0;
    let n = 0;
    for (let i = 0; i < e.results.length; i += 1) {
      const alt = e.results[i][0];
      if (e.results[i].isFinal) {
        fijo += alt.transcript;
        if (alt.confidence > 0) { suma += alt.confidence; n += 1; }
      } else {
        parcial += alt.transcript;
      }
    }
    final = fijo;
    confianza = n ? suma / n : 1;
    alParcial?.((fijo + parcial).trim());
    clearTimeout(silencio);
    silencio = setTimeout(() => { try { rec.stop(); } catch { /* ya estaba detenido */ } }, silencioMs);
  };
  rec.onerror = (e) => {
    huboError = true;
    alError?.(e.error);
  };
  rec.onend = () => {
    clearTimeout(silencio);
    clearTimeout(limite);
    if (!huboError && final.trim()) alFinal?.(final.trim(), confianza);
    alTerminar?.(final.trim().length > 0);
  };

  try {
    rec.start();
  } catch {
    clearTimeout(limite);
    alError?.('no-iniciado');
    alTerminar?.(false);
  }
  return () => {
    clearTimeout(silencio);
    clearTimeout(limite);
    rec.onresult = null;
    rec.onerror = null;
    rec.onend = null;
    try { rec.abort(); } catch { /* ya estaba detenido */ }
  };
}

// Quita emojis y símbolos que el lector leería mal.
export function textoParaVoz(texto) {
  return String(texto || '')
    .replace(/[\p{Extended_Pictographic}️‍]/gu, '')
    .replace(/[*_#>`]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
}

function elegirVoz() {
  const voces = window.speechSynthesis.getVoices();
  return voces.find((v) => /^es[-_]/i.test(v.lang) && /Google|Microsoft|Natural/i.test(v.name))
    || voces.find((v) => /^es[-_]/i.test(v.lang))
    || null;
}

// Lee un texto en voz alta; resuelve cuando termina (o si falla).
export function hablar(texto) {
  return new Promise((resolver) => {
    const limpio = textoParaVoz(texto);
    if (!hayVozSintetica || !limpio) {
      resolver();
      return;
    }
    window.speechSynthesis.cancel();
    const frase = new SpeechSynthesisUtterance(limpio);
    frase.lang = 'es-PE';
    frase.rate = 1.03;
    const voz = elegirVoz();
    if (voz) frase.voice = voz;
    frase.onend = () => resolver();
    frase.onerror = () => resolver();
    window.speechSynthesis.speak(frase);
  });
}

export function callar() {
  if (hayVozSintetica) window.speechSynthesis.cancel();
}
