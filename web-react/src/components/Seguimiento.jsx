import { useState } from 'react';
import './Seguimiento.css';
import Evolucion from './Evolucion';
import Metas from './Metas';

// Pantalla "Mi progreso" del estudiante: sus metas semanales (HU-11) y su evolución (HU-12).
export default function Seguimiento() {
  const [pestana, setPestana] = useState('metas');
  return (
    <div className="sg-contenedor">
      <div className="sg-vistas" role="tablist" aria-label="Mi progreso">
        <button type="button" role="tab" aria-selected={pestana === 'metas'} className={`sg-vista${pestana === 'metas' ? ' activo' : ''}`}
          onClick={() => setPestana('metas')}>Metas de la semana</button>
        <button type="button" role="tab" aria-selected={pestana === 'evolucion'} className={`sg-vista${pestana === 'evolucion' ? ' activo' : ''}`}
          onClick={() => setPestana('evolucion')}>Mi evolución</button>
      </div>
      {pestana === 'metas' ? <Metas /> : <Evolucion />}
    </div>
  );
}
