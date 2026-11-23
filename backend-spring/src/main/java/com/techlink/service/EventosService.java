package com.techlink.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Avisa en vivo a quien tenga la sesión abierta. Si se llama dentro de una transacción, el aviso sale solo cuando ya se guardó
// (así nadie recibe un aviso de algo que luego se deshace).
@Service
public class EventosService {

    private final EventosRegistro registro;

    public EventosService(EventosRegistro registro) {
        this.registro = registro;
    }

    // Una notificación nueva para esa persona: su campanita se actualiza sin recargar.
    public void notificacion(int usuarioId) {
        despues(() -> registro.enviarA(usuarioId, "{\"t\":\"notificacion\"}"));
    }

    // Esa persona cambió su foto (version) o la quitó (version null): todos los que la estén viendo la actualizan.
    public void foto(int usuarioId, String version) {
        String v = version == null ? "null" : "\"" + version.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        despues(() -> registro.difundir("{\"t\":\"foto\",\"id\":" + usuarioId + ",\"v\":" + v + "}"));
    }

    // Cambió otra imagen del perfil ("portada"): version null si la quitó.
    public void imagen(int usuarioId, String cual, String version) {
        String v = version == null ? "null" : "\"" + version + "\"";
        despues(() -> registro.difundir("{\"t\":\"" + cual + "\",\"id\":" + usuarioId + ",\"v\":" + v + "}"));
    }

    public void presencia(int usuarioId, boolean enLinea) {
        registro.difundir("{\"t\":\"presencia\",\"id\":" + usuarioId + ",\"enLinea\":" + enLinea + "}");
    }

    private static void despues(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }
}
