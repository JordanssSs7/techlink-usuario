// Indicador de carga animado (solo blanco y negro): un anillo que gira, un aro que gira al revés y un punto que late.
export default function Cargando({ texto = 'Cargando…' }) {
  return (
    <div className="cargando" role="status" aria-live="polite">
      <span className="cargando-orbita" aria-hidden="true">
        <i />
        <i />
        <i />
      </span>
      <p>{texto}</p>
    </div>
  );
}
