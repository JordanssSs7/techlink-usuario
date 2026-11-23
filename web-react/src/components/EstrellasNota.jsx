// La calificación de un mentor con estrellitas: ★★★★☆ 4,3 (12 clases). Sin calificaciones todavía, dice "Nuevo mentor".
export default function EstrellasNota({ valor, total = 0 }) {
  if (valor == null) return <span className="estrellas-nota"><small>Nuevo mentor</small></span>;
  const nota = Math.max(0, Math.min(5, Number(valor)));
  const texto = String(nota.toFixed(1)).replace('.', ',');
  return (
    <span className="estrellas-nota" role="img" aria-label={`${texto} de 5 estrellas, ${total} ${total === 1 ? 'clase calificada' : 'clases calificadas'}`}>
      <span className="estrellas" aria-hidden="true">
        ★★★★★<span style={{ width: `${(nota / 5) * 100}%` }}>★★★★★</span>
      </span>
      <strong>{texto}</strong>
      <small>({total} {total === 1 ? 'clase' : 'clases'})</small>
    </span>
  );
}
