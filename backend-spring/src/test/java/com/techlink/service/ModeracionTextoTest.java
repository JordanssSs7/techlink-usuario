package com.techlink.service;

import com.techlink.exception.ReglaNegocioException;
import com.techlink.service.ModeracionTexto.Motivo;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Pruebas de la moderación de texto del foro y del chat de la sala: se bloquean insultos, amenazas, spam y gritos, sin confundir
// palabras normales.
class ModeracionTextoTest {

    private static Motivo motivo(String texto) {
        return ModeracionTexto.revisar(texto).motivo();
    }

    @Test
    void unTextoNormalSePermite() {
        assertTrue(ModeracionTexto.revisar("¿Alguien me explica cómo funciona un JOIN en MySQL?").permitido());
        assertTrue(ModeracionTexto.revisar("Gracias, me sirvió mucho la explicación del mentor.").permitido());
        assertTrue(ModeracionTexto.revisar(null).permitido());
        assertTrue(ModeracionTexto.revisar("   ").permitido());
    }

    @Test
    void sePermitenLasPalabrasNormalesQueContienenUnaGroseria() {
        // Se comparan palabras completas: "computadora" contiene "puta" pero no es insulto.
        assertTrue(ModeracionTexto.revisar("Mi computadora no compila el proyecto de la reputación del usuario").permitido());
        assertTrue(ModeracionTexto.revisar("La disputa del merge se resolvió con un rebase").permitido());
        assertTrue(ModeracionTexto.revisar("Escribí un script para mi clase de matemática discreta").permitido());
        assertTrue(ModeracionTexto.revisar("El apellido Vergara y la concha marina no ofenden a nadie").permitido());
    }

    @Test
    void sePermitePerroOPerraComoAnimal() {
        assertTrue(ModeracionTexto.revisar("Mi perro se comió mi cuaderno de apuntes").permitido());
        assertEquals(Motivo.INSULTO, motivo("tú eres un perro"));
    }

    @Test
    void seBloqueanLosInsultosComunes() {
        assertEquals(Motivo.INSULTO, motivo("Eres un idiota"));
        assertEquals(Motivo.INSULTO, motivo("esto es una mierda"));
        assertEquals(Motivo.INSULTO, motivo("qué huevón eres"));
        assertEquals(Motivo.INSULTO, motivo("ctm"));
    }

    @Test
    void laAbreviaturaMrdaSeBloqueaPeroLaSiglaMrdDeBasesDeDatosNo() {
        assertEquals(Motivo.INSULTO, motivo("como hago mrda"));
        assertEquals(Motivo.INSULTO, motivo("MRDA"));
        assertEquals(Motivo.INSULTO, motivo("como creo una base de datos en mysql mrda"));
        assertTrue(ModeracionTexto.revisar("como creo una base de datos en mysql").permitido());
        assertTrue(ModeracionTexto.revisar("¿Cómo hago el MRD (modelo relacional de datos) de este caso?").permitido());
        assertTrue(ModeracionTexto.revisar("mrd").permitido());
    }

    @Test
    void noDistingueMayusculasNiTildes() {
        assertEquals(Motivo.INSULTO, motivo("ESTÚPIDO"));
        assertEquals(Motivo.INSULTO, motivo("Eres un Imbécil"));
        assertEquals(Motivo.INSULTO, motivo("Qué cabrón"));
    }

    @Test
    void detectaLasGroseriasDisfrazadas() {
        assertEquals(Motivo.INSULTO, motivo("qué mi3rda de tarea"));
        assertEquals(Motivo.INSULTO, motivo("eres un p.u.t.a"));
        assertEquals(Motivo.INSULTO, motivo("p u t a"));
        assertEquals(Motivo.INSULTO, motivo("PUUUUTA"));
        assertEquals(Motivo.INSULTO, motivo("qué $4$ imb3cil"));
    }

    @Test
    void detectaFrasesInsultantes() {
        assertEquals(Motivo.INSULTO, motivo("hijo de puta"));
        assertEquals(Motivo.INSULTO, motivo("Vete a la mierda con tu pregunta"));
    }

    @Test
    void detectaAmenazas() {
        assertEquals(Motivo.AMENAZA, motivo("Te voy a matar si no me ayudas"));
        assertEquals(Motivo.AMENAZA, motivo("ojalá te mueras"));
        assertEquals(Motivo.AMENAZA, motivo("sé dónde vives"));
    }

    @Test
    void detectaSpam() {
        assertEquals(Motivo.SPAM, motivo("mira http://a.com y http://b.com y http://c.com"));
        assertEquals(Motivo.SPAM, motivo("holaaaaaaaaaaaaaaaa"));
        assertTrue(ModeracionTexto.revisar("Esta guía explica el tema: https://docs.oracle.com/javase y este otro ejemplo https://www.w3schools.com/sql").permitido(),
                "uno o dos enlaces es normal");
    }

    @Test
    void detectaTextoLargoEscritoEnMayusculas() {
        assertEquals(Motivo.GRITOS, motivo("POR FAVOR RESPONDAN YA MI PREGUNTA NADIE ME AYUDA"));
        assertTrue(ModeracionTexto.revisar("URGENTE").permitido(), "una palabra corta en mayúsculas no es gritar");
    }

    @Test
    void elCodigoEnMayusculasNoCuentaComoGritar() {
        assertTrue(ModeracionTexto.revisar("SELECT NOMBRE, CORREO FROM USUARIOS WHERE ESTADO = 'ACTIVO' ORDER BY NOMBRE").permitido());
    }

    @Test
    void cadaMotivoTieneUnMensajeAmable() {
        assertTrue(Motivo.INSULTO.mensaje().contains("aprendemos juntos"));
        for (Motivo m : Motivo.values()) {
            assertFalse(m.mensaje().isBlank());
        }
    }

    @Test
    void exigirLimpioLanza422ConElMensaje() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> ModeracionTexto.exigirLimpio("un título normal", "eres un idiota"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getEstado());
        assertEquals(Motivo.INSULTO.mensaje(), error.getMessage());
    }

    @Test
    void exigirLimpioNoHaceNadaConTextosBuenos() {
        ModeracionTexto.exigirLimpio("hola", null, "gracias por la ayuda");
        assertNull(ModeracionTexto.revisar("hola").motivo());
    }
}
