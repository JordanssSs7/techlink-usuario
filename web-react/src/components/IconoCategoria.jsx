// Ícono de cada categoría de curso (ver cursosCategorias.js).

const trazo = { fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round', strokeLinejoin: 'round' };

const ICONOS = {
  todos: (
    <>
      <rect x="3" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
      <rect x="14" y="14" width="7" height="7" rx="1.5" />
    </>
  ),
  programacion: (
    <>
      <path d="m8 7-5 5 5 5" />
      <path d="m16 7 5 5-5 5" />
      <path d="m13.5 4-3 16" />
    </>
  ),
  datos: (
    <>
      <ellipse cx="12" cy="5.5" rx="7.5" ry="2.8" />
      <path d="M4.5 5.5v6.5c0 1.6 3.4 2.8 7.5 2.8s7.5-1.2 7.5-2.8V5.5" />
      <path d="M4.5 12v6.5c0 1.6 3.4 2.8 7.5 2.8s7.5-1.2 7.5-2.8V12" />
    </>
  ),
  web: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M3 12h18" />
      <path d="M12 3c2.5 2.6 3.8 5.6 3.8 9s-1.3 6.4-3.8 9c-2.5-2.6-3.8-5.6-3.8-9S9.5 5.6 12 3z" />
    </>
  ),
  movil: (
    <>
      <rect x="6.5" y="2.5" width="11" height="19" rx="2.5" />
      <path d="M10.5 18.5h3" />
    </>
  ),
  ia: (
    <>
      <path d="M12 3.5 13.8 9l5.7 1.8-5.7 1.8L12 18.2l-1.8-5.6-5.7-1.8L10.2 9z" />
      <path d="M19 3v3M17.5 4.5h3" />
      <path d="M5 17.5v3M3.5 19h3" />
    </>
  ),
  software: (
    <>
      <path d="M9 3.5h6M10 3.5v5.2L4.6 18.2A1.6 1.6 0 0 0 6 20.5h12a1.6 1.6 0 0 0 1.4-2.3L14 8.7V3.5" />
      <path d="M7.2 14h9.6" />
    </>
  ),
  diseno: (
    <>
      <path d="m12 19 7-7 3 3-7 7z" />
      <path d="m18 13-1.5-7.5L2 2l3.5 14.5L13 18z" />
      <path d="m2 2 7.6 7.6" />
      <circle cx="11" cy="11" r="2" />
    </>
  ),
  sistemas: (
    <>
      <path d="M7 18.5a4.5 4.5 0 0 1-.6-9A6 6 0 0 1 18 8.6a4 4 0 0 1-.5 9.9z" />
      <path d="M12 12v4M10 14h4" />
    </>
  )
};

export default function IconoCategoria({ id, size = 20 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" {...trazo}>
      {ICONOS[id] || ICONOS.programacion}
    </svg>
  );
}
