package com.techlink.service;

import com.techlink.model.ParametrosSeguridad;
import com.techlink.repository.ParametrosSeguridadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// HU-60: entrega los parámetros de seguridad vigentes (dominio institucional, intentos máximos y minutos de bloqueo).
// Se leen de la base en cada uso, así un cambio hecho en el panel de administración rige desde el siguiente inicio de
// sesión sin reiniciar nada. Si la fila no existe, se usan los valores con los que ya funcionaba el login (HU-02).
@Service
public class ParametrosSeguridadService {

    // dominio va sin arroba y en minúsculas, por ejemplo "tecsup.edu.pe".
    public record Parametros(String dominio, int maxIntentos, int minutosBloqueo) {

        public static final Parametros PREDETERMINADOS = new Parametros("tecsup.edu.pe", 3, 15);

        // El correo debe terminar exactamente en "@dominio" (sin distinguir mayúsculas): "ana@fake-tecsup.edu.pe" no vale.
        public boolean aceptaCorreo(String correo) {
            return correo != null && correo.toLowerCase().endsWith("@" + dominio);
        }
    }

    private final ParametrosSeguridadRepository repositorio;

    public ParametrosSeguridadService(ParametrosSeguridadRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(readOnly = true)
    public Parametros obtener() {
        return repositorio.findById(ParametrosSeguridad.ID)
                .map(p -> new Parametros(p.getDominioPermitido().trim().toLowerCase(), p.getMaxIntentos(), p.getMinutosBloqueo()))
                .orElse(Parametros.PREDETERMINADOS);
    }
}
