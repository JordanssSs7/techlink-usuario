// Muestra una hora en formato de 12 horas con AM o PM: "08:00" -> "8:00 AM", "20:30:00" -> "8:30 PM", 13 -> "1:00 PM".
export function hora12(valor) {
  const texto = typeof valor === 'number' ? `${valor}:00` : String(valor ?? '');
  const [h, m] = texto.split(':');
  const horas = Number(h);
  if (!Number.isFinite(horas)) return texto;
  const sufijo = horas % 24 >= 12 ? 'PM' : 'AM';
  const doce = horas % 12 === 0 ? 12 : horas % 12;
  return `${doce}:${(m ?? '00').slice(0, 2)} ${sufijo}`;
}
