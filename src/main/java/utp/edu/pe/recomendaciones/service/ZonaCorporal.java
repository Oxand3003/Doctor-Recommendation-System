package utp.edu.pe.recomendaciones.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Zonas del maniquí corporal del asistente de IA. Es una ORIENTACIÓN, no un diagnóstico.
 * <p>
 * Relaciona lo que escribe el paciente con la zona del cuerpo que se marca en el maniquí (tolerando errores de
 * ortografía con {@link AsistenteTexto}) y cada zona con la especialidad que suele atenderla. Los ids de zona
 * son los mismos que usa el maniquí del navegador ({@code orientacion-corporal.js}).
 * Sin dependencias externas: se puede probar con un simple {@code main}.
 */
public final class ZonaCorporal {
    private ZonaCorporal() { }

    /** Una zona del maniquí: id, nombre visible y raíces de las especialidades que la atienden. */
    public record Zona(String id, String etiqueta, List<String> raices) { }

    private record Termino(String texto, List<String> fonetico, int peso) { }

    private static final Map<String, Zona> ZONAS = new LinkedHashMap<>();
    private static final Map<String, List<Termino>> TERMINOS = new LinkedHashMap<>();
    private static final List<String> PALABRAS_DE_FECHA = new ArrayList<>();

    /** Raíces de especialidad conocidas (el nombre en la base puede o no llevar tilde). */
    private static final List<String> RAICES = List.of("cardiolog", "neumolog", "neurolog", "oftalmolog", "otorrino",
            "odontolog", "psiquiatr", "gastroenterolog", "nutri", "dermatolog", "endocrinolog", "urolog", "ginecolog",
            "traumatolog", "reumatolog", "pediatr", "medicina");

    private static final Map<String, String> DEFECTO = Map.ofEntries(
            Map.entry("cardiolog", "pecho"), Map.entry("neumolog", "pecho"), Map.entry("neurolog", "cabeza"),
            Map.entry("oftalmolog", "ojos"), Map.entry("otorrino", "oidos"), Map.entry("odontolog", "boca"),
            Map.entry("psiquiatr", "salud_mental"), Map.entry("gastroenterolog", "abdomen"),
            Map.entry("nutri", "abdomen"), Map.entry("dermatolog", "piel"), Map.entry("endocrinolog", "hormonas"),
            Map.entry("urolog", "pelvis"), Map.entry("ginecolog", "pelvis"));

    private static final Map<String, String> DESCRIPCION = Map.ofEntries(
            Map.entry("cardiolog", "el corazón, la presión arterial y la circulación"),
            Map.entry("neumolog", "los pulmones y las vías respiratorias"),
            Map.entry("neurolog", "el cerebro, los nervios y los dolores de cabeza"),
            Map.entry("oftalmolog", "los ojos y la visión"),
            Map.entry("otorrino", "los oídos, la nariz y la garganta"),
            Map.entry("odontolog", "los dientes, las encías y la boca"),
            Map.entry("psiquiatr", "la salud mental: ánimo, ansiedad y sueño"),
            Map.entry("gastroenterolog", "el estómago, el intestino, el hígado y la digestión"),
            Map.entry("nutri", "la alimentación y el control del peso"),
            Map.entry("dermatolog", "la piel, el cabello y las uñas"),
            Map.entry("endocrinolog", "las hormonas, la tiroides y la diabetes"),
            Map.entry("urolog", "las vías urinarias y el aparato reproductor masculino"),
            Map.entry("ginecolog", "la salud femenina, el ciclo menstrual y el embarazo"),
            Map.entry("traumatolog", "los huesos, las articulaciones, los músculos y los ligamentos"),
            Map.entry("reumatolog", "las enfermedades inflamatorias de las articulaciones"),
            Map.entry("pediatr", "la salud de bebés, niños y adolescentes"),
            Map.entry("medicina", "la evaluación de cuadros comunes y la derivación al especialista adecuado"));

    private static void zona(String id, String etiqueta, String raices, String... terminos) {
        ZONAS.put(id, new Zona(id, etiqueta, List.of(raices.split(","))));
        List<Termino> lista = new ArrayList<>();
        for (String t : terminos) {
            int sep = t.lastIndexOf('|');
            int peso = sep > 0 ? Integer.parseInt(t.substring(sep + 1)) : 2;
            String texto = sep > 0 ? t.substring(0, sep) : t;
            lista.add(new Termino(texto, AsistenteTexto.palabras(texto), peso));
        }
        TERMINOS.put(id, lista);
    }

    static {
        zona("cabeza", "Cabeza", "neurolog",
                "dolor de cabeza|3", "cefalea|3", "migrana|3", "jaqueca|3", "cabeza|2", "mareo|2", "mareos|2",
                "vertigo|2", "convulsion|3", "convulsiones|3", "epilepsia|3", "perdida de memoria|3", "memoria|2",
                "olvidos|2", "desmayo|2", "desmayos|2", "derrame|3", "temblor|2", "temblores|2");
        zona("ojos", "Ojos", "oftalmolog",
                "ojo|3", "ojos|3", "vista|3", "vision|3", "vista borrosa|3", "borrosa|3", "lentes|3", "miopia|3",
                "astigmatismo|3", "conjuntivitis|3", "ojo rojo|3", "lagrimeo|2", "cataratas|3", "glaucoma|3",
                "ver mal|3", "parpado|3", "legana|3");
        zona("oidos", "Oídos, nariz y garganta", "otorrino",
                "oido|3", "oidos|3", "dolor de oido|3", "garganta|3", "dolor de garganta|3", "garganta inflamada|3",
                "nariz|3", "sinusitis|3", "amigdalas|3", "zumbido|3", "sordera|3", "no escucho|3", "ronquera|2",
                "afonia|2", "ronquidos|2", "congestion nasal|3", "sangrado de nariz|3");
        zona("boca", "Boca y dientes", "odontolog",
                "diente|3", "dientes|3", "muela|3", "muelas|3", "dolor de muela|3", "encia|3", "encias|3",
                "sangrado de encias|3", "caries|3", "mandibula|2", "boca|2", "sarro|3", "ortodoncia|3", "colmillo|3");
        zona("salud_mental", "Salud mental", "psiquiatr",
                "ansiedad|3", "depresion|3", "estres|2", "animo|2", "triste|2", "tristeza|3", "panico|3",
                "ataques de panico|3", "angustia|3", "nervios|2", "insomnio|3", "no puedo dormir|3", "bipolar|3",
                "salud mental|3", "no tengo ganas|2", "irritable|1");
        zona("pecho", "Pecho", "cardiolog,neumolog",
                "pecho|3", "dolor de pecho|3", "corazon|3", "cardiaco|3", "palpitaciones|3", "taquicardia|3",
                "arritmia|3", "presion alta|3", "hipertension|3", "infarto|3", "pecho apretado|3", "pulmon|3",
                "pulmones|3", "asma|3", "bronquitis|3", "neumonia|3", "falta de aire|3", "no puedo respirar|3",
                "dificultad para respirar|3", "tos con flema|3", "tos cronica|3", "silbido al respirar|3", "tos|2");
        zona("abdomen", "Abdomen", "gastroenterolog,nutri",
                "estomago|3", "dolor de estomago|3", "barriga|3", "me duele la barriga|3", "panza|3", "abdomen|3",
                "dolor abdominal|3", "gastritis|3", "acidez|3", "reflujo|3", "diarrea|3", "estrenimiento|3",
                "nauseas|2", "vomito|2", "vomitos|2", "colitis|3", "higado|3", "intestino|3", "digestion|3",
                "colon|3", "ulcera|3", "vesicula|3", "gases|2", "indigestion|3", "empacho|2", "hemorroides|2");
        zona("espalda", "Espalda, cuello y cadera", "traumatolog,reumatolog",
                "espalda|3", "columna|3", "lumbar|3", "cintura|2", "cadera|3", "cuello|2", "dolor de espalda|3",
                "ciatica|3", "escoliosis|3");
        zona("piel", "Piel", "dermatolog",
                "piel|3", "acne|3", "granos|3", "espinillas|3", "sarpullido|3", "ronchas|3", "roncha|3", "lunar|3",
                "lunares|3", "mancha en la piel|3", "manchas|2", "picazon|2", "comezon|2", "caspa|2",
                "caida de cabello|3", "caida del cabello|3", "alopecia|3", "hongos|2", "verruga|3", "verrugas|3",
                "eczema|3", "dermatitis|3", "urticaria|3", "psoriasis|3", "rascarse|2");
        zona("hormonas", "Hormonas y metabolismo", "endocrinolog,nutri",
                "diabetes|3", "azucar alta|3", "glucosa|3", "tiroides|3", "hipotiroidismo|3", "hipertiroidismo|3",
                "hormonas|3", "insulina|3", "colesterol|2", "trigliceridos|2", "obesidad|2", "metabolismo|2",
                "sed excesiva|2");
        zona("brazo", "Hombro, brazo y mano", "traumatolog,reumatolog",
                "hombro|3", "codo|3", "muneca|3", "brazo|3", "brazos|3", "mano|3", "manos|3", "dedos|2",
                "tendinitis|2", "tunel carpiano|3", "hormigueo en la mano|3");
        zona("pelvis", "Zona pélvica", "ginecolog,urolog",
                "orina|3", "orinar|3", "dolor al orinar|3", "cistitis|3", "vejiga|3", "prostata|3", "rinon|3",
                "rinones|3", "calculos|3", "piedras en el rinon|3", "testiculo|3", "testiculos|3", "regla|3",
                "menstruacion|3", "retraso menstrual|3", "flujo vaginal|3", "flujo|2", "vagina|3", "utero|3",
                "ovarios|3", "dolor pelvico|3", "embarazo|3", "embarazada|3", "papanicolau|3", "menopausia|3",
                "anticonceptivo|3", "anticonceptivos|3", "sangrado vaginal|3", "parto|3", "cesarea|3");
        zona("rodilla", "Rodilla y pierna", "traumatolog,reumatolog",
                "rodilla|3", "rodillas|3", "menisco|3", "ligamento|3", "pierna|3", "piernas|3", "muslo|3",
                "dolor de pierna|3", "hinchazon de piernas|2", "pantorrilla|3", "varices|2");
        zona("tobillo", "Pie y tobillo", "traumatolog,reumatolog",
                "tobillo|3", "pie|3", "pies|3", "talon|3", "planta del pie|3", "juanete|3", "esguince|2",
                "me torci|2", "torcedura|2", "dedos del pie|3");
        zona("general", "Cuerpo en general", "medicina,pediatr,reumatolog,traumatolog",
                "fiebre|2", "gripe|2", "gripa|2", "resfrio|2", "resfriado|2", "malestar general|3", "malestar|2",
                "cansancio|2", "escalofrios|2", "covid|2", "chequeo|3", "me siento mal|2", "no me siento bien|2",
                "dolor de cuerpo|3", "cuerpo cortado|2", "mi hijo|3", "mi hija|3", "mi bebe|3", "bebe|3", "nino|2",
                "nina|2");

        for (String t : new String[]{"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
                "setiembre", "septiembre", "octubre", "noviembre", "diciembre", "lunes", "martes", "miercoles",
                "jueves", "viernes", "sabado", "domingo", "manana", "hoy"}) {
            PALABRAS_DE_FECHA.add(AsistenteTexto.fonetica(t));
        }
    }

    // ------------------------------------------------------------------ consulta

    public static boolean existe(String id) {
        return id != null && ZONAS.containsKey(id);
    }

    public static String etiqueta(String id) {
        Zona z = id == null ? null : ZONAS.get(id);
        return z == null ? "Cuerpo en general" : z.etiqueta();
    }

    public static List<String> ids() {
        return List.copyOf(ZONAS.keySet());
    }

    /** Raíz de la especialidad (\"cardiolog\" para \"Cardiología\"); null si no se reconoce. */
    public static String raiz(String especialidad) {
        String normal = AsistenteTexto.normalizar(especialidad);
        if (normal.isEmpty()) return null;
        for (String r : RAICES) if (normal.startsWith(r)) return r;
        return null;
    }

    /** Frase corta de lo que atiende la especialidad (\"los huesos, las articulaciones...\"), o null. */
    public static String descripcion(String especialidad) {
        String r = raiz(especialidad);
        return r == null ? null : DESCRIPCION.get(r);
    }

    /**
     * Zona del maniquí que mejor corresponde a lo escrito. Si ya hay una especialidad elegida se prefieren las
     * zonas que ella atiende (salvo medicina general/pediatría, que aceptan cualquiera); sin pistas en el texto se
     * usa la zona típica de la especialidad y, en último caso, "general".
     */
    public static String detectar(String texto, String especialidad) {
        List<String> palabras = limpiar(texto);
        String raiz = raiz(especialidad);
        boolean abierta = raiz == null || raiz.equals("medicina") || raiz.equals("pediatr");
        String mejor = null;
        int mejorPuntaje = 0;
        for (Zona z : ZONAS.values()) {
            if (!abierta && !z.raices().contains(raiz)) continue;
            int p = puntaje(palabras, z.id());
            if (p > mejorPuntaje) {
                mejorPuntaje = p;
                mejor = z.id();
            }
        }
        if (mejor != null) return mejor;
        return raiz == null ? "general" : DEFECTO.getOrDefault(raiz, "general");
    }

    /** Lo que el paciente mencionó (términos de peso 2 o más) que corresponde a la zona. */
    public static List<String> motivos(String texto, String zonaId) {
        List<String> palabras = limpiar(texto);
        List<String> out = new ArrayList<>();
        for (Termino t : TERMINOS.getOrDefault(zonaId, List.of())) {
            if (t.peso() < 2 || AsistenteTexto.buscar(palabras, t.fonetico()) < 0) continue;
            String clave = AsistenteTexto.fonetica(AsistenteTexto.normalizar(t.texto()));
            // "rodilla" y "rodillas" cuentan como una sola mención.
            boolean repetido = out.stream().anyMatch(o ->
                    AsistenteTexto.parecida(AsistenteTexto.fonetica(AsistenteTexto.normalizar(o)), clave));
            if (!repetido) out.add(t.texto());
        }
        return out;
    }

    /**
     * Especialidad (nombre EXACTO de la base) que atiende la zona tocada en el maniquí. En la zona pélvica
     * depende del sexo ("F" = ginecología primero, "M" = urología). Si no hay médicos de esa especialidad se
     * recurre a medicina; null si tampoco existe.
     */
    public static String especialidadParaZona(String zonaId, String sexo, List<String> especialidadesBase) {
        List<String> raices = switch (zonaId == null ? "" : zonaId) {
            case "cabeza" -> List.of("neurolog");
            case "ojos" -> List.of("oftalmolog");
            case "oidos" -> List.of("otorrino");
            case "boca" -> List.of("odontolog");
            case "salud_mental" -> List.of("psiquiatr");
            case "pecho" -> List.of("cardiolog", "neumolog");
            case "abdomen" -> List.of("gastroenterolog");
            case "piel" -> List.of("dermatolog");
            case "hormonas" -> List.of("endocrinolog");
            case "espalda", "brazo", "rodilla", "tobillo" -> List.of("traumatolog", "reumatolog");
            case "pelvis" -> "F".equals(sexo) ? List.of("ginecolog", "urolog") : List.of("urolog");
            default -> List.of("medicina");
        };
        for (String r : raices) {
            for (String nombre : especialidadesBase) {
                if (AsistenteTexto.normalizar(nombre).startsWith(r)) return nombre;
            }
        }
        for (String nombre : especialidadesBase) {
            if (AsistenteTexto.normalizar(nombre).startsWith("medicina general")) return nombre;
        }
        for (String nombre : especialidadesBase) {
            if (AsistenteTexto.normalizar(nombre).startsWith("medicina")) return nombre;
        }
        return null;
    }

    /** "F" (mujer) o "M" (hombre); null si el valor no es válido. */
    public static String normalizarSexo(String valor) {
        String v = AsistenteTexto.normalizar(valor).toLowerCase(Locale.ROOT);
        return switch (v) {
            case "mujer", "femenino", "f", "female", "soy mujer" -> "F";
            case "hombre", "masculino", "m", "varon", "male", "soy hombre" -> "M";
            default -> null;
        };
    }

    // ------------------------------------------------------------------ internos

    private static List<String> limpiar(String texto) {
        List<String> palabras = AsistenteTexto.palabras(texto);
        palabras.removeAll(PALABRAS_DE_FECHA);   // "marzo" no debe parecerse a "mareo"
        return palabras;
    }

    private static int puntaje(List<String> palabras, String zonaId) {
        if (palabras.isEmpty()) return 0;
        int total = 0;
        for (Termino t : TERMINOS.getOrDefault(zonaId, List.of())) {
            if (AsistenteTexto.buscar(palabras, t.fonetico()) >= 0) total += t.peso();
        }
        return total;
    }
}
