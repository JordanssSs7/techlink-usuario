package com.techlink.service;

import com.techlink.dto.MentorEstadisticasDtos.Constancia;
import com.techlink.dto.MentorEstadisticasDtos.CursoAtendido;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

// HU-27: dibuja la constancia de horas de mentoría: nombre del mentor, total de horas y cursos atendidos.
// Paleta de TechLink: Midnight #122C4F, Ocean #5B88B2, Pearl #FBF9E4.
final class ConstanciaPdf {

    private static final double[] MARINO = {0.071, 0.173, 0.310};
    private static final double[] OCEANO = {0.357, 0.533, 0.698};
    private static final double[] PERLA = {0.984, 0.976, 0.894};
    private static final double[] BLANCO = {1, 1, 1};
    private static final double[] NEGRO = {0.05, 0.05, 0.06};
    private static final double[] GRIS = {0.37, 0.40, 0.45};
    private static final double[] LINEA = {0.82, 0.85, 0.90};

    private static final double MARGEN = 56;
    private static final double ANCHO_UTIL = PdfSimple.ANCHO - 2 * MARGEN;
    private static final double LIMITE_INFERIOR = 110;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private ConstanciaPdf() {
    }

    static byte[] generar(Constancia c) {
        PdfSimple pdf = new PdfSimple();
        int pagina = 0;

        // Franja superior con la marca.
        pdf.rectangulo(pagina, 0, PdfSimple.ALTO - 78, PdfSimple.ANCHO, 78, MARINO);
        pdf.texto(pagina, MARGEN, PdfSimple.ALTO - 44, 22, true, BLANCO, "TechLink");
        pdf.texto(pagina, MARGEN, PdfSimple.ALTO - 62, 10, false, PERLA, "Plataforma de mentorías entre estudiantes de Tecsup");

        double y = PdfSimple.ALTO - 130;
        String titulo = "CONSTANCIA DE HORAS DE MENTORÍA";
        pdf.texto(pagina, (PdfSimple.ANCHO - PdfSimple.ancho(titulo, 18, true)) / 2, y, 18, true, MARINO, titulo);
        y -= 12;
        pdf.linea(pagina, MARGEN + 120, y, PdfSimple.ANCHO - MARGEN - 120, y, 1.5, OCEANO);

        // Texto principal.
        y -= 36;
        String parrafo = "Se deja constancia de que %s participó como mentor en la plataforma TechLink y completó %s horas de mentoría en %d %s, realizadas entre el %s y el %s."
                .formatted(c.mentor(), horas(c.horasTotales()), c.sesionesCompletadas(),
                        c.sesionesCompletadas() == 1 ? "sesión" : "sesiones", fecha(c.desde()), fecha(c.hasta()));
        for (String linea : PdfSimple.partir(parrafo, 12, false, ANCHO_UTIL)) {
            pdf.texto(pagina, MARGEN, y, 12, false, NEGRO, linea);
            y -= 18;
        }

        // Recuadro con los datos principales.
        y -= 10;
        double altoCaja = 92;
        pdf.rectangulo(pagina, MARGEN, y - altoCaja, ANCHO_UTIL, altoCaja, PERLA);
        pdf.rectangulo(pagina, MARGEN, y - altoCaja, 4, altoCaja, MARINO);
        String[][] datos = {
                {"Mentor", c.mentor()},
                {"Correo institucional", c.correo()},
                {"Total de horas", horas(c.horasTotales()) + " h"},
                {"Sesiones completadas", String.valueOf(c.sesionesCompletadas())}
        };
        double yc = y - 22;
        for (String[] d : datos) {
            pdf.texto(pagina, MARGEN + 18, yc, 11, true, MARINO, d[0] + ":");
            pdf.texto(pagina, MARGEN + 160, yc, 11, false, NEGRO, d[1]);
            yc -= 19;
        }
        y -= altoCaja + 34;

        // Tabla de cursos atendidos.
        pdf.texto(pagina, MARGEN, y, 13, true, MARINO, "Cursos atendidos");
        y -= 12;
        y = cabeceraTabla(pdf, pagina, y);
        List<CursoAtendido> cursos = c.cursos();
        for (CursoAtendido curso : cursos) {
            if (y < LIMITE_INFERIOR) {
                pdf.nuevaPagina();
                pagina++;
                y = PdfSimple.ALTO - 70;
                y = cabeceraTabla(pdf, pagina, y);
            }
            pdf.texto(pagina, MARGEN + 8, y - 15, 11, false, NEGRO, recortar(curso.curso(), 330, 11));
            pdf.texto(pagina, MARGEN + 350, y - 15, 11, false, NEGRO, String.valueOf(curso.sesiones()));
            pdf.texto(pagina, MARGEN + 420, y - 15, 11, false, NEGRO, horas(curso.horas()));
            y -= 22;
            pdf.linea(pagina, MARGEN, y, MARGEN + ANCHO_UTIL, y, 0.5, LINEA);
        }
        if (y < LIMITE_INFERIOR) {
            pdf.nuevaPagina();
            pagina++;
            y = PdfSimple.ALTO - 70;
        }
        pdf.texto(pagina, MARGEN + 8, y - 16, 11, true, MARINO, "Total");
        pdf.texto(pagina, MARGEN + 350, y - 16, 11, true, MARINO, String.valueOf(c.sesionesCompletadas()));
        pdf.texto(pagina, MARGEN + 420, y - 16, 11, true, MARINO, horas(c.horasTotales()));

        // Pie de cada página.
        int total = pdf.totalPaginas();
        for (int i = 0; i < total; i++) {
            pdf.linea(i, MARGEN, 78, MARGEN + ANCHO_UTIL, 78, 0.8, LINEA);
            pdf.texto(i, MARGEN, 62, 9, false, GRIS, "Emitida el %s a las %s · Folio %s".formatted(
                    c.emitidaEn().format(FECHA), c.emitidaEn().format(HORA), c.folio()));
            String pie = "Documento generado automáticamente por TechLink con las sesiones completadas registradas en la plataforma.";
            pdf.texto(i, MARGEN, 49, 8, false, GRIS, pie);
            String numero = "Página %d de %d".formatted(i + 1, total);
            pdf.texto(i, PdfSimple.ANCHO - MARGEN - PdfSimple.ancho(numero, 9, false), 62, 9, false, GRIS, numero);
        }
        return pdf.generar("Constancia de horas de mentoría - " + c.mentor());
    }

    private static double cabeceraTabla(PdfSimple pdf, int pagina, double y) {
        pdf.rectangulo(pagina, MARGEN, y - 24, ANCHO_UTIL, 24, MARINO);
        pdf.texto(pagina, MARGEN + 8, y - 16, 10, true, BLANCO, "Curso");
        pdf.texto(pagina, MARGEN + 350, y - 16, 10, true, BLANCO, "Sesiones");
        pdf.texto(pagina, MARGEN + 420, y - 16, 10, true, BLANCO, "Horas");
        return y - 24;
    }

    // Corta con "…" lo que no entre en el ancho dado.
    private static String recortar(String texto, double ancho, double tamano) {
        if (PdfSimple.ancho(texto, tamano, false) <= ancho) {
            return texto;
        }
        String corto = texto;
        while (corto.length() > 1 && PdfSimple.ancho(corto + "...", tamano, false) > ancho) {
            corto = corto.substring(0, corto.length() - 1);
        }
        return corto.trim() + "...";
    }

    private static String fecha(java.time.LocalDate fecha) {
        return fecha == null ? "-" : fecha.format(FECHA);
    }

    // 12 -> "12", 12.5 -> "12,5", 12.25 -> "12,25".
    static String horas(double horas) {
        String texto = String.format(Locale.US, "%.2f", horas);
        texto = texto.replaceAll("0+$", "").replaceAll("\\.$", "");
        return texto.replace('.', ',');
    }
}
