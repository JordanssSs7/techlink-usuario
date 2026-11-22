package com.techlink.dto;

// HU-11: lo que se manda para crear una meta. fechaEntrega va como AAAA-MM-DD. idUsuarioEstudiante solo lo usa un mentor
// (el alumno al que le pone la meta); un estudiante siempre crea la meta para sí mismo.
public record MetaRequest(String titulo, Integer idCurso, String fechaEntrega, Integer idUsuarioEstudiante) {
}
