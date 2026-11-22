import { useEffect, useRef } from 'react';
import { LLAMA } from './llamaImagenes';

// LlamaLink avisa con cariño cuando la moderación no deja publicar un mensaje (un insulto, una amenaza, spam...). No es un error
// del sistema: es una invitación a reformular. El texto lo trae el servidor (respuesta 422).
export default function AvisoLlama({ mensaje, onCerrar }) {
  const boton = useRef(null);

  // El foco va al botón para que se pueda cerrar con Enter y se lea el aviso; Escape también cierra.
  useEffect(() => {
    boton.current?.focus();
    const escape = (e) => { if (e.key === 'Escape') onCerrar(); };
    document.addEventListener('keydown', escape);
    return () => document.removeEventListener('keydown', escape);
  }, [onCerrar]);

  return (
    <div className="co-llama-velo" role="alertdialog" aria-modal="true" aria-label="Aviso de LlamaLink" onClick={onCerrar}>
      <div className="co-llama" onClick={(e) => e.stopPropagation()}>
        <img src={LLAMA.duda} alt="" draggable="false" />
        <div>
          <strong>LlamaLink te avisa</strong>
          <p>{mensaje}</p>
          <button ref={boton} type="button" className="co-boton primario" onClick={onCerrar}>Entendido, lo reformulo</button>
        </div>
      </div>
    </div>
  );
}
