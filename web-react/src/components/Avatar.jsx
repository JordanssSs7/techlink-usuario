import './Avatar.css';
import { useFotoUrl } from './fotoPerfil';
import { useEnLinea } from '../eventos/eventosContext';

// Círculo con la foto del usuario (HU-13) o, si no tiene, con sus iniciales.
//  - insignia: un mentor aprobado lleva alrededor de su foto el aro dorado de TechLink, y lo ve todo el público.
//  - mostrarEstado: si la persona tiene la sesión abierta ahora mismo, aparece un puntito verde (se actualiza en tiempo real).
export default function Avatar({ idUsuario, tieneFoto, version, iniciales, tamano = 44, className = '', insignia = false, mostrarEstado = false }) {
  const url = useFotoUrl(idUsuario, tieneFoto, version);
  const enLinea = useEnLinea(idUsuario);
  const circulo = (
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
  if (!insignia && !mostrarEstado) return circulo;
  return (
    <span className={`avatar-marco${insignia ? ' con-insignia' : ''}`} style={{ '--t': `${tamano}px` }}>
      {circulo}
      {mostrarEstado && enLinea && <span className="avatar-punto" role="img" aria-label="En línea" title="En línea" />}
    </span>
  );
}
