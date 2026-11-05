// Categorías de los cursos de la malla, con su ícono. El backend solo envía { id, nombre },
// así que la categoría se deduce del nombre. Las reglas se revisan en orden: gana la primera que coincide.

export const CATEGORIAS = [
  { id: 'todos', nombre: 'Todos' },
  { id: 'programacion', nombre: 'Programación' },
  { id: 'datos', nombre: 'Datos' },
  { id: 'web', nombre: 'Web' },
  { id: 'movil', nombre: 'Móvil' },
  { id: 'ia', nombre: 'IA' },
  { id: 'software', nombre: 'Software' },
  { id: 'diseno', nombre: 'Diseño' },
  { id: 'sistemas', nombre: 'Nube y sistemas' }
];

const REGLAS = [
  ['ia', /inteligencia artificial|emergentes/],
  ['movil', /m[oó]vil/],
  ['web', /web|internet/],
  ['programacion', /algoritmo/],
  ['datos', /datos/],
  ['software', /software|requerimiento|prueba|proceso/],
  ['diseno', /interfaces|dise[nñ]o/],
  ['sistemas', /nube|empresarial|integraci|arquitectura|sistema|red/],
  ['programacion', /programaci|objeto/]
];

export function categoriaDe(nombre) {
  const n = nombre.toLowerCase();
  const regla = REGLAS.find(([, re]) => re.test(n));
  return regla ? regla[0] : 'programacion';
}
