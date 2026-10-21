package utp.edu.pe.recomendaciones.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Herramientas de texto del asistente para entender mensajes mal escritos.
 * <p>
 * Cada palabra se reduce a una "clave fonética" del español (b/v, c/s/z, ll/y, h muda, qu/k, g/j...) y dos
 * claves se consideran iguales si son idénticas o difieren en 1-2 letras (según la longitud, con
 * transposiciones). Así "cavesa", "cabesa" y "cabeza" coinciden, igual que "migrña" o "dermatolojia".
 * Sin dependencias externas: se puede probar con un simple {@code main}.
 */
public final class AsistenteTexto {
    private AsistenteTexto() { }

    /** Minúsculas, sin tildes ni signos, espacios simples. "¿Dolor de cabeza?" -> "dolor de cabeza". */
    public static String normalizar(String valor) {
        if (valor == null) return "";
        String sinTildes = Normalizer.normalize(valor, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Clave fonética de UNA palabra ya normalizada. */
    public static String fonetica(String palabra) {
        if (palabra == null || palabra.isEmpty()) return "";
        String p = colapsar(palabra);
        p = p.replace("ph", "f").replace("ch", "C").replace("qu", "k").replace("ll", "i");
        p = p.replace("gue", "ge").replace("gui", "gi").replace("ge", "je").replace("gi", "ji");
        p = p.replace("ce", "se").replace("ci", "si").replace("c", "k");
        p = p.replace("z", "s").replace("v", "b").replace("h", "").replace("y", "i").replace("w", "u");
        return colapsar(p);
    }

    private static String colapsar(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (i == 0 || c != s.charAt(i - 1) || Character.isDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    /** Palabras fonéticas de un texto libre (ya normalizado dentro). */
    public static List<String> palabras(String texto) {
        List<String> out = new ArrayList<>();
        String n = normalizar(texto);
        if (n.isEmpty()) return out;
        for (String w : n.split(" ")) out.add(fonetica(w));
        return out;
    }

    /** Errores permitidos según el largo: palabras cortas deben ser exactas para evitar falsos positivos. */
    static int tolerancia(int largo) {
        if (largo <= 3) return 0;
        if (largo <= 7) return 1;
        return 2;
    }

    /** Distancia de edición con transposiciones, cortada en {@code max+1}. */
    static int distancia(String a, String b, int max) {
        if (Math.abs(a.length() - b.length()) > max) return max + 1;
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + costo);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }

    /** ¿Las dos palabras fonéticas son "la misma" con tolerancia a errores? */
    public static boolean parecida(String a, String b) {
        if (a.equals(b)) return true;
        int tol = tolerancia(Math.min(a.length(), b.length()));
        return tol > 0 && distancia(a, b, tol) <= tol;
    }

    /** ¿La palabra empieza como la raíz (p. ej. "cardiolgo" ~ raíz "cardiolog")? */
    public static boolean empiezaCon(String palabra, String raiz) {
        if (palabra.length() < raiz.length() - 1) return false;
        String inicio = palabra.length() >= raiz.length() ? palabra.substring(0, raiz.length()) : palabra;
        if (inicio.equals(raiz)) return true;
        int tol = raiz.length() >= 6 ? 1 : 0;
        return tol > 0 && distancia(inicio, raiz, tol) <= tol;
    }

    /** Posición donde la frase (palabras fonéticas) aparece dentro del texto, o -1. */
    public static int buscar(List<String> texto, List<String> frase) {
        if (frase.isEmpty() || texto.size() < frase.size()) return -1;
        for (int i = 0; i + frase.size() <= texto.size(); i++) {
            boolean ok = true;
            for (int j = 0; j < frase.size() && ok; j++) ok = parecida(texto.get(i + j), frase.get(j));
            if (ok) return i;
        }
        return -1;
    }

    public static boolean contiene(List<String> texto, String frase) {
        return buscar(texto, palabras(frase)) >= 0;
    }
}
