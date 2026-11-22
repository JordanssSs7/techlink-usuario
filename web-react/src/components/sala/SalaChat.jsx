import { useEffect, useRef, useState } from 'react';
import { LENGUAJES, nombreDeLenguaje, resaltar } from './resaltado';

const MAX_TEXTO = 1000;
const MAX_CODIGO = 4000;

const hora = (iso) => (iso ? new Date(iso).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' }) : '');

// Un fragmento de código con su lenguaje, colores y un botón para copiarlo.
function Codigo({ lenguaje, contenido }) {
  const [copiado, setCopiado] = useState(false);

  async function copiar() {
    try {
      await navigator.clipboard.writeText(contenido);
      setCopiado(true);
      setTimeout(() => setCopiado(false), 1500);
    } catch {
      // Sin permiso para el portapapeles: se puede seleccionar y copiar a mano.
    }
  }

  return (
    <div className="sa-codigo">
      <div className="sa-codigo-cab">
        <span>{nombreDeLenguaje(lenguaje)}</span>
        <button type="button" onClick={copiar}>{copiado ? 'Copiado ✓' : 'Copiar'}</button>
      </div>
      {/* El HTML sale de highlight.js, que escapa el código: no se puede colar nada ejecutable. */}
      <pre><code dangerouslySetInnerHTML={{ __html: resaltar(contenido, lenguaje) }} /></pre>
    </div>
  );
}

// HU-08: el chat de la sala. Se escriben mensajes de texto o se comparte un fragmento de código con su lenguaje, que los
// demás ven con colores y pueden copiar. Enter envía el texto (Mayús + Enter hace un salto de línea); en el modo código
// se envía con Ctrl + Enter.
export default function SalaChat({ mensajes, yoId, aviso, onTexto, onCodigo }) {
  const [modoCodigo, setModoCodigo] = useState(false);
  const [texto, setTexto] = useState('');
  const [codigo, setCodigo] = useState('');
  const [lenguaje, setLenguaje] = useState('javascript');
  const [error, setError] = useState('');
  const finRef = useRef(null);

  // Al llegar un mensaje nuevo, se baja hasta el final.
  useEffect(() => {
    finRef.current?.scrollIntoView({ block: 'end' });
  }, [mensajes.length]);

  function enviarTexto() {
    const limpio = texto.trim();
    if (!limpio) return;
    if (limpio.length > MAX_TEXTO) {
      setError(`El mensaje no puede pasar de ${MAX_TEXTO} caracteres.`);
      return;
    }
    setError('');
    onTexto(limpio);
    setTexto('');
  }

  function enviarCodigo() {
    if (!codigo.trim()) return;
    if (codigo.length > MAX_CODIGO) {
      setError(`El código no puede pasar de ${MAX_CODIGO} caracteres.`);
      return;
    }
    setError('');
    onCodigo(lenguaje, codigo);
    setCodigo('');
    setModoCodigo(false);
  }

  return (
    <div className="sa-chat">
      <ul className="sa-mensajes" aria-live="polite" aria-label="Mensajes del chat">
        {mensajes.length === 0 && <li className="sa-vacio">Todavía no hay mensajes. Escribe algo o comparte un fragmento de código.</li>}
        {mensajes.map((m) => (
          <li key={m.id} className={`sa-mensaje${m.autorId === yoId ? ' mio' : ''}`}>
            <div className="sa-mensaje-cab">
              <strong>{m.autorId === yoId ? 'Tú' : m.autor}</strong>
              <span>{hora(m.fecha)}</span>
            </div>
            {m.tipo === 'CODIGO' ? <Codigo lenguaje={m.lenguaje} contenido={m.contenido} /> : <p>{m.contenido}</p>}
          </li>
        ))}
        <li ref={finRef} aria-hidden="true" />
      </ul>

      {(error || aviso) && <p className="sa-error" role="alert">{error || aviso}</p>}

      {modoCodigo ? (
        <form className="sa-escribir" onSubmit={(e) => { e.preventDefault(); enviarCodigo(); }}>
          <div className="sa-codigo-opciones">
            <label>
              Lenguaje
              <select value={lenguaje} onChange={(e) => setLenguaje(e.target.value)}>
                {LENGUAJES.map(([valor, nombre]) => <option key={valor} value={valor}>{nombre}</option>)}
              </select>
            </label>
            <span className="sa-contador">{codigo.length}/{MAX_CODIGO}</span>
          </div>
          <textarea
            className="sa-areacodigo"
            rows={7}
            spellCheck={false}
            value={codigo}
            maxLength={MAX_CODIGO}
            placeholder="Pega o escribe tu código aquí"
            aria-label="Código para compartir"
            onChange={(e) => setCodigo(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); enviarCodigo(); } }}
          />
          <div className="sa-escribir-botones">
            <button type="button" className="sa-boton" onClick={() => setModoCodigo(false)}>Volver al texto</button>
            <button type="submit" className="sa-boton primario" disabled={!codigo.trim()}>Enviar código</button>
          </div>
        </form>
      ) : (
        <form className="sa-escribir" onSubmit={(e) => { e.preventDefault(); enviarTexto(); }}>
          <textarea
            rows={2}
            value={texto}
            maxLength={MAX_TEXTO}
            placeholder="Escribe un mensaje"
            aria-label="Mensaje"
            onChange={(e) => setTexto(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); enviarTexto(); } }}
          />
          <div className="sa-escribir-botones">
            <button type="button" className="sa-boton" onClick={() => setModoCodigo(true)}>{'</>'} Compartir código</button>
            <button type="submit" className="sa-boton primario" disabled={!texto.trim()}>Enviar</button>
          </div>
        </form>
      )}
    </div>
  );
}
