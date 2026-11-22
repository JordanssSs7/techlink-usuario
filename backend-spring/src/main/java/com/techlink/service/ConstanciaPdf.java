package com.techlink.service;

import com.techlink.dto.MentorEstadisticasDtos.Constancia;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

// HU-27: dibuja la constancia de mentoría (hoja A4 horizontal): logo de TechLink, el nombre del mentor tal como está en su
// perfil, el curso principal y las horas, y el sello oficial de TechLink con la T en lugar de una firma. Lleva un código de
// verificación con su QR (que abre la página pública de TechLink que comprueba que es auténtica) y aclara que la emite TechLink,
// no TECSUP. No lista cursos ni sesiones. Paleta de TechLink: Midnight #122C4F, Ocean #5B88B2, Pearl #FBF9E4.
final class ConstanciaPdf {

    static final double ANCHO = 842;
    static final double ALTO = 595;

    private static final double[] MARINO = {0.071, 0.173, 0.310};
    private static final double[] OCEANO = {0.357, 0.533, 0.698};
    private static final double[] CELESTE = {0.878, 0.918, 0.957};
    private static final double[] MARCA_DE_AGUA = {0.955, 0.972, 0.99};
    private static final double[] PERLA = {0.984, 0.976, 0.894};
    private static final double[] BLANCO = {1, 1, 1};
    private static final double[] GRIS = {0.37, 0.40, 0.45};
    private static final double CENTRO = ANCHO / 2;

    private ConstanciaPdf() {
    }

    // urlVerificacion: la dirección de la página pública que comprueba esta constancia; es lo que codifica el QR.
    static byte[] generar(Constancia c, String urlVerificacion) {
        PdfSimple pdf = new PdfSimple(ANCHO, ALTO);
        int p = 0;

        // Marco fino, marca de agua con la T de TechLink y ondas de las esquinas.
        marco(pdf, p);
        logo(pdf, p, CENTRO - 150, 130, 12.5, MARCA_DE_AGUA);
        ondas(pdf, p, false);
        ondas(pdf, p, true);

        // Marca de TechLink.
        String marca = "TechLink";
        double anchoMarca = 34 + 8 + PdfSimple.ancho(marca, 24, true);
        double xMarca = CENTRO - anchoMarca / 2;
        logo(pdf, p, xMarca, 520, 1.4, MARINO);
        pdf.texto(p, xMarca + 42, 529, 24, true, MARINO, marca);

        // Título, con rombos a los lados de la banda.
        centrado(pdf, p, 462, 46, "F2", MARINO, "CONSTANCIA");
        pdf.rectangulo(p, CENTRO - 175, 421, 350, 26, MARINO);
        centrado(pdf, p, 429, 11.5, "F2", PERLA, "DE PARTICIPACIÓN COMO MENTOR");
        rombo(pdf, p, CENTRO - 192, 434, 5, OCEANO);
        rombo(pdf, p, CENTRO + 192, 434, 5, OCEANO);

        // A quién se otorga: el nombre del perfil del mentor (el de su cuenta institucional).
        centrado(pdf, p, 386, 12, "F1", GRIS, "TechLink deja constancia de que");
        double tamano = 42;
        while (tamano > 22 && PdfSimple.anchoTimes(c.mentor(), tamano) > 600) {
            tamano -= 2;
        }
        pdf.textoCon(p, CENTRO - PdfSimple.anchoTimes(c.mentor(), tamano) / 2, 336, tamano, "F3", MARINO, c.mentor());
        pdf.linea(p, CENTRO - 250, 322, CENTRO - 10, 322, 1.5, OCEANO);
        pdf.linea(p, CENTRO + 10, 322, CENTRO + 250, 322, 1.5, OCEANO);
        rombo(pdf, p, CENTRO, 322, 5, MARINO);

        // Motivo, sin detalle de cursos ni sesiones.
        String curso = c.cursoPrincipal() == null ? "la plataforma" : c.cursoPrincipal();
        String motivo = ("participó como mentor de %s en TechLink, la comunidad de mentorías de Tecsup, y completó más de %s "
                + "horas de mentoría compartiendo su conocimiento con otros estudiantes.")
                .formatted(curso, ConstanciaPdf.horas(c.horasMinimas()));
        double y = 292;
        for (String linea : PdfSimple.partir(motivo, 14 * 0.9, false, 500)) {
            pdf.textoCon(p, CENTRO - PdfSimple.anchoTimes(linea, 14) / 2, y, 14, "F4", MARINO, linea);
            y -= 20;
        }

        // Código de verificación con su QR a la izquierda, sello oficial al centro y fecha a la derecha.
        String codigo = c.codigoVerificacion() == null ? "" : c.codigoVerificacion();
        qr(pdf, p, urlVerificacion, 154, 122, 64);
        pdf.textoCon(p, 186 - PdfSimple.anchoTimes(codigo, 13) / 2, 108, 13, "F4", MARINO, codigo);
        pdf.linea(p, 96, 104, 276, 104, 1.2, OCEANO);
        centrado(pdf, p, 90, 10, "F2", MARINO, 186, "Código de verificación");
        centrado(pdf, p, 78, 7.5, "F1", GRIS, 186, "Escanea el QR para comprobarla");
        String fecha = "%d de %s de %d".formatted(c.emitidaEn().getDayOfMonth(),
                c.emitidaEn().getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-PE")), c.emitidaEn().getYear());
        pdf.textoCon(p, 666 - PdfSimple.anchoTimes(fecha, 13) / 2, 110, 13, "F4", MARINO, fecha);
        pdf.linea(p, 576, 104, 756, 104, 1.2, OCEANO);
        centrado(pdf, p, 86, 10, "F2", MARINO, 666, "Fecha de emisión");
        sello(pdf, p, CENTRO, 100);

        centrado(pdf, p, 38, 8.5, "F1", GRIS, "Constancia emitida por TechLink, plataforma de mentorías entre estudiantes de Tecsup. No es una certificación oficial de TECSUP.");
        centrado(pdf, p, 27, 8, "F1", GRIS, "Comprueba su autenticidad con el código QR o en " + urlVerificacion + " · soporte.techlink.app@gmail.com");
        return pdf.generar("Constancia de mentoría - " + c.mentor());
    }

    // Código QR dibujado con rectángulos (una tira por cada fila de módulos oscuros seguidos), con un margen blanco alrededor.
    private static void qr(PdfSimple pdf, int pagina, String contenido, double x, double y, double lado) {
        BitMatrix matriz;
        try {
            matriz = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.MARGIN, 0, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
        } catch (WriterException e) {
            throw new IllegalStateException("No se pudo generar el código QR", e);
        }
        double modulo = lado / matriz.getWidth();
        pdf.rectangulo(pagina, x - 5, y - 5, lado + 10, lado + 10, BLANCO);
        for (int fila = 0; fila < matriz.getHeight(); fila++) {
            int col = 0;
            while (col < matriz.getWidth()) {
                if (!matriz.get(col, fila)) {
                    col++;
                    continue;
                }
                int inicio = col;
                while (col < matriz.getWidth() && matriz.get(col, fila)) {
                    col++;
                }
                // Un poco más ancho y alto que el módulo, para que no queden líneas blancas entre las tiras.
                pdf.rectangulo(pagina, x + inicio * modulo, y + (matriz.getHeight() - 1 - fila) * modulo,
                        (col - inicio) * modulo + 0.15, modulo + 0.15, MARINO);
            }
        }
    }

    // Texto centrado en la página.
    private static void centrado(PdfSimple pdf, int pagina, double y, double tamano, String fuente, double[] color, String texto) {
        centrado(pdf, pagina, y, tamano, fuente, color, CENTRO, texto);
    }

    // Texto centrado alrededor de la posición x dada.
    private static void centrado(PdfSimple pdf, int pagina, double y, double tamano, String fuente, double[] color, double x, String texto) {
        double ancho = PdfSimple.ancho(texto, tamano, "F2".equals(fuente));
        pdf.textoCon(pagina, x - ancho / 2, y, tamano, fuente, color, texto);
    }

    // El logo de TechLink (dos bloques en diagonal), con su esquina inferior izquierda en (x, y).
    private static void logo(PdfSimple pdf, int pagina, double x, double y, double escala, double[] color) {
        // Coordenadas del logo en una cuadrícula de 24 x 24 con el eje y hacia abajo, como en el ícono de la aplicación.
        double[][] izquierdo = {{3, 3}, {11, 3}, {8, 12}, {3, 12}};
        double[][] derecho = {{13, 3}, {21, 3}, {21, 12}, {16, 12}, {13, 21}, {9, 21}};
        pdf.poligono(pagina, mover(izquierdo, x, y, escala), color);
        pdf.poligono(pagina, mover(derecho, x, y, escala), color);
    }

    private static double[][] mover(double[][] puntos, double x, double y, double escala) {
        double[][] r = new double[puntos.length][2];
        for (int i = 0; i < puntos.length; i++) {
            r[i][0] = x + (puntos[i][0] - 3) * escala;
            r[i][1] = y + (21 - puntos[i][1]) * escala;
        }
        return r;
    }

    private static void rombo(PdfSimple pdf, int pagina, double cx, double cy, double radio, double[] color) {
        pdf.poligono(pagina, new double[][]{{cx, cy + radio}, {cx + radio, cy}, {cx, cy - radio}, {cx - radio, cy}}, color);
    }

    // Marco fino alrededor de la hoja.
    private static void marco(PdfSimple pdf, int pagina) {
        double m = 24;
        double[] color = OCEANO;
        pdf.linea(pagina, m, m, ANCHO - m, m, 0.8, color);
        pdf.linea(pagina, ANCHO - m, m, ANCHO - m, ALTO - m, 0.8, color);
        pdf.linea(pagina, ANCHO - m, ALTO - m, m, ALTO - m, 0.8, color);
        pdf.linea(pagina, m, ALTO - m, m, m, 0.8, color);
    }

    // Sello oficial de TechLink: círculo con doble anillo, texto alrededor, puntos y la T de TechLink al centro.
    private static void sello(PdfSimple pdf, int pagina, double cx, double cy) {
        pdf.circulo(pagina, cx, cy, 56, MARINO, null, 0);
        pdf.circulo(pagina, cx, cy, 52, null, PERLA, 1.2);
        pdf.circulo(pagina, cx, cy, 31, OCEANO, null, 0);
        pdf.circulo(pagina, cx, cy, 31, null, PERLA, 1.4);
        // Texto en arco: arriba "TECHLINK · MENTORÍAS" y abajo "TECSUP", leídos de izquierda a derecha.
        arco(pdf, pagina, cx, cy, 40.5, "TECHLINK · MENTORES", true);
        arco(pdf, pagina, cx, cy, 44, "TECSUP", false);
        // Puntos separadores a los lados.
        for (double grados : new double[]{180, 0}) {
            double a = Math.toRadians(grados);
            pdf.circulo(pagina, cx + Math.cos(a) * 42, cy + Math.sin(a) * 42, 2.2, PERLA, null, 0);
        }
        logo(pdf, pagina, cx - 16, cy - 13.5, 1.55, BLANCO);
    }

    // Reparte un texto en un arco de circunferencia. arriba = true: sobre el centro, de izquierda a derecha;
    // false: debajo del centro, también de izquierda a derecha.
    private static void arco(PdfSimple pdf, int pagina, double cx, double cy, double radio, String texto, boolean arriba) {
        double tamano = 7.5;
        double paso = Math.toRadians(arriba ? 9.2 : 14);
        double total = paso * (texto.length() - 1);
        for (int i = 0; i < texto.length(); i++) {
            String letra = texto.substring(i, i + 1);
            if (letra.isBlank()) {
                continue;
            }
            double ancho = PdfSimple.ancho(letra, tamano, true);
            // En el arco de arriba se avanza de izquierda a derecha bajando el ángulo; en el de abajo, subiéndolo.
            double theta = arriba ? Math.PI / 2 + total / 2 - i * paso : 3 * Math.PI / 2 - total / 2 + i * paso;
            double giro = arriba ? theta - Math.PI / 2 : theta + Math.PI / 2;
            // La letra se centra sobre su punto, siguiendo la tangente.
            double x = cx + Math.cos(theta) * radio - Math.cos(giro) * ancho / 2;
            double y = cy + Math.sin(theta) * radio - Math.sin(giro) * ancho / 2;
            pdf.textoGirado(pagina, x, y, tamano, "F2", PERLA, giro, letra);
        }
    }

    // Ondas decorativas en una esquina. espejo = false: arriba a la izquierda; true: abajo a la derecha.
    private static void ondas(PdfSimple pdf, int pagina, boolean espejo) {
        DoubleUnaryOperator fx = espejo ? x -> ANCHO - x : x -> x;
        DoubleUnaryOperator fy = espejo ? y -> ALTO - y : y -> y;
        // Cada onda: (esquina de arriba, curva hacia la izquierda), vistas desde la esquina superior izquierda.
        pdf.ruta(pagina, trazo(fx, fy, new double[][]{{0, 595}, {230, 595}, {170, 575}, {80, 520}, {0, 430}}), CELESTE, null, 0);
        pdf.ruta(pagina, trazo(fx, fy, new double[][]{{0, 595}, {150, 595}, {100, 575}, {45, 535}, {0, 480}}), OCEANO, null, 0);
        pdf.ruta(pagina, trazo(fx, fy, new double[][]{{0, 595}, {95, 595}, {60, 580}, {25, 555}, {0, 525}}), MARINO, null, 0);
    }

    // Camino cerrado: de la esquina a un punto del borde de arriba, una curva hasta el borde de la izquierda y vuelta.
    private static String trazo(DoubleUnaryOperator fx, DoubleUnaryOperator fy, double[][] pts) {
        return String.format(Locale.US, "%.1f %.1f m %.1f %.1f l %.1f %.1f %.1f %.1f %.1f %.1f c h",
                fx.applyAsDouble(pts[0][0]), fy.applyAsDouble(pts[0][1]),
                fx.applyAsDouble(pts[1][0]), fy.applyAsDouble(pts[1][1]),
                fx.applyAsDouble(pts[2][0]), fy.applyAsDouble(pts[2][1]),
                fx.applyAsDouble(pts[3][0]), fy.applyAsDouble(pts[3][1]),
                fx.applyAsDouble(pts[4][0]), fy.applyAsDouble(pts[4][1]));
    }

    // 12 -> "12", 12.5 -> "12,5", 12.25 -> "12,25".
    static String horas(double horas) {
        String texto = String.format(Locale.US, "%.2f", horas);
        texto = texto.replaceAll("0+$", "").replaceAll("\\.$", "");
        return texto.replace('.', ',');
    }

    // Lista de textos de apoyo para pruebas.
    static List<String> lineas(String texto, double tamano, double ancho) {
        return PdfSimple.partir(texto, tamano * 0.9, false, ancho);
    }
}
