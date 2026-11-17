
package com.techlink.controller;

import com.techlink.dto.AgendarSesionRequest;
import com.techlink.dto.SesionResponse;
import com.techlink.model.Sesion;
import com.techlink.service.SesionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SesionControllerTest {

    private SesionService sesionService;
    private SesionController controller;

    private Jwt jwtEstudiante;
    private Jwt jwtMentor;
    private AgendarSesionRequest request;

    @BeforeEach
    void preparar() {
        sesionService = Mockito.mock(SesionService.class);
        controller = new SesionController(sesionService);

        jwtEstudiante = crearJwt("ESTUDIANTE");
        jwtMentor = crearJwt("MENTOR");

        request = new AgendarSesionRequest();
        request.setMentorId(2);
        request.setDisponibilidadId(3);
        request.setFecha(LocalDate.now().plusDays(7));
        request.setHoraInicio(LocalTime.of(9, 0));
        request.setHoraFin(LocalTime.of(10, 0));
        request.setEnlaceReunion("https://meet.google.com/reunion-prueba");
    }

    private Jwt crearJwt(String rol) {
        return Jwt.withTokenValue("token-prueba")
                .header("alg", "HS256")
                .subject("1")
                .claim("rol", rol)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    void registraSesionYDevuelve201() {
        Sesion sesion = new Sesion();
        SesionResponse respuesta = new SesionResponse();

        when(sesionService.agendarSesion(
                eq(1), eq(2), eq(3),
                any(LocalDate.class),
                any(LocalTime.class),
                any(LocalTime.class),
                anyString()
        )).thenReturn(sesion);

        when(sesionService.convertirARespuesta(sesion))
                .thenReturn(respuesta);

        ResponseEntity<SesionResponse> resultado =
                controller.agendarSesion(jwtEstudiante, request);

        assertEquals(HttpStatus.CREATED, resultado.getStatusCode());
        assertSame(respuesta, resultado.getBody());

        verify(sesionService).agendarSesion(
                eq(1), eq(2), eq(3),
                any(LocalDate.class),
                any(LocalTime.class),
                any(LocalTime.class),
                anyString()
        );
    }

    @Test
    void rechazaReservaDuplicadaCon409() {
        when(sesionService.agendarSesion(
                anyInt(), anyInt(), anyInt(),
                any(LocalDate.class),
                any(LocalTime.class),
                any(LocalTime.class),
                anyString()
        )).thenThrow(new IllegalArgumentException(
                "El mentor ya tiene una sesión reservada en ese horario."
        ));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.agendarSesion(jwtEstudiante, request)
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void rechazaDatosInvalidosCon400() {
        when(sesionService.agendarSesion(
                anyInt(), anyInt(), anyInt(),
                any(LocalDate.class),
                any(LocalTime.class),
                any(LocalTime.class),
                anyString()
        )).thenThrow(new IllegalArgumentException(
                "La sesión debe estar dentro del horario disponible del mentor."
        ));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.agendarSesion(jwtEstudiante, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rechazaAgendamientoDeMentorCon403() {
        ResponseEntity<SesionResponse> resultado =
                controller.agendarSesion(jwtMentor, request);

        assertEquals(HttpStatus.FORBIDDEN, resultado.getStatusCode());
        verifyNoInteractions(sesionService);
    }

    @Test
    void listaSesionesDelEstudianteCon200() {
        Sesion sesion = new Sesion();
        SesionResponse respuesta = new SesionResponse();

        when(sesionService.listarSesiones(1))
                .thenReturn(List.of(sesion));

        when(sesionService.convertirARespuesta(sesion))
                .thenReturn(respuesta);

        ResponseEntity<List<SesionResponse>> resultado =
                controller.listarSesiones(jwtEstudiante);

        assertEquals(HttpStatus.OK, resultado.getStatusCode());
        assertEquals(1, resultado.getBody().size());
        assertSame(respuesta, resultado.getBody().get(0));

        verify(sesionService).listarSesiones(1);
    }

    @Test
    void rechazaListadoDeMentorCon403() {
        ResponseEntity<List<SesionResponse>> resultado =
                controller.listarSesiones(jwtMentor);

        assertEquals(HttpStatus.FORBIDDEN, resultado.getStatusCode());
        verifyNoInteractions(sesionService);
    }
}
