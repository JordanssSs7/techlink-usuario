import { useEffect, useState } from 'react';
import PerfilUsuario from './PerfilUsuario';

// Abre el perfil de una persona cuando un aviso lo pide (por ejemplo «Fulana empezó a seguirte»). Se coloca una vez en cada portal.
export default function PerfilDesdeAviso() {
  const [idUsuario, setIdUsuario] = useState(null);

  useEffect(() => {
    const abrir = (e) => setIdUsuario(e.detail.idUsuario);
    window.addEventListener('techlink:perfil', abrir);
    return () => window.removeEventListener('techlink:perfil', abrir);
  }, []);

  return idUsuario ? <PerfilUsuario idUsuario={idUsuario} onCerrar={() => setIdUsuario(null)} /> : null;
}
