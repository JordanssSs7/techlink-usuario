package com.techlink.dto;

import java.util.List;

// HU-13: lo que el usuario edita de su perfil. El ciclo solo aplica a estudiantes. Se valida en PerfilService para
// poder responder con un mensaje por campo.
public record PerfilRequest(String descripcion, Integer ciclo, List<String> intereses) {
}
