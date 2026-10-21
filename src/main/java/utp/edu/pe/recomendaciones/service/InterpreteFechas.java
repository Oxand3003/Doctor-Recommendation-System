package utp.edu.pe.recomendaciones.service;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee fechas y horas escritas con libertad: "mañana", "el viernez", "25 de octubre", "20/10", "el 25",
 * "dentro de 3 días", "a las 4 y media", "4pm", "por la tarde". Tolera errores de ortografía.
 * Sin dependencias externas.
 */
public final class InterpreteFechas {
    private InterpreteFechas() { }

    private static final Pattern ISO_DATE = Pattern.compile("\\b(20\\d{2})-(\\d{1,2})-(\\d{1,2})\\b");
    private static final Pattern LOCAL_DATE = Pattern.compile("(?<![\\d/-])(\\d{1,2})[/-](\\d{1,2})[/-](20\\d{2})\\b");
    private static final Pattern SHORT_DATE = Pattern.compile("(?<![\\d/-])(\\d{1,2})[/-](\\d{1,2})(?![\\d/:-])");
    private static final Pattern CLOCK_TIME = Pattern.compile("(?i)(?<![\\d/-])(\\d{1,2})[:.](\\d{2})(?:\\s*(a\\.?\\s?m\\.?|p\\.?\\s?m\\.?))?(?![\\d/-])");
    private static final Pattern AM_PM_TIME = Pattern.compile("(?i)(?<![\\d/:.-])(\\d{1,2})\\s*(a\\.?\\s?m\\.?|p\\.?\\s?m\\.?)(?![a-z])");
    private static final Pattern A_LAS = Pattern.compile("\\ba la?s?\\s+(\\d{1,2})(?:\\s*(?:h|hs|hrs|horas))?(?:\\s+y\\s+(media|cuarto))?(?:\\s+de la\\s+(manana|tarde|noche))?");
    private static final Pattern HORA_DE_LA = Pattern.compile("\\b(\\d{1,2})\\s+de la\\s+(manana|tarde|noche)");
    private static final Pattern DIA_DEL_MES = Pattern.compile("\\b(?:el|para el|dia)\\s+(\\d{1,2})\\b(?!\\s*(?:de la|am|pm|h\\b|horas|:))");
    private static final Pattern EN_N_DIAS = Pattern.compile("\\b(?:en|dentro de)\\s+(\\d{1,2})\\s+dias?\\b");
    private static final Pattern DIA_Y_MES = Pattern.compile("\\b(\\d{1,2})\\s*(?:de\\s+)?([a-z]{4,10})\\b");

    private static final Map<String, Integer> MESES = new LinkedHashMap<>();
    private static final Map<String, DayOfWeek> DIAS = new LinkedHashMap<>();

    static {
        String[] meses = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "setiembre",
                "octubre", "noviembre", "diciembre"};
        for (int i = 0; i < meses.length; i++) MESES.put(AsistenteTexto.fonetica(meses[i]), i + 1);
        MESES.put(AsistenteTexto.fonetica("septiembre"), 9);
        DIAS.put("lunes", DayOfWeek.MONDAY);
        DIAS.put("martes", DayOfWeek.TUESDAY);
        DIAS.put("miercoles", DayOfWeek.WEDNESDAY);
        DIAS.put("jueves", DayOfWeek.THURSDAY);
        DIAS.put("viernes", DayOfWeek.FRIDAY);
        DIAS.put("sabado", DayOfWeek.SATURDAY);
        DIAS.put("domingo", DayOfWeek.SUNDAY);
    }

    /** Fecha mencionada en el texto (hoy o posterior) o null. */
    public static LocalDate fecha(String texto, LocalDate hoy) {
        if (texto == null || texto.isBlank()) return null;
        LocalDate f = fechaNumerica(texto, hoy);
        if (f == null) f = fechaConPalabras(texto, hoy);
        return f != null && !f.isBefore(hoy) ? f : null;
    }

    private static LocalDate fechaNumerica(String texto, LocalDate hoy) {
        Matcher iso = ISO_DATE.matcher(texto);
        if (iso.find()) {
            try {
                return LocalDate.of(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)), Integer.parseInt(iso.group(3)));
            } catch (DateTimeException ignored) { /* sigue */ }
        }
        Matcher local = LOCAL_DATE.matcher(texto);
        if (local.find()) {
            try {
                return LocalDate.of(Integer.parseInt(local.group(3)), Integer.parseInt(local.group(2)), Integer.parseInt(local.group(1)));
            } catch (DateTimeException ignored) { /* sigue */ }
        }
        Matcher corta = SHORT_DATE.matcher(texto);
        if (corta.find()) {
            try {
                LocalDate c = LocalDate.of(hoy.getYear(), Integer.parseInt(corta.group(2)), Integer.parseInt(corta.group(1)));
                return c.isBefore(hoy) ? c.plusYears(1) : c;
            } catch (DateTimeException ignored) { /* sigue */ }
        }
        return null;
    }

    private static LocalDate fechaConPalabras(String texto, LocalDate hoy) {
        // "de la mañana" es una hora, no "mañana" (el día siguiente).
        String normal = AsistenteTexto.normalizar(texto)
                .replaceAll("\\b(de|por|en|a) la manana\\b", " ")
                .replaceAll("\\b(de|por|en|a) la tarde\\b", " ")
                .replaceAll("\\b(de|por|en|a) la noche\\b", " ");
        List<String> palabras = AsistenteTexto.palabras(normal);

        // "25 de octubre", "25 octubre", "5 de setiembre" (mes con errores permitidos).
        Matcher dm = DIA_Y_MES.matcher(normal);
        while (dm.find()) {
            Integer mes = mesDe(dm.group(2));
            if (mes == null) continue;
            try {
                LocalDate c = LocalDate.of(hoy.getYear(), mes, Integer.parseInt(dm.group(1)));
                return c.isBefore(hoy) ? c.plusYears(1) : c;
            } catch (DateTimeException ignored) { /* siguiente */ }
        }

        Matcher enDias = EN_N_DIAS.matcher(normal);
        if (enDias.find()) return hoy.plusDays(Integer.parseInt(enDias.group(1)));

        if (AsistenteTexto.contiene(palabras, "pasado manana")) return hoy.plusDays(2);
        if (palabras.contains(AsistenteTexto.fonetica("hoy"))) return hoy;
        if (AsistenteTexto.contiene(palabras, "manana")) return hoy.plusDays(1);

        for (Map.Entry<String, DayOfWeek> dia : DIAS.entrySet()) {
            if (AsistenteTexto.contiene(palabras, dia.getKey())) {
                int faltan = (dia.getValue().getValue() - hoy.getDayOfWeek().getValue() + 7) % 7;
                return hoy.plusDays(faltan == 0 ? 7 : faltan);
            }
        }

        // "el 25" (sin mes): este mes si aún no pasó, si no el siguiente.
        Matcher soloDia = DIA_DEL_MES.matcher(normal);
        if (soloDia.find()) {
            int d = Integer.parseInt(soloDia.group(1));
            if (d >= 1 && d <= 31) {
                for (int add = 0; add <= 12; add++) {
                    LocalDate base = hoy.withDayOfMonth(1).plusMonths(add);
                    if (d <= base.lengthOfMonth()) {
                        LocalDate c = base.withDayOfMonth(d);
                        if (!c.isBefore(hoy)) return c;
                    }
                }
            }
        }
        return null;
    }

    private static Integer mesDe(String palabra) {
        String f = AsistenteTexto.fonetica(palabra);
        if (MESES.containsKey(f)) return MESES.get(f);          // exacto primero (junio y julio se parecen)
        Integer unico = null;
        for (Map.Entry<String, Integer> m : MESES.entrySet()) {
            if (AsistenteTexto.parecida(f, m.getKey())) {
                if (unico != null && !unico.equals(m.getValue())) return null;   // ambiguo
                unico = m.getValue();
            }
        }
        return unico;
    }

    /** Hora (o "desde qué hora") mencionada en el texto, o null. */
    public static LocalTime hora(String texto) {
        if (texto == null || texto.isBlank()) return null;
        Matcher reloj = CLOCK_TIME.matcher(texto);
        while (reloj.find()) {
            int h = Integer.parseInt(reloj.group(1));
            int m = Integer.parseInt(reloj.group(2));
            String meridiano = AsistenteTexto.normalizar(reloj.group(3)).replace(" ", "");
            if (!meridiano.isEmpty()) {
                h %= 12;
                if (meridiano.startsWith("p")) h += 12;
            }
            try { return LocalTime.of(h, m); } catch (DateTimeException ignored) { /* siguiente */ }
        }
        Matcher ampm = AM_PM_TIME.matcher(texto);
        if (ampm.find()) {
            int h = Integer.parseInt(ampm.group(1));
            if (h >= 1 && h <= 12) {
                h %= 12;
                if (AsistenteTexto.normalizar(ampm.group(2)).replace(" ", "").startsWith("p")) h += 12;
                return LocalTime.of(h, 0);
            }
        }
        String normal = AsistenteTexto.normalizar(texto);
        Matcher aLas = A_LAS.matcher(normal);
        if (aLas.find()) {
            LocalTime t = desdePalabras(Integer.parseInt(aLas.group(1)), aLas.group(3));
            if (t != null) {
                String extra = aLas.group(2);
                if ("media".equals(extra)) t = t.withMinute(30);
                else if ("cuarto".equals(extra)) t = t.withMinute(15);
                return t;
            }
        }
        Matcher deLa = HORA_DE_LA.matcher(normal);
        if (deLa.find()) {
            LocalTime t = desdePalabras(Integer.parseInt(deLa.group(1)), deLa.group(2));
            if (t != null) return t;
        }
        // Momentos del día: se toma como "desde" esa hora.
        List<String> palabras = AsistenteTexto.palabras(normal);
        if (AsistenteTexto.contiene(palabras, "medio dia") || AsistenteTexto.contiene(palabras, "mediodia")) return LocalTime.of(12, 0);
        if (normal.matches(".*\\b(de|por|en|a) la manana\\b.*")) return LocalTime.of(7, 0);
        if (AsistenteTexto.contiene(palabras, "por la tarde") || AsistenteTexto.contiene(palabras, "en la tarde")) return LocalTime.of(13, 0);
        if (AsistenteTexto.contiene(palabras, "por la noche") || AsistenteTexto.contiene(palabras, "en la noche")) return LocalTime.of(18, 0);
        return null;
    }

    private static LocalTime desdePalabras(int hora, String momento) {
        if (hora > 23) return null;
        if ("manana".equals(momento)) {
            if (hora == 12) hora = 0;
        } else if (momento != null) {                       // tarde / noche
            if (hora < 12) hora += 12;
        } else if (hora >= 1 && hora <= 6) {                // "a las 3": en consulta médica se asume la tarde
            hora += 12;
        }
        return LocalTime.of(hora, 0);
    }
}
