package com.techlink.service;

import com.techlink.dto.AyudaDtos.AyudaResponse;
import com.techlink.dto.AyudaDtos.Pregunta;
import com.techlink.dto.AyudaDtos.Tema;
import com.techlink.model.AyudaPregunta;
import com.techlink.repository.AyudaPreguntaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// HU-30: centro de ayuda. Lista las preguntas frecuentes agrupadas por tema y permite buscarlas por palabra clave
// (sin importar mayúsculas ni tildes). Si no se encuentra la respuesta, ofrece el correo de soporte.
@Service
public class AyudaService {

    static final int MAX_BUSQUEDA = 100;

    private final AyudaPreguntaRepository preguntaRepository;
    private final String correoSoporte;

    public AyudaService(AyudaPreguntaRepository preguntaRepository,
                        @Value("${app.ayuda.correo-soporte:soporte@techlink.edu.pe}") String correoSoporte) {
        this.preguntaRepository = preguntaRepository;
        this.correoSoporte = correoSoporte;
    }

    // Sin búsqueda devuelve todas. Con búsqueda, solo las preguntas que contienen todas las palabras escritas.
    @Transactional(readOnly = true)
    public AyudaResponse consultar(String busqueda) {
        String texto = busqueda == null ? "" : busqueda.trim();
        if (texto.length() > MAX_BUSQUEDA) {
            texto = texto.substring(0, MAX_BUSQUEDA);
        }
        List<String> palabras = palabras(texto);

        Map<Integer, List<AyudaPregunta>> porTema = new LinkedHashMap<>();
        int total = 0;
        for (AyudaPregunta p : preguntaRepository.activas()) {
            if (coincide(p, palabras)) {
                porTema.computeIfAbsent(p.getTema().getId(), k -> new ArrayList<>()).add(p);
                total++;
            }
        }

        List<Tema> temas = porTema.values().stream()
                .map(lista -> new Tema(lista.get(0).getTema().getId(), lista.get(0).getTema().getNombre(),
                        lista.stream().map(p -> new Pregunta(p.getId(), p.getPregunta(), p.getRespuesta())).toList()))
                .toList();
        return new AyudaResponse(texto, total, temas, correoSoporte);
    }

    private static boolean coincide(AyudaPregunta p, List<String> palabras) {
        if (palabras.isEmpty()) {
            return true;
        }
        String contenido = normalizar(p.getTema().getNombre() + " " + p.getPregunta() + " " + p.getRespuesta());
        return palabras.stream().allMatch(contenido::contains);
    }

    // Palabras de la búsqueda, sin tildes y en minúsculas. Las de una sola letra se ignoran.
    static List<String> palabras(String texto) {
        List<String> palabras = new ArrayList<>();
        for (String palabra : normalizar(texto).split("[^\\p{L}\\p{N}]+")) {
            if (palabra.length() >= 2) {
                palabras.add(palabra);
            }
        }
        return palabras;
    }

    static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
}
