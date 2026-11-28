import { Children, useEffect, useId, useRef, useState } from 'react';

// Lista desplegable propia: reemplaza al <select> del navegador, que se ve distinto en cada sistema y no se puede estilar.
// Se usa como un <select>: mismas props value / onChange (con e.target.value) y las mismas <option> como hijos.
export default function Desplegable({ value, onChange, children, className = '', disabled = false, id, ...resto }) {
  const idLista = useId();
  const raiz = useRef(null);
  const lista = useRef(null);
  const [abierta, setAbierta] = useState(false);
  const [activa, setActiva] = useState(0);

  const opciones = Children.toArray(children)
    .filter((c) => c && c.type === 'option')
    .map((c) => ({ valor: String(c.props.value ?? ''), texto: c.props.children, deshabilitada: Boolean(c.props.disabled) }));
  const indiceActual = Math.max(0, opciones.findIndex((o) => o.valor === String(value ?? '')));
  const actual = opciones[indiceActual];

  useEffect(() => {
    if (!abierta) return undefined;
    const fuera = (e) => { if (raiz.current && !raiz.current.contains(e.target)) setAbierta(false); };
    document.addEventListener('mousedown', fuera);
    return () => document.removeEventListener('mousedown', fuera);
  }, [abierta]);

  useEffect(() => {
    if (abierta) lista.current?.children[activa]?.scrollIntoView({ block: 'nearest' });
  }, [abierta, activa]);

  function abrir() {
    if (disabled) return;
    setActiva(indiceActual);
    setAbierta(true);
  }
  function elegir(i) {
    const o = opciones[i];
    if (!o || o.deshabilitada) return;
    setAbierta(false);
    if (o.valor !== String(value ?? '')) onChange?.({ target: { value: o.valor } });
  }
  function mover(paso) {
    let i = activa;
    for (let n = 0; n < opciones.length; n += 1) {
      i = (i + paso + opciones.length) % opciones.length;
      if (!opciones[i].deshabilitada) break;
    }
    setActiva(i);
  }
  function teclado(e) {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      if (!abierta) abrir();
      else mover(e.key === 'ArrowDown' ? 1 : -1);
    } else if (e.key === 'Home' && abierta) { e.preventDefault(); setActiva(0); }
    else if (e.key === 'End' && abierta) { e.preventDefault(); setActiva(opciones.length - 1); }
    else if ((e.key === 'Enter' || e.key === ' ') && abierta) { e.preventDefault(); elegir(activa); }
    else if (e.key === 'Escape' && abierta) { e.preventDefault(); setAbierta(false); }
    else if (e.key === 'Tab') setAbierta(false);
  }

  return (
    <div className={`dd ${abierta ? 'abierta' : ''} ${className}`.trim()} ref={raiz}>
      <button
        type="button"
        id={id}
        className="dd-boton"
        role="combobox"
        aria-haspopup="listbox"
        aria-expanded={abierta}
        aria-controls={idLista}
        aria-label={resto['aria-label']}
        disabled={disabled}
        onClick={() => (abierta ? setAbierta(false) : abrir())}
        onKeyDown={teclado}
      >
        <span className="dd-texto">{actual?.texto}</span>
        <svg className="dd-flecha" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="m6 9 6 6 6-6" />
        </svg>
      </button>
      {abierta && (
        <ul className="dd-lista" id={idLista} role="listbox" ref={lista} aria-label={resto['aria-label']}>
          {opciones.map((o, i) => (
            <li
              key={o.valor}
              role="option"
              aria-selected={i === indiceActual}
              aria-disabled={o.deshabilitada || undefined}
              className={`dd-opcion${i === indiceActual ? ' elegida' : ''}${i === activa ? ' activa' : ''}`}
              onMouseEnter={() => setActiva(i)}
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => elegir(i)}
            >
              <span>{o.texto}</span>
              {i === indiceActual && (
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M20 6 9 17l-5-5" /></svg>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
