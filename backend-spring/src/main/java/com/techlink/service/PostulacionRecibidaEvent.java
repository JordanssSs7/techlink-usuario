package com.techlink.service;

// HU-03: se recibió la postulación de un estudiante. Se usa para avisarle por correo después de guardarla.
public record PostulacionRecibidaEvent(Integer postulacionId, String correo, String nombres) {
}
