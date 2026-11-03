package com.renaser.os.shared.domain;


/**
 * Las dos preguntas que cada pieza de la Comunidad se hace sobre su formación (spec 024, D-301), resueltas en un solo lugar para que el Muro, los eventos, los
 * cursos, los testimonios, la Tribu y el ranking no las reimplementen:
 *
 * <ul>
 *   <li>{@link #paraLeer}: qué formaciones lee quien pide. La persona de una formación lee la suya; Administración, Alquimista y Soporte TI leen «Todas» o la que
 *       eligen con el parámetro {@code formation}. Pedir una formación fuera del alcance no revela nada: la lista sale vacía.</li>
 *   <li>{@link #paraCrear}: en qué formación nace lo nuevo. La del aprendiz o la activa del personal (FR-006); Administración la elige siempre, y nada se crea
 *       «para todas» ni «sin formación».</li>
 * </ul>
 */
public final class FormacionDelContenido {

    static final String ELEGIR_LA_FORMACION = "Elige la formación en la que lo quieres crear.";
    static final String SIN_FORMACION = "No tienes una formación asignada. Pídele a Administración que te asigne una.";

    private FormacionDelContenido() {
    }

    /** El parámetro {@code formation} de la API: vacío o ausente = sin filtro ({@code null}); un código que no existe es un error de quien llama (400). */
    public static Formacion pedida(String codigo) {
        return codigo == null || codigo.isBlank() ? null : Formacion.desde(codigo);
    }

    /** Lo que {@code alcance} deja leer cuando la persona pidió {@code pedida} ({@code null} = sin filtro). */
    public static AlcanceDeFormacion paraLeer(AlcanceDeFormacion alcance, Formacion pedida) {
        return alcance.acotadoA(pedida);
    }

    /**
     * @throws ObjetoDeOtraFormacionException (404) si pidió una formación que no ve
     * @throws NotAuthorizedException         (403) si no tiene ninguna formación
     * @throws IllegalArgumentException       (400) si ve varias y no dijo cuál (Administración)
     */
    public static Formacion paraCrear(AlcanceDeFormacion alcance, Formacion pedida) {
        if (alcance.esVacio()) {
            throw new NotAuthorizedException(SIN_FORMACION);
        }
        if (pedida != null) {
            alcance.exigirQueIncluya(pedida);
            return pedida;
        }
        return alcance.unica().orElseThrow(() -> new IllegalArgumentException(ELEGIR_LA_FORMACION));
    }
}
