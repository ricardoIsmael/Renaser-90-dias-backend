package com.renaser.os.onboarding.domain.model.diagnostico;

import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.onboarding.domain.model.diagnostico.LecturaDeImportacion.AvisoDeImportacion;
import com.renaser.os.onboarding.domain.model.diagnostico.LecturaDeImportacion.ErrorDeImportacion;
import com.renaser.os.onboarding.domain.model.diagnostico.LecturaDeImportacion.PreguntaImportada;
import com.renaser.os.shared.domain.Formacion;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * La hoja de cálculo de la que Administración carga preguntas (spec 026, Q4-A): lectura con errores por fila, plantilla y exportación. Sin Spring ni librerías.
 *
 * <p>Se lee texto (CSV con coma o punto y coma, o las celdas copiadas de Excel o Google Sheets, que salen con tabuladores): UTF-8, campos entre comillas, saltos
 * de línea dentro de una celda. Todo o nada: quien aplica el resultado no guarda nada si hay un solo error.
 */
public final class PlantillaDeImportacion {

    public static final List<String> COLUMNAS = List.of("Sección", "Clave", "Pregunta", "Ayuda", "Tipo", "Obligatoria",
            "Opciones o filas (separadas por |)", "Máximo de opciones", "Se muestra solo si", "Alimenta", "Se muestra en");

    static final int SECCION = 0, CLAVE = 1, PREGUNTA = 2, AYUDA = 3, TIPO = 4, OBLIGATORIA = 5, OPCIONES = 6, MAXIMO = 7,
            CONDICION = 8, ALIMENTA = 9, FORMACIONES = 10;
    private static final List<Integer> OBLIGATORIAS = List.of(SECCION, PREGUNTA, TIPO);

    static final Map<TipoPreguntaOnboarding, String> NOMBRE_DEL_TIPO = new EnumMap<>(TipoPreguntaOnboarding.class);

    static {
        NOMBRE_DEL_TIPO.put(TipoPreguntaOnboarding.TEXTO, "Texto corto");
        NOMBRE_DEL_TIPO.put(TipoPreguntaOnboarding.AREA_TEXTO, "Párrafo");
        NOMBRE_DEL_TIPO.put(TipoPreguntaOnboarding.NUMERO, "Número");
        NOMBRE_DEL_TIPO.put(TipoPreguntaOnboarding.SELECCION_UNICA, "Opción única");
        NOMBRE_DEL_TIPO.put(TipoPreguntaOnboarding.SELECCION_MULTIPLE, "Casillas");
        NOMBRE_DEL_TIPO.put(TipoPreguntaOnboarding.ESCALA, "Escala");
        NOMBRE_DEL_TIPO.put(TipoPreguntaOnboarding.CUADRICULA, "Cuadrícula de escala");
        NOMBRE_DEL_TIPO.put(TipoPreguntaOnboarding.FECHA, "Fecha");
        NOMBRE_DEL_TIPO.put(TipoPreguntaOnboarding.CASILLA, "Consentimiento");
    }

    private PlantillaDeImportacion() {
    }

    /** Los nombres de los tipos tal como se escriben en la plantilla. */
    public static String nombreDelTipo(TipoPreguntaOnboarding tipo) {
        return NOMBRE_DEL_TIPO.get(tipo);
    }

    /**
     * @param existentes las preguntas que ya están en el diagnóstico (clave -> sus opciones) y a las que una fila nueva puede apuntar en «Se muestra solo si»;
     *                   vacío cuando el archivo reemplaza la ficha
     */
    public static LecturaDeImportacion leer(String texto, Map<String, List<OpcionDelDiagnostico>> existentes) {
        List<ErrorDeImportacion> errores = new ArrayList<>();
        List<AvisoDeImportacion> avisos = new ArrayList<>();
        List<List<String>> registros = HojaDeCalculo.registros(texto == null ? "" : texto);
        if (registros.isEmpty()) {
            errores.add(new ErrorDeImportacion(1, null, "El archivo está vacío"));
            return new LecturaDeImportacion(List.of(), errores, avisos);
        }
        int[] columnas = columnas(registros.get(0), errores);
        if (!errores.isEmpty()) {
            return new LecturaDeImportacion(List.of(), errores, avisos);
        }
        return new LectorDeFilas(columnas, registros, existentes, errores, avisos).resolver();
    }

    // ---------------------------------------------------------------------------------------------------- la hoja

    private static int[] columnas(List<String> cabecera, List<ErrorDeImportacion> errores) {
        int[] posicion = new int[COLUMNAS.size()];
        Arrays.fill(posicion, -1);
        for (int i = 0; i < cabecera.size(); i++) {
            String nombre = plano(cabecera.get(i));
            if (nombre.isEmpty()) {
                continue;
            }
            int columna = indiceDeColumna(nombre);
            if (columna < 0) {
                errores.add(new ErrorDeImportacion(1, cabecera.get(i).trim(), "La columna «" + cabecera.get(i).trim() + "» no existe en la plantilla"));
            } else if (posicion[columna] < 0) {
                posicion[columna] = i;
            }
        }
        for (int obligatoria : OBLIGATORIAS) {
            if (posicion[obligatoria] < 0) {
                errores.add(new ErrorDeImportacion(1, COLUMNAS.get(obligatoria), "Falta la columna «" + COLUMNAS.get(obligatoria) + "»"));
            }
        }
        return posicion;
    }

    private static int indiceDeColumna(String nombrePlano) {
        for (int i = 0; i < COLUMNAS.size(); i++) {
            String esperada = plano(COLUMNAS.get(i));
            if (nombrePlano.equals(esperada) || (i == OPCIONES && nombrePlano.startsWith("opciones"))) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------------------------- plantilla y exportación

    /** La plantilla vacía: la cabecera y dos filas de ejemplo. */
    public static String plantilla() {
        StringBuilder sb = new StringBuilder(fila(COLUMNAS));
        sb.append(fila(List.of("Conociéndote", "p1", "¿Cuál es tu nombre completo?", "", "Texto corto", "Sí", "", "", "", "", "")));
        sb.append(fila(List.of("Tu vida hoy", "p2", "¿Tienes un negocio?", "", "Opción única", "Sí", "Sí. | No.", "", "", "", "")));
        sb.append(fila(List.of("Tu negocio", "p2a", "¿A qué se dedica tu negocio?", "", "Texto corto", "No", "", "", "p2=Sí.", "", "")));
        return sb.toString();
    }

    /** El diagnóstico tal como está (sin las retiradas) en el formato de la plantilla, para editarlo fuera y volver a importarlo. */
    public static String exportar(FichaDelDiagnostico ficha) {
        StringBuilder sb = new StringBuilder(fila(COLUMNAS));
        Map<Integer, String> clavePorId = new HashMap<>();
        ficha.preguntas().forEach(p -> clavePorId.put(p.id(), p.clave()));
        for (SeccionDelDiagnostico s : ficha.secciones()) {
            for (PreguntaDelDiagnostico p : s.preguntas()) {
                sb.append(fila(List.of(s.titulo(), p.clave(), p.texto(), p.ayuda() == null ? "" : p.ayuda().replace("\n", " · "),
                        NOMBRE_DEL_TIPO.get(p.tipo()), p.requerida() ? "Sí" : "No", opcionesComoTexto(p),
                        p.maximoOpciones() == null ? "" : String.valueOf(p.maximoOpciones()),
                        p.condicion() == null ? "" : clavePorId.getOrDefault(p.condicion().preguntaId(), "") + "=" + p.condicion().valor(),
                        p.alimenta() == null ? "" : p.alimenta().etiqueta(),
                        String.join(" | ", p.formaciones().stream().map(f -> nombreDeLaFormacion(f)).sorted().toList()))));
            }
        }
        return sb.toString();
    }

    private static String opcionesComoTexto(PreguntaDelDiagnostico p) {
        if (p.tipo() == TipoPreguntaOnboarding.ESCALA) {
            return p.escala().min() + "-" + p.escala().max();
        }
        boolean conCodigo = p.alimenta() != null && p.alimenta().valoresPermitidos().isPresent();
        return String.join(" | ", p.opciones().stream().map(o -> conCodigo ? o.etiqueta() + " = " + o.valor() : o.etiqueta()).toList());
    }

    private static String nombreDeLaFormacion(Formacion f) {
        return switch (f) {
            case ESCUELA -> "Escuela";
            case DIPLOMADO -> "Diplomado";
            case CERTIFICACION -> "Certificación";
        };
    }

    private static String fila(List<String> celdas) {
        return String.join(",", celdas.stream().map(PlantillaDeImportacion::celda).toList()) + "\r\n";
    }

    private static String celda(String valor) {
        if (valor.contains(",") || valor.contains("\"") || valor.contains("\n") || valor.contains("\r")) {
            return "\"" + valor.replace("\"", "\"\"") + "\"";
        }
        return valor;
    }

    /** Para comparar «Sí» con «Sí.»: sin tildes, sin mayúsculas y sin el punto o los signos del final. */
    static String sinPuntuacion(String s) {
        return plano(s).replaceAll("[\\p{Punct}\\s]+$", "");
    }

    static String plano(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim()
                .replaceAll("\\s+", " ");
    }
}
