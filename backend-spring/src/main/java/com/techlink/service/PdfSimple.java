package com.techlink.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// Escritor mínimo de PDF (HU-27): páginas A4 (vertical u horizontal) con texto Helvetica y Times, rectángulos, líneas, polígonos
// y círculos. No necesita librerías externas.
// El texto se escribe en Latin-1 (WinAnsi), que cubre las tildes, la ñ y los signos del español; cualquier otro
// carácter se cambia por "?". Los textos no se comprimen, así que el PDF se puede revisar a simple vista.
final class PdfSimple {

    static final double ANCHO = 595;
    static final double ALTO = 842;

    // Anchos de Helvetica (por mil de la altura de letra) de los caracteres ASCII 32 a 126.
    private static final int[] ANCHOS = {
            278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
            556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
            1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
            667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
            333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
            556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584
    };

    private final List<StringBuilder> paginas = new ArrayList<>();
    private final double anchoPagina;
    private final double altoPagina;

    // A4 vertical.
    PdfSimple() {
        this(ANCHO, ALTO);
    }

    // Página de cualquier tamaño en puntos (A4 horizontal: 842 x 595).
    PdfSimple(double anchoPagina, double altoPagina) {
        this.anchoPagina = anchoPagina;
        this.altoPagina = altoPagina;
        nuevaPagina();
    }

    void nuevaPagina() {
        paginas.add(new StringBuilder());
    }

    int totalPaginas() {
        return paginas.size();
    }

    // Texto con la esquina inferior izquierda en (x, y). El origen del PDF es la esquina inferior izquierda de la página.
    void texto(int pagina, double x, double y, double tamano, boolean negrita, double[] color, String texto) {
        textoCon(pagina, x, y, tamano, negrita ? "F2" : "F1", color, texto);
    }

    // Igual, eligiendo la tipografía: F1 Helvetica, F2 Helvetica negrita, F3 Times negrita cursiva, F4 Times.
    void textoCon(int pagina, double x, double y, double tamano, String fuente, double[] color, String texto) {
        StringBuilder c = paginas.get(pagina);
        c.append(String.format(java.util.Locale.US, "BT %.3f %.3f %.3f rg /%s %.2f Tf %.2f %.2f Td (%s) Tj ET\n",
                color[0], color[1], color[2], fuente, tamano, x, y, escapar(texto)));
    }

    // Texto girado "angulo" radianes (en sentido contrario a las agujas del reloj) alrededor de su punto de inicio (x, y).
    void textoGirado(int pagina, double x, double y, double tamano, String fuente, double[] color, double angulo, String texto) {
        StringBuilder c = paginas.get(pagina);
        c.append(String.format(java.util.Locale.US, "BT %.3f %.3f %.3f rg /%s %.2f Tf %.4f %.4f %.4f %.4f %.2f %.2f Tm (%s) Tj ET\n",
                color[0], color[1], color[2], fuente, tamano, Math.cos(angulo), Math.sin(angulo), -Math.sin(angulo),
                Math.cos(angulo), x, y, escapar(texto)));
    }

    // Ancho aproximado de un texto en Times (un poco más angosto que Helvetica).
    static double anchoTimes(String texto, double tamano) {
        return ancho(texto, tamano, false) * 0.9;
    }

    // Polígono relleno a partir de sus vértices.
    void poligono(int pagina, double[][] puntos, double[] color) {
        StringBuilder ruta = new StringBuilder();
        for (int i = 0; i < puntos.length; i++) {
            ruta.append(String.format(java.util.Locale.US, "%.2f %.2f %s ", puntos[i][0], puntos[i][1], i == 0 ? "m" : "l"));
        }
        ruta(pagina, ruta + "h", color, null, 0);
    }

    // Círculo con relleno y/o borde (null = sin eso), dibujado con cuatro curvas.
    void circulo(int pagina, double cx, double cy, double r, double[] relleno, double[] borde, double grosor) {
        double k = 0.5523 * r;
        String ruta = String.format(java.util.Locale.US,
                "%.2f %.2f m %.2f %.2f %.2f %.2f %.2f %.2f c %.2f %.2f %.2f %.2f %.2f %.2f c %.2f %.2f %.2f %.2f %.2f %.2f c %.2f %.2f %.2f %.2f %.2f %.2f c h",
                cx + r, cy,
                cx + r, cy + k, cx + k, cy + r, cx, cy + r,
                cx - k, cy + r, cx - r, cy + k, cx - r, cy,
                cx - r, cy - k, cx - k, cy - r, cx, cy - r,
                cx + k, cy - r, cx + r, cy - k, cx + r, cy);
        ruta(pagina, ruta, relleno, borde, grosor);
    }

    // Una ruta escrita con operadores de PDF (m, l, c, h), rellena y/o con borde.
    void ruta(int pagina, String operadores, double[] relleno, double[] borde, double grosor) {
        StringBuilder c = paginas.get(pagina);
        if (relleno != null) {
            c.append(String.format(java.util.Locale.US, "%.3f %.3f %.3f rg ", relleno[0], relleno[1], relleno[2]));
        }
        if (borde != null) {
            c.append(String.format(java.util.Locale.US, "%.3f %.3f %.3f RG %.2f w ", borde[0], borde[1], borde[2], grosor));
        }
        String pintar = relleno != null && borde != null ? "B" : relleno != null ? "f" : "S";
        c.append(operadores).append(' ').append(pintar).append('\n');
    }

    void rectangulo(int pagina, double x, double y, double ancho, double alto, double[] color) {
        paginas.get(pagina).append(String.format(java.util.Locale.US, "%.3f %.3f %.3f rg %.2f %.2f %.2f %.2f re f\n",
                color[0], color[1], color[2], x, y, ancho, alto));
    }

    void linea(int pagina, double x1, double y1, double x2, double y2, double grosor, double[] color) {
        paginas.get(pagina).append(String.format(java.util.Locale.US, "%.3f %.3f %.3f RG %.2f w %.2f %.2f m %.2f %.2f l S\n",
                color[0], color[1], color[2], grosor, x1, y1, x2, y2));
    }

    // Ancho aproximado de un texto, para centrarlo o partirlo en líneas. Las letras con tilde miden como su base.
    static double ancho(String texto, double tamano, boolean negrita) {
        double total = 0;
        for (char ch : texto.toCharArray()) {
            total += (ch >= 32 && ch <= 126 ? ANCHOS[ch - 32] : 556) * (negrita ? 1.06 : 1.0);
        }
        return total * tamano / 1000.0;
    }

    // Parte un texto en líneas que no pasen del ancho dado.
    static List<String> partir(String texto, double tamano, boolean negrita, double anchoMaximo) {
        List<String> lineas = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : texto.split("\\s+")) {
            String prueba = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (!actual.isEmpty() && ancho(prueba, tamano, negrita) > anchoMaximo) {
                lineas.add(actual.toString());
                actual = new StringBuilder(palabra);
            } else {
                actual = new StringBuilder(prueba);
            }
        }
        if (!actual.isEmpty()) {
            lineas.add(actual.toString());
        }
        return lineas;
    }

    private static String escapar(String texto) {
        StringBuilder sb = new StringBuilder();
        for (char ch : texto.toCharArray()) {
            if (ch == '\\' || ch == '(' || ch == ')') {
                sb.append('\\').append(ch);
            } else if (ch < 32) {
                sb.append(' ');
            } else {
                sb.append(ch > 255 ? '?' : ch);
            }
        }
        return sb.toString();
    }

    // Arma el archivo: catálogo, páginas, las dos tipografías y el contenido de cada página.
    byte[] generar(String titulo) {
        int n = paginas.size();
        // Objetos: 1 catálogo, 2 páginas, 3 y 4 tipografías, 5 información, y por cada página (página, contenido).
        List<byte[]> objetos = new ArrayList<>();
        StringBuilder hijos = new StringBuilder();
        for (int i = 0; i < n; i++) {
            hijos.append(8 + 2 * i).append(" 0 R ");
        }
        objetos.add(latin("<< /Type /Catalog /Pages 2 0 R >>"));
        objetos.add(latin("<< /Type /Pages /Kids [" + hijos + "] /Count " + n + " >>"));
        objetos.add(latin("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
        objetos.add(latin("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"));
        objetos.add(latin("<< /Title (" + escapar(titulo) + ") /Producer (TechLink) >>"));
        objetos.add(latin("<< /Type /Font /Subtype /Type1 /BaseFont /Times-BoldItalic /Encoding /WinAnsiEncoding >>"));
        objetos.add(latin("<< /Type /Font /Subtype /Type1 /BaseFont /Times-Roman /Encoding /WinAnsiEncoding >>"));
        for (int i = 0; i < n; i++) {
            objetos.add(latin("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + (int) anchoPagina + " " + (int) altoPagina + "] "
                    + "/Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 6 0 R /F4 7 0 R >> >> /Contents " + (9 + 2 * i) + " 0 R >>"));
            byte[] contenido = latin(paginas.get(i).toString());
            ByteArrayOutputStream flujo = new ByteArrayOutputStream();
            flujo.writeBytes(latin("<< /Length " + contenido.length + " >>\nstream\n"));
            flujo.writeBytes(contenido);
            flujo.writeBytes(latin("endstream"));
            objetos.add(flujo.toByteArray());
        }

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.writeBytes(latin("%PDF-1.4\n"));
        int[] posiciones = new int[objetos.size()];
        for (int i = 0; i < objetos.size(); i++) {
            posiciones[i] = salida.size();
            salida.writeBytes(latin((i + 1) + " 0 obj\n"));
            salida.writeBytes(objetos.get(i));
            salida.writeBytes(latin("\nendobj\n"));
        }
        int inicioTabla = salida.size();
        StringBuilder tabla = new StringBuilder("xref\n0 " + (objetos.size() + 1) + "\n0000000000 65535 f \n");
        for (int p : posiciones) {
            tabla.append(String.format("%010d 00000 n \n", p));
        }
        tabla.append("trailer\n<< /Size ").append(objetos.size() + 1).append(" /Root 1 0 R /Info 5 0 R >>\nstartxref\n")
                .append(inicioTabla).append("\n%%EOF\n");
        salida.writeBytes(latin(tabla.toString()));
        return salida.toByteArray();
    }

    private static byte[] latin(String texto) {
        return texto.getBytes(StandardCharsets.ISO_8859_1);
    }
}
