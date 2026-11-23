// Elige una hora en una sola lista, con AM o PM: "8:00 AM", "8:30 AM", … "11:30 PM". valor y onChange usan el formato "HH:mm" de 24 horas
// ("20:30" = 8:30 PM); valor '' significa que aún no se eligió.
const OPCIONES = Array.from({ length: 48 }, (_, i) => {
  const h = Math.floor(i / 2);
  const m = i % 2 === 0 ? '00' : '30';
  const h12 = h % 12 === 0 ? 12 : h % 12;
  return { valor: `${String(h).padStart(2, '0')}:${m}`, texto: `${h12}:${m} ${h >= 12 ? 'PM' : 'AM'}` };
});

export default function SelectorHora({ valor, onChange, etiqueta }) {
  const conocido = OPCIONES.some((o) => o.valor === valor);
  return (
    <select className="selector-hora" aria-label={etiqueta} value={valor || ''} onChange={(e) => onChange(e.target.value)}>
      <option value="" disabled>Elige la hora</option>
      {valor && !conocido && <option value={valor}>{valor}</option>}
      {OPCIONES.map((o) => <option key={o.valor} value={o.valor}>{o.texto}</option>)}
    </select>
  );
}
