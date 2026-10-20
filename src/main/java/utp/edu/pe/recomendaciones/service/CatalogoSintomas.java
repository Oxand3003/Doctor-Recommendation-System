package utp.edu.pe.recomendaciones.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo de síntomas/palabras -> especialidad. Es una ORIENTACIÓN para buscar médico, no un diagnóstico.
 * <p>
 * Las claves son raíces del nombre de la especialidad ("cardiolog" sirve para "Cardiología"), de modo que
 * funciona aunque en la base el nombre tenga o no tilde. Solo se sugieren especialidades que existan en la base.
 * Para añadir un síntoma basta una línea en {@link #CATALOGO}; el peso (1-3) indica qué tan específico es.
 */
public final class CatalogoSintomas {
    private CatalogoSintomas() { }

    /** Una sugerencia: nombre EXACTO de la especialidad en la base, puntaje y lo que el paciente mencionó. */
    public record Sugerencia(String especialidad, int puntaje, List<String> motivos) { }

    private record Termino(String texto, List<String> fonetico, int peso) { }

    private static final Map<String, List<Termino>> CATALOGO = new LinkedHashMap<>();
    private static final List<Termino> URGENCIAS = new ArrayList<>();
    private static final List<String> PALABRAS_DE_FECHA = new ArrayList<>();

    private static void def(String raiz, String... terminos) {
        List<Termino> lista = CATALOGO.computeIfAbsent(raiz, k -> new ArrayList<>());
        for (String t : terminos) {
            int sep = t.lastIndexOf('|');
            int peso = sep > 0 ? Integer.parseInt(t.substring(sep + 1)) : 2;
            String texto = sep > 0 ? t.substring(0, sep) : t;
            lista.add(new Termino(texto, AsistenteTexto.palabras(texto), peso));
        }
    }

    static {
        def("cardiolog", "corazon|3", "cardiaco|3", "cardiologo|3", "palpitaciones|3", "taquicardia|3", "arritmia|3",
                "dolor de pecho|3", "presion alta|3", "hipertension|3", "presion arterial|2", "infarto|3", "soplo|3",
                "latidos|2", "pecho apretado|3", "se me acelera el corazon|3", "hinchazon de piernas|1", "tension alta|2");
        def("pediatr", "pediatra|3", "mi hijo|3", "mi hija|3", "mi bebe|3", "bebe|3", "recien nacido|3", "nino|2",
                "nina|2", "mi nino|3", "mi nina|3", "vacunas|2", "lactancia|2", "crecimiento|1", "llora mucho|2",
                "adolescente|2", "menor de edad|2");
        def("dermatolog", "piel|3", "acne|3", "granos|3", "espinillas|3", "sarpullido|3", "ronchas|3", "lunar|3",
                "lunares|3", "mancha en la piel|3", "manchas|2", "picazon|2", "comezon|2", "caspa|2", "caida de cabello|3",
                "caida del cabello|3", "alopecia|3", "hongos|2", "verruga|3", "verrugas|3", "eczema|3", "dermatitis|3",
                "urticaria|3", "psoriasis|3", "rascarse|2", "cabello|1", "dermatologo|3", "roncha|3", "quemadura|1");
        def("ginecolog", "embarazo|3", "embarazada|3", "regla|3", "menstruacion|3", "periodo|2", "flujo vaginal|3",
                "flujo|2", "papanicolau|3", "menopausia|3", "ovarios|3", "utero|3", "vagina|3", "anticonceptivo|3",
                "anticonceptivos|3", "dolor pelvico|3", "quiste en el ovario|3", "control prenatal|3", "ginecologo|3",
                "ginecologa|3", "sangrado vaginal|3", "retraso menstrual|3", "senos|2", "mamas|2", "cesarea|3", "parto|3");
        def("endocrinolog", "diabetes|3", "azucar alta|3", "glucosa|3", "tiroides|3", "hipotiroidismo|3",
                "hipertiroidismo|3", "obesidad|2", "colesterol|2", "trigliceridos|2", "hormonas|3", "insulina|3",
                "metabolismo|2", "sed excesiva|2", "subir de peso|1", "bajar de peso|1", "endocrinologo|3");
        def("neurolog", "dolor de cabeza|3", "cefalea|3", "migrana|3", "jaqueca|3", "mareos|2", "mareo|2", "vertigo|2",
                "convulsiones|3", "convulsion|3", "epilepsia|3", "hormigueo|2", "adormecimiento|2", "entumecimiento|2",
                "temblor|2", "temblores|2", "perdida de memoria|3", "olvidos|2", "memoria|2", "parkinson|3", "derrame|3",
                "neurologo|3", "cabeza|1", "desmayo|1", "desmayos|1", "no puedo dormir|1");
        def("traumatolog", "fractura|3", "esguince|3", "golpe|2", "rodilla|3", "tobillo|3", "hombro|3", "codo|2",
                "muneca|2", "espalda|3", "columna|3", "lumbar|3", "cintura|2", "cadera|3", "hueso|3", "huesos|3",
                "articulacion|3", "articulaciones|3", "torcedura|3", "luxacion|3", "lesion|2", "tendinitis|3",
                "menisco|3", "ligamento|3", "ortopedia|3", "traumatologo|3", "dolor de pierna|2", "talon|2", "cuello|1",
                "me torci|3", "me cai|1", "dolor muscular|2", "musculo|1");
        def("psiquiatr", "ansiedad|3", "depresion|3", "estres|2", "animo|2", "triste|2", "tristeza|3", "panico|3",
                "angustia|3", "bipolar|3", "psiquiatra|3", "psicologo|2", "obsesivo|2", "salud mental|3", "nervios|2",
                "insomnio|2", "no puedo dormir|2", "sueno|1", "irritable|1", "ataques de panico|3", "no tengo ganas|2");
        def("gastroenterolog", "estomago|3", "dolor de estomago|3", "gastritis|3", "acidez|3", "reflujo|3", "diarrea|3",
                "estrenimiento|3", "nauseas|2", "vomito|2", "vomitos|2", "colitis|3", "higado|3", "intestino|3",
                "digestion|3", "barriga|2", "abdomen|2", "dolor abdominal|3", "colon|3", "ulcera|3", "hemorroides|2",
                "vesicula|3", "panza|2", "indigestion|3", "empacho|2", "gases|2", "heces|2", "gastroenterologo|3",
                "me duele la barriga|3", "no puedo ir al bano|2");
        def("oftalmolog", "ojo|3", "ojos|3", "vista|3", "vision|3", "vista borrosa|3", "borrosa|3", "lentes|3",
                "miopia|3", "astigmatismo|3", "conjuntivitis|3", "ojo rojo|3", "lagrimeo|2", "cataratas|3", "glaucoma|3",
                "ver mal|3", "oftalmologo|3", "parpado|3", "legana|3");
        def("otorrino", "oido|3", "oidos|3", "garganta|3", "nariz|3", "sinusitis|3", "amigdalas|3", "ronquidos|2",
                "sordera|3", "zumbido|3", "ronquera|2", "afonia|2", "otorrino|3", "sangrado de nariz|3", "dolor de oido|3",
                "congestion nasal|3", "no escucho|3", "garganta inflamada|3");
        def("urolog", "orina|3", "orinar|3", "prostata|3", "rinon|3", "rinones|3", "cistitis|3", "urologo|3",
                "dolor al orinar|3", "calculos|3", "piedras en el rinon|3", "vejiga|3", "testiculo|3", "testiculos|3");
        def("neumolog", "asma|3", "pulmon|3", "pulmones|3", "bronquitis|3", "neumonia|3", "falta de aire|2",
                "tos con flema|3", "tos cronica|3", "silbido al respirar|3", "dificultad para respirar|2", "neumologo|3");
        def("reumatolog", "artritis|3", "artrosis|3", "reuma|3", "reumatismo|3", "lupus|3", "gota|3",
                "dolor en las articulaciones|3", "reumatologo|3", "articulaciones hinchadas|3");
        def("nutri", "nutricion|3", "nutricionista|3", "dieta|3", "adelgazar|3", "bajar de peso|2", "alimentacion|2",
                "sobrepeso|3", "desnutricion|3");
        def("odontolog", "diente|3", "dientes|3", "muela|3", "muelas|3", "encia|3", "encias|3", "caries|3", "dentista|3",
                "mandibula|2", "ortodoncia|3", "brackets|3", "limpieza dental|3", "sarro|3", "dental|3", "odontologo|3",
                "boca|1", "colmillo|3", "sangrado de encias|3", "dolor de muela|3");
        // Cuadros generales: medicina general e interna (la interna con menor peso).
        def("medicina general", "fiebre|2", "gripe|2", "gripa|2", "resfrio|2", "resfriado|2", "tos|2", "malestar general|3",
                "malestar|2", "cansancio|1", "chequeo|3", "control|1", "dolor de garganta|1", "infeccion|1", "cuerpo cortado|2",
                "me siento mal|2", "dolor de cuerpo|2", "covid|2", "medico general|3", "consulta general|3", "examen medico|2",
                "no me siento bien|2", "escalofrios|2", "dolor de cabeza y fiebre|1");
        def("medicina interna", "adulto|1", "enfermedad cronica|3", "chequeo|2", "malestar general|2", "fiebre|1",
                "cansancio|2", "internista|3", "medicina interna|3", "hipertension|1", "diabetes|1", "analisis|1");

        for (String t : new String[]{"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
                "setiembre", "septiembre", "octubre", "noviembre", "diciembre", "lunes", "martes", "miercoles", "jueves",
                "viernes", "sabado", "domingo", "manana", "hoy"}) {
            PALABRAS_DE_FECHA.add(AsistenteTexto.fonetica(t));
        }
        for (String t : new String[]{"no puedo respirar", "dificultad para respirar", "me falta el aire", "dolor de pecho",
                "dolor fuerte de pecho", "perdi el conocimiento", "me desmaye", "convulsion", "convulsiones",
                "sangrado abundante", "hemorragia", "quiero morir", "me quiero matar", "suicidio", "paralisis",
                "no puedo mover", "cara caida", "envenenamiento", "infarto", "derrame cerebral"}) {
            URGENCIAS.add(new Termino(t, AsistenteTexto.palabras(t), 3));
        }
    }

    /**
     * Especialidades (de las que existen en la base) que mejor encajan con lo que escribió el paciente,
     * de mayor a menor puntaje. Vacío si no se reconoce ningún síntoma.
     */
    public static List<Sugerencia> sugerir(String texto, List<String> especialidadesBase) {
        List<String> palabras = AsistenteTexto.palabras(texto);
        palabras.removeAll(PALABRAS_DE_FECHA);   // "marzo" no debe parecerse a "mareo"
        List<Sugerencia> out = new ArrayList<>();
        if (palabras.isEmpty()) return out;
        for (String nombre : especialidadesBase) {
            String normal = AsistenteTexto.normalizar(nombre);
            int puntaje = 0;
            List<String> motivos = new ArrayList<>();
            for (Map.Entry<String, List<Termino>> entrada : CATALOGO.entrySet()) {
                if (!normal.startsWith(entrada.getKey())) continue;
                for (Termino t : entrada.getValue()) {
                    if (AsistenteTexto.buscar(palabras, t.fonetico()) >= 0) {
                        puntaje += t.peso();
                        if (t.peso() >= 2 && !motivos.contains(t.texto())) motivos.add(t.texto());
                    }
                }
            }
            if (puntaje > 0) out.add(new Sugerencia(nombre, puntaje, motivos));
        }
        out.sort(Comparator.comparingInt(Sugerencia::puntaje).reversed());
        return out;
    }

    /** ¿Lo descrito podría ser una urgencia? (solo para avisar que acuda a emergencias). */
    public static boolean pareceUrgente(String texto) {
        List<String> palabras = AsistenteTexto.palabras(texto);
        for (Termino t : URGENCIAS) {
            if (AsistenteTexto.buscar(palabras, t.fonetico()) >= 0) return true;
        }
        return false;
    }

    /**
     * Especialidad escrita por su nombre o su profesional ("cardiologo", "pediatria", "dermatologa"),
     * tolerando errores. Devuelve el nombre exacto de la base o null.
     */
    public static String porNombre(String texto, List<String> especialidadesBase) {
        List<String> palabras = AsistenteTexto.palabras(texto);
        for (String nombre : especialidadesBase) {
            String normal = AsistenteTexto.normalizar(nombre);
            // Nombre completo ("medicina general") o primera palabra raíz ("cardiolog" de "cardiologia").
            if (AsistenteTexto.buscar(palabras, AsistenteTexto.palabras(normal)) >= 0) return nombre;
            for (String raiz : CATALOGO.keySet()) {
                if (!normal.startsWith(raiz) || raiz.contains(" ")) continue;
                String raizFon = AsistenteTexto.fonetica(raiz);
                for (String p : palabras) if (p.length() >= 5 && AsistenteTexto.empiezaCon(p, raizFon)) return nombre;
            }
        }
        return null;
    }
}
