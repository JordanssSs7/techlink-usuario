import { useFotoUrl } from './fotoPerfil';

// Círculo con la foto del usuario (HU-13) o, si no tiene, con sus iniciales.
export default function Avatar({ idUsuario, tieneFoto, version, iniciales, tamano = 44, className = '' }) {
  const url = useFotoUrl(idUsuario, tieneFoto, version);
  return (
    <span
      className={`avatar ${className}`}
      style={{
        display: 'inline-grid', placeItems: 'center', width: tamano, height: tamano, flexShrink: 0,
        borderRadius: '50%', overflow: 'hidden', background: '#0b0b0c', color: '#fff',
        fontWeight: 700, fontSize: Math.round(tamano * 0.38)
      }}
      aria-hidden="true"
    >
      {url ? <img src={url} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} /> : iniciales}
    </span>
  );
}
