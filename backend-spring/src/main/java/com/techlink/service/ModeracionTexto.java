package com.techlink.service;

import com.techlink.exception.ReglaNegocioException;
import org.springframework.http.HttpStatus;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

// Moderación de lo que escriben los usuarios en el foro y en el chat de la sala: antes de publicar se revisa el texto y, si trae un
// insulto, una amenaza, spam o gritos, no se publica y el frontend muestra a LlamaLink pidiendo reformularlo con respeto.
//
// Es un filtro de reglas (rápido y sin costo), no una inteligencia artificial: detecta las groserías más comunes aunque estén
// disfrazadas ("p.u.t.a", "mi3rda", "puuuuta") sin confundirlas con palabras normales ("computadora", "reputación"), porque compara
// palabras completas. Lo que no pille se cubre con los reportes de los usuarios y la moderación del administrador (HU-29, HU-52 y
// HU-54). Un paso siguiente sería sumar una revisión con IA para el contexto (acoso sin groserías).
public final class ModeracionTexto {

    public enum Motivo {
        INSULTO("¡Hola! 💙 En TechLink aprendemos juntos. Intenta reformular tu mensaje con respeto antes de publicarlo."),
        AMENAZA("Ese mensaje suena a una amenaza. En TechLink cuidamos a toda la comunidad: reformúlalo con respeto antes de publicarlo."),
        SPAM("Tu mensaje parece spam: evita repetir letras o poner muchos enlaces."),
        GRITOS("Escribir todo en mayúsculas se lee como gritar. Prueba con minúsculas para que todos te lean con calma.");

        private final String mensaje;

        Motivo(String mensaje) {
            this.mensaje = mensaje;
        }

        public String mensaje() {
            return mensaje;
        }
    }

    // motivo es null si el texto está bien.
    public record Resultado(boolean permitido, Motivo motivo) {

        static final Resultado BIEN = new Resultado(true, null);
    }

    // Palabras sueltas (ya sin tildes y en minúsculas). Se comparan completas, nunca como parte de otra palabra.
    // Ojo: "mrd" NO está en la lista a propósito: es la sigla de "Modelo Relacional de Datos", un tema de los cursos de bases de datos. "mrda"
    // (la abreviatura de la groseria) sí se bloquea.
    private static final Set<String> INSULTOS = Set.of(
            "puta", "puto", "putas", "putos", "putita", "putazo", "putamadre", "hijoputa", "hijueputa", "hdp",
            "mierda", "mierdas", "carajo", "joder", "jodete", "jodido", "cojudo", "cojuda", "cojudos", "huevon", "huevona", "huevones",
            "conchatumadre", "conchetumare", "conchesumadre", "ctm", "csm", "ctmr", "reconchatumadre", "mrda", "mrdd",
            "pendejo", "pendeja", "pendejos", "imbecil", "imbeciles", "idiota", "idiotas", "estupido", "estupida", "estupidos",
            "malparido", "malparida", "gilipollas", "cabron", "cabrona", "cabrones", "zorra", "zorras", "maricon", "maricones", "marica",
            "mongolo", "mongola", "retrasado", "retrasada", "subnormal", "tarado", "tarada", "pajero", "pajera", "mamahuevo",
            "mamaguevo", "verga", "chucha", "chuchasumadre", "pichula", "culiao", "culiado", "weon", "weona", "boludo", "pelotudo",
            "inutil", "basuras", "asqueroso", "asquerosa", "perra", "perro");

    // Frases (con las palabras separadas por un solo espacio, sin tildes).
    private static final List<String> INSULTOS_EN_FRASE = List.of(
            "hijo de puta", "hija de puta", "vete a la mierda", "andate a la mierda", "ve a la mierda", "concha de tu madre",
            "mama huevo");

    private static final List<String> AMENAZAS = List.of(
            "te voy a matar", "te voy a pegar", "te voy a buscar", "te voy a romper", "ojala te mueras", "ojala te mueran", "matate",
            "te mato", "te vas a arrepentir", "sabemos donde vives", "se donde vives", "te voy a golpear");

    // "perro" y "perra" solo son insulto dirigido a alguien; en una pregunta normal ("mi perro") no se bloquean: se cuentan solo
    // si van acompañados de un "eres", "sos", "tu" o "ese/esa" delante.
    private static final Pattern DIRIGIDO = Pattern.compile("\\b(eres|sos|tu|ese|esa|esos|esas|que)(\\s+(un|una|el|la))?\\s+(perro|perra)\\b");

    private static final Pattern ENLACE = Pattern.compile("(https?://|www\\.)\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern LETRA_REPETIDA = Pattern.compile("(\\p{L})\\1{9,}");

    private ModeracionTexto() {
    }

    // Revisa un texto. null o vacío se considera bien (otras reglas se encargan de los campos obligatorios).
    public static Resultado revisar(String texto) {
        if (texto == null || texto.isBlank()) {
            return Resultado.BIEN;
        }
        if (esSpam(texto)) {
            return new Resultado(false, Motivo.SPAM);
        }
        if (gritaTodo(texto)) {
            return new Resultado(false, Motivo.GRITOS);
        }
        String normal = normalizar(texto);
        for (String amenaza : AMENAZAS) {
            if ((" " + normal + " ").contains(" " + amenaza + " ")) {
                return new Resultado(false, Motivo.AMENAZA);
            }
        }
        for (String frase : INSULTOS_EN_FRASE) {
            if ((" " + normal + " ").contains(" " + frase + " ")) {
                return new Resultado(false, Motivo.INSULTO);
            }
        }
        for (String palabra : palabras(normal)) {
            if (esInsulto(palabra)) {
                // "perro" y "perra" solos son inocentes: solo cuentan dirigidos a alguien.
                if (palabra.equals("perro") || palabra.equals("perra")) {
                    continue;
                }
                return new Resultado(false, Motivo.INSULTO);
            }
        }
        if (DIRIGIDO.matcher(normal).find()) {
            return new Resultado(false, Motivo.INSULTO);
        }
        return Resultado.BIEN;
    }

    // Revisa varios textos y, si alguno no pasa, lanza 422 con el mensaje amable que muestra LlamaLink.
    public static void exigirLimpio(String... textos) {
        for (String texto : textos) {
            Resultado r = revisar(texto);
            if (!r.permitido()) {
                throw new ReglaNegocioException(HttpStatus.UNPROCESSABLE_ENTITY, r.motivo().mensaje());
            }
        }
    }

    private static boolean esInsulto(String palabra) {
        return INSULTOS.contains(palabra) || INSULTOS.contains(sinRepeticiones(palabra));
    }

    private static boolean esSpam(String texto) {
        long enlaces = ENLACE.matcher(texto).results().count();
        return enlaces > 2 || LETRA_REPETIDA.matcher(texto).find();
    }

    // Texto largo escrito casi todo en mayúsculas. El código (que lleva paréntesis, llaves, punto y coma, signos de igual...) no cuenta:
    // una consulta SQL en mayúsculas no es gritar.
    private static boolean gritaTodo(String texto) {
        long letras = texto.chars().filter(Character::isLetter).count();
        if (letras < 25 || texto.chars().anyMatch(c -> "(){};=<>*[]".indexOf(c) >= 0)) {
            return false;
        }
        long mayusculas = texto.chars().filter(Character::isUpperCase).count();
        return mayusculas * 100 / letras >= 85;
    }

    // Minúsculas, sin tildes, con los números y símbolos que se usan para disfrazar letras cambiados por la letra que imitan, y con
    // todo lo que no sea letra convertido en espacio: "mi3rda" -> "mierda", "p.u.t.a" -> "p u t a".
    static String normalizar(String texto) {
        String base = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(base.length());
        for (char c : base.toCharArray()) {
            sb.append(switch (c) {
                case '0' -> 'o';
                case '1', '!', '|' -> 'i';
                case '3' -> 'e';
                case '4', '@' -> 'a';
                case '5', '$' -> 's';
                case '7' -> 't';
                case '8' -> 'b';
                default -> Character.isLetter(c) ? c : ' ';
            });
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    // Las palabras del texto ya normalizado. Las letras sueltas seguidas ("p u t a") se juntan en una palabra.
    private static List<String> palabras(String normal) {
        List<String> palabras = new ArrayList<>();
        StringBuilder suelta = new StringBuilder();
        for (String token : normal.split(" ")) {
            if (token.length() == 1) {
                suelta.append(token);
                continue;
            }
            cerrarSuelta(palabras, suelta);
            palabras.add(token);
        }
        cerrarSuelta(palabras, suelta);
        return palabras;
    }

    private static void cerrarSuelta(List<String> palabras, StringBuilder suelta) {
        if (suelta.length() >= 3) {
            palabras.add(suelta.toString());
        }
        suelta.setLength(0);
    }

    // "puuuuta" -> "puta": las letras repetidas más de dos veces se reducen a una.
    private static String sinRepeticiones(String palabra) {
        return palabra.replaceAll("(.)\\1{2,}", "$1");
    }
}
