// Reacciones del foro: me gusta, me encanta, me divierte, me sorprende y me molesta. Tocar la que ya pusiste la quita.
const OPCIONES = [
  ['ME_GUSTA', '👍', 'Me gusta'],
  ['ME_ENCANTA', '❤️', 'Me encanta'],
  ['ME_DIVIERTE', '😂', 'Me divierte'],
  ['ME_SORPRENDE', '😮', 'Me sorprende'],
  ['ME_MOLESTA', '😡', 'Me molesta']
];

export default function Reacciones({ datos, onReaccionar }) {
  const conteos = datos?.conteos || {};
  const mia = datos?.mia || null;
  return (
    <div className="co-reacciones" role="group" aria-label="Reacciones">
      {OPCIONES.map(([id, emoji, nombre]) => (
        <button key={id} type="button" className={`co-reaccion${mia === id ? ' activa' : ''}`}
          aria-pressed={mia === id} title={nombre} aria-label={`${nombre}${conteos[id] ? `: ${conteos[id]}` : ''}`}
          onClick={() => onReaccionar(id)}>
          <span aria-hidden="true">{emoji}</span>
          {conteos[id] > 0 && <span className="co-reaccion-n">{conteos[id]}</span>}
        </button>
      ))}
    </div>
  );
}
