// Fechas de las metas y de la evolución. Las semanas van de lunes a domingo y todo se trabaja con la fecha local del navegador.
export const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
const DIAS = ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb'];

export const aIso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
export const deIso = (iso) => { const [a, m, d] = iso.split('-').map(Number); return new Date(a, m - 1, d); };
export const sumarDias = (d, n) => { const x = new Date(d.getFullYear(), d.getMonth(), d.getDate()); x.setDate(x.getDate() + n); return x; };
export const lunesDe = (d) => sumarDias(d, -((d.getDay() + 6) % 7));
export const hoyLocal = () => { const h = new Date(); return new Date(h.getFullYear(), h.getMonth(), h.getDate()); };

// "vie 20 nov"
export function fechaCorta(iso) {
  const d = deIso(iso);
  return `${DIAS[d.getDay()]} ${d.getDate()} ${MESES[d.getMonth()]}`;
}
