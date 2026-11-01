package com.renaser.os.onboarding.domain.model.diagnostico;

import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.shared.domain.Formacion;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Una pregunta del diagnóstico (spec 026), con todo lo que Administración puede configurarle. Valida sus propias invariantes al construirse: lo que no
 * cuadra (un tipo sin opciones, un máximo mayor que las opciones, un destino que no va con el tipo) es una {@link IllegalArgumentException} con el motivo,
 * que el servidor devuelve como 400. Lo que depende de OTRAS preguntas (que la condición apunte a una anterior) lo valida {@link FichaDelDiagnostico}.
 *
 * <p>{@code id == 0} es una pregunta que todavía no se guardó.
 */
public record PreguntaDelDiagnostico(int id, String clave, int seccionId, int orden, String texto, String ayuda,
                                     TipoPreguntaOnboarding tipo, boolean requerida, Integer maximoOpciones,
                                     List<OpcionDelDiagnostico> opciones, EscalaDelDiagnostico escala,
                                     CondicionDeVisibilidad condicion, DestinoDeRespuesta alimenta,
                                     Set<Formacion> formaciones, String precarga, Instant retiradaEn) {

    public static final int MAX_TEXTO = 600;
    public static final int MAX_AYUDA = 6000;
    /** El tope de un objetivo de 90 días ({@code RocaMaestra.MAX_OBJETIVO}); lo exige el diagnóstico desde que se escribe, sin recortes silenciosos. */
    public static final int MAX_OBJETIVO = 500;
    public static final int MAX_RESPUESTA_LIBRE = 4000;
    public static final String PRECARGA_NOMBRE = "NOMBRE_COMPLETO";

    private static final Set<TipoPreguntaOnboarding> TIPOS_PERMITIDOS = EnumSet.of(
            TipoPreguntaOnboarding.TEXTO, TipoPreguntaOnboarding.AREA_TEXTO, TipoPreguntaOnboarding.NUMERO,
            TipoPreguntaOnboarding.SELECCION_UNICA, TipoPreguntaOnboarding.SELECCION_MULTIPLE,
            TipoPreguntaOnboarding.ESCALA, TipoPreguntaOnboarding.CUADRICULA, TipoPreguntaOnboarding.FECHA,
            TipoPreguntaOnboarding.CASILLA);

    public PreguntaDelDiagnostico {
        Objects.requireNonNull(tipo, "El tipo de la pregunta es obligatorio");
        if (!TIPOS_PERMITIDOS.contains(tipo)) {
            throw new IllegalArgumentException("El diagnóstico no admite preguntas de tipo " + tipo);
        }
        texto = limpio(texto, MAX_TEXTO, "El texto de la pregunta");
        ayuda = ayuda == null || ayuda.isBlank() ? null : recortarLargo(ayuda.trim(), MAX_AYUDA, "La ayuda");
        opciones = List.copyOf(opciones == null ? List.of() : opciones);
        formaciones = formaciones == null ? Set.of() : Set.copyOf(formaciones);
        precarga = precarga == null || precarga.isBlank() ? null : precarga.trim();
        validarOpciones(tipo, opciones);
        maximoOpciones = validarMaximo(tipo, maximoOpciones, opciones.size());
        escala = validarEscala(tipo, escala);
        validarDestino(tipo, alimenta, opciones);
        if (precarga != null && (tipo != TipoPreguntaOnboarding.TEXTO || !PRECARGA_NOMBRE.equals(precarga))) {
            throw new IllegalArgumentException("Solo una pregunta de texto corto puede precargar el nombre");
        }
    }

    /** Una pregunta nueva, sin guardar. */
    public static PreguntaDelDiagnostico nueva(int seccionId, int orden, String clave, String texto, String ayuda,
                                               TipoPreguntaOnboarding tipo, boolean requerida, Integer maximoOpciones,
                                               List<OpcionDelDiagnostico> opciones, EscalaDelDiagnostico escala,
                                               CondicionDeVisibilidad condicion, DestinoDeRespuesta alimenta,
                                               Set<Formacion> formaciones, String precarga) {
        return new PreguntaDelDiagnostico(0, clave, seccionId, orden, texto, ayuda, tipo, requerida, maximoOpciones,
                opciones, escala, condicion, alimenta, formaciones, precarga, null);
    }

    public boolean estaRetirada() {
        return retiradaEn != null;
    }

    /**
     * La usa otra función de la app y no se puede quitar ni cambiar de tipo: el trato de la persona (SER y los avisos) y el consentimiento (sin él no se
     * termina el diagnóstico). Las que alimentan un objetivo SÍ se pueden quitar, con aviso (US5.9).
     */
    public boolean esProtegida() {
        return alimenta == DestinoDeRespuesta.TRATO_DE_LA_PERSONA || tipo == TipoPreguntaOnboarding.CASILLA;
    }

    /** {@code null} = quien mira ve todas las formaciones (Administración). */
    public boolean seMuestraEn(Formacion formacion) {
        return formacion == null || formaciones.isEmpty() || formaciones.contains(formacion);
    }

    /** El tope de caracteres de lo que se escribe; {@code null} si no tiene uno propio (el general de {@link #MAX_RESPUESTA_LIBRE} aplica igual). */
    public Integer largoMaximo() {
        return alimenta != null && (alimenta.esObjetivo() || alimenta == DestinoDeRespuesta.PRIMERA_ACCION) ? MAX_OBJETIVO : null;
    }

    public boolean tieneOpcion(String valor) {
        return opciones.stream().anyMatch(o -> o.valor().equals(valor));
    }

    public PreguntaDelDiagnostico enSeccion(int nuevaSeccionId, int nuevoOrden) {
        return new PreguntaDelDiagnostico(id, clave, nuevaSeccionId, nuevoOrden, texto, ayuda, tipo, requerida,
                maximoOpciones, opciones, escala, condicion, alimenta, formaciones, precarga, retiradaEn);
    }

    /** La misma pregunta con otro texto y otra ayuda (los que ya trae la persona con el plazo y la formación puestos). */
    public PreguntaDelDiagnostico conTextos(String nuevoTexto, String nuevaAyuda) {
        return new PreguntaDelDiagnostico(id, clave, seccionId, orden, nuevoTexto, nuevaAyuda, tipo, requerida, maximoOpciones,
                opciones, escala, condicion, alimenta, formaciones, precarga, retiradaEn);
    }

    public PreguntaDelDiagnostico sinCondicion() {
        return new PreguntaDelDiagnostico(id, clave, seccionId, orden, texto, ayuda, tipo, requerida, maximoOpciones,
                opciones, escala, null, alimenta, formaciones, precarga, retiradaEn);
    }

    public PreguntaDelDiagnostico retirada(Instant ahora) {
        return new PreguntaDelDiagnostico(id, clave, seccionId, orden, texto, ayuda, tipo, requerida, maximoOpciones,
                opciones, escala, condicion, alimenta, formaciones, precarga, ahora);
    }

    public PreguntaDelDiagnostico restaurada() {
        return retirada(null);
    }

    private static void validarOpciones(TipoPreguntaOnboarding tipo, List<OpcionDelDiagnostico> opciones) {
        boolean pideOpciones = switch (tipo) {
            case SELECCION_UNICA, SELECCION_MULTIPLE, CUADRICULA -> true;
            default -> false;
        };
        if (pideOpciones && opciones.isEmpty()) {
            throw new IllegalArgumentException(tipo == TipoPreguntaOnboarding.CUADRICULA
                    ? "La cuadrícula necesita al menos una fila" : "La pregunta necesita al menos una opción");
        }
        if (tipo == TipoPreguntaOnboarding.CASILLA && opciones.size() != 1) {
            throw new IllegalArgumentException("El consentimiento lleva una sola frase para la casilla");
        }
        if (!pideOpciones && tipo != TipoPreguntaOnboarding.CASILLA && !opciones.isEmpty()) {
            throw new IllegalArgumentException("Una pregunta de tipo " + tipo + " no lleva opciones");
        }
        Set<String> vistos = new HashSet<>();
        for (OpcionDelDiagnostico opcion : opciones) {
            if (!vistos.add(opcion.valor())) {
                throw new IllegalArgumentException("La opción «" + opcion.etiqueta() + "» está repetida");
            }
        }
    }

    private static Integer validarMaximo(TipoPreguntaOnboarding tipo, Integer maximo, int cuantas) {
        if (maximo == null) {
            return null;
        }
        if (tipo != TipoPreguntaOnboarding.SELECCION_MULTIPLE) {
            throw new IllegalArgumentException("Solo las casillas tienen un máximo de opciones");
        }
        if (maximo < 1 || maximo > cuantas) {
            throw new IllegalArgumentException("El máximo de opciones va de 1 a " + cuantas);
        }
        return maximo;
    }

    private static EscalaDelDiagnostico validarEscala(TipoPreguntaOnboarding tipo, EscalaDelDiagnostico escala) {
        boolean lleva = tipo == TipoPreguntaOnboarding.ESCALA || tipo == TipoPreguntaOnboarding.CUADRICULA;
        if (!lleva) {
            if (escala != null) {
                throw new IllegalArgumentException("Solo la escala y la cuadrícula llevan una escala");
            }
            return null;
        }
        return escala == null ? EscalaDelDiagnostico.DEL_1_AL_5 : escala;
    }

    private static void validarDestino(TipoPreguntaOnboarding tipo, DestinoDeRespuesta destino, List<OpcionDelDiagnostico> opciones) {
        if (destino == null) {
            return;
        }
        boolean libre = tipo == TipoPreguntaOnboarding.TEXTO || tipo == TipoPreguntaOnboarding.AREA_TEXTO;
        if ((destino.esObjetivo() || destino == DestinoDeRespuesta.PRIMERA_ACCION) && !libre) {
            throw new IllegalArgumentException("«" + destino.etiqueta() + "» se responde con texto");
        }
        destino.valoresPermitidos().ifPresent(permitidos -> {
            if (tipo != TipoPreguntaOnboarding.SELECCION_UNICA) {
                throw new IllegalArgumentException("«" + destino.etiqueta() + "» es una pregunta de opción única");
            }
            for (OpcionDelDiagnostico opcion : opciones) {
                if (!permitidos.contains(opcion.valor())) {
                    throw new IllegalArgumentException("La opción «" + opcion.etiqueta() + "» de «" + destino.etiqueta()
                            + "» tiene que decir a cuál corresponde: " + String.join(", ", permitidos.stream().sorted().toList()));
                }
            }
        });
    }

    private static String limpio(String s, int max, String que) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException(que + " es obligatorio");
        }
        return recortarLargo(s.trim(), max, que);
    }

    private static String recortarLargo(String s, int max, String que) {
        if (s.length() > max) {
            throw new IllegalArgumentException(que + " no puede pasar de " + max + " caracteres");
        }
        return s;
    }
}
