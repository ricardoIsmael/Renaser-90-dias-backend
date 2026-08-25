package com.renaser.os.onboarding.domain.model.respuesta;

import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * EAV tipado (tabla {@code respuestas_onboarding}): exactamente UN valor no-nulo entre
 * texto/numero/booleano/escala/json, o ninguno si la respuesta es solo una media adjunta
 * (AUDIO/FIRMA/ARCHIVO) — replica el CHECK {@code un_solo_valor} del baseline.
 *
 * <p><b>Mapeo tipo de pregunta -> slot de valor</b> (decision de este modulo, no confirmada
 * por nadie del equipo — el baseline no lo especifica explicitamente, es una consecuencia
 * necesaria de que el esquema EAV solo tiene 5 slots para 11 tipos de pregunta; documentado
 * en docs/MODULO_ONBOARDING.md para que se corrija si no es la intencion real):
 * <ul>
 *   <li>{@code TEXTO}, {@code AREA_TEXTO}, {@code FECHA} (ISO-8601) -> {@code valorTexto}</li>
 *   <li>{@code NUMERO} -> {@code valorNumero}</li>
 *   <li>{@code ESCALA} -> {@code valorEscala} (1..10)</li>
 *   <li>{@code SELECCION_UNICA} -> {@code valorTexto} (el {@code valor} de la opcion elegida)</li>
 *   <li>{@code SELECCION_MULTIPLE} -> {@code valorJson} (array de valores elegidos, opaco)</li>
 *   <li>{@code CASILLA} -> {@code valorBooleano}</li>
 *   <li>{@code AUDIO}, {@code FIRMA}, {@code ARCHIVO} -> sin valor tipado, requiere {@code mediaId}</li>
 * </ul>
 *
 * <p>Upsert (unico {@code usuario_id, pregunta_id}): {@link #actualizarValor} NUNCA crea una
 * identidad nueva, siempre conserva {@code id}/{@code usuarioId}/{@code preguntaId}/
 * {@code respondidaEn} originales — es la garantia, a nivel de dominio, de que "guardar de
 * nuevo actualiza, no duplica" (el upsert-por-clave contra Postgres es responsabilidad del
 * adaptador de persistencia, ver {@code RespuestaPersistenceAdapter}).
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class Respuesta {

    private final Long id;
    private final UserId usuarioId;
    private final int preguntaId;
    private String valorTexto;
    private BigDecimal valorNumero;
    private Boolean valorBooleano;
    private Short valorEscala;
    private String valorJson;
    private Long mediaId;
    private Instant aceptadaEn;
    private final Instant respondidaEn;
    private Instant actualizadoEn;

    /**
     * Crea una respuesta nueva (id null: lo asigna Postgres, o el adaptador la reutiliza si
     * ya existia una fila para esa {@code (usuarioId, preguntaId)} — ver upsert arriba).
     * Valida coherencia con {@code tipo} segun el mapeo documentado en la clase.
     */
    public static Respuesta crear(TipoPreguntaOnboarding tipo, UserId usuarioId, int preguntaId, String valorTexto,
                                   BigDecimal valorNumero, Boolean valorBooleano, Short valorEscala,
                                   String valorJson, Long mediaId, Clock clock) {
        requireCoherenciaConTipo(tipo, valorTexto, valorNumero, valorBooleano, valorEscala, valorJson, mediaId);
        Instant ahora = clock.now();
        Instant aceptadaEn = tipo == TipoPreguntaOnboarding.CASILLA && Boolean.TRUE.equals(valorBooleano)
                ? ahora : null;
        Respuesta r = new Respuesta(null, requireUsuarioId(usuarioId), preguntaId, valorTexto, valorNumero,
                valorBooleano, valorEscala, valorJson, mediaId, aceptadaEn, ahora, ahora);
        r.requireUnSoloValor();
        return r;
    }

    /** Solo para el adaptador de persistencia: reconstruye una fila ya existente, sin re-validar tipo. */
    public static Respuesta rehydrate(Long id, UserId usuarioId, int preguntaId, String valorTexto,
                                       BigDecimal valorNumero, Boolean valorBooleano, Short valorEscala,
                                       String valorJson, Long mediaId, Instant aceptadaEn, Instant respondidaEn,
                                       Instant actualizadoEn) {
        return new Respuesta(id, usuarioId, preguntaId, valorTexto, valorNumero, valorBooleano, valorEscala,
                valorJson, mediaId, aceptadaEn, respondidaEn, actualizadoEn);
    }

    /**
     * Actualiza el valor de una respuesta EXISTENTE (misma pregunta respondida de nuevo).
     * Conserva {@code id}/{@code usuarioId}/{@code preguntaId}/{@code respondidaEn} — nunca
     * crea una respuesta distinta, ver javadoc de la clase.
     */
    public Respuesta actualizarValor(TipoPreguntaOnboarding tipo, String valorTexto, BigDecimal valorNumero,
                                      Boolean valorBooleano, Short valorEscala, String valorJson, Long mediaId,
                                      Clock clock) {
        requireCoherenciaConTipo(tipo, valorTexto, valorNumero, valorBooleano, valorEscala, valorJson, mediaId);
        Instant ahora = clock.now();
        Instant aceptadaEn = tipo == TipoPreguntaOnboarding.CASILLA && Boolean.TRUE.equals(valorBooleano)
                ? ahora : this.aceptadaEn;
        Respuesta actualizada = new Respuesta(this.id, this.usuarioId, this.preguntaId, valorTexto, valorNumero,
                valorBooleano, valorEscala, valorJson, mediaId, aceptadaEn, this.respondidaEn, ahora);
        actualizada.requireUnSoloValor();
        return actualizada;
    }

    private void requireUnSoloValor() {
        int cantidad = 0;
        if (valorTexto != null) {
            cantidad++;
        }
        if (valorNumero != null) {
            cantidad++;
        }
        if (valorBooleano != null) {
            cantidad++;
        }
        if (valorEscala != null) {
            cantidad++;
        }
        if (valorJson != null) {
            cantidad++;
        }
        if (cantidad > 1) {
            throw new IllegalArgumentException(
                    "Una respuesta admite un solo valor no nulo (texto/numero/booleano/escala/json): " + this);
        }
        if (valorEscala != null && (valorEscala < 1 || valorEscala > 10)) {
            throw new IllegalArgumentException("valorEscala debe estar entre 1 y 10: " + valorEscala);
        }
    }

    /** Slot de valor EAV esperado para un tipo de pregunta — ver mapeo documentado en la clase. */
    private enum SlotValor {
        TEXTO, NUMERO, BOOLEANO, ESCALA, JSON, SOLO_MEDIA
    }

    private static SlotValor slotEsperado(TipoPreguntaOnboarding tipo) {
        return switch (tipo) {
            case TEXTO, AREA_TEXTO, FECHA, SELECCION_UNICA -> SlotValor.TEXTO;
            case NUMERO -> SlotValor.NUMERO;
            case ESCALA -> SlotValor.ESCALA;
            case SELECCION_MULTIPLE -> SlotValor.JSON;
            case CASILLA -> SlotValor.BOOLEANO;
            case AUDIO, FIRMA, ARCHIVO -> SlotValor.SOLO_MEDIA;
        };
    }

    private static void requireCoherenciaConTipo(TipoPreguntaOnboarding tipo, String valorTexto,
                                                  BigDecimal valorNumero, Boolean valorBooleano, Short valorEscala,
                                                  String valorJson, Long mediaId) {
        Objects.requireNonNull(tipo, "tipo es obligatorio");
        SlotValor esperado = slotEsperado(tipo);
        if (esperado == SlotValor.SOLO_MEDIA) {
            if (mediaId == null) {
                throw new IllegalArgumentException("Una respuesta de tipo " + tipo + " requiere mediaId");
            }
            if (valorTexto != null || valorNumero != null || valorBooleano != null || valorEscala != null
                    || valorJson != null) {
                throw new IllegalArgumentException("Una respuesta de tipo " + tipo + " no lleva valor tipado, solo media");
            }
            return;
        }
        boolean textoOk = (esperado == SlotValor.TEXTO) == (valorTexto != null);
        boolean numeroOk = (esperado == SlotValor.NUMERO) == (valorNumero != null);
        boolean booleanoOk = (esperado == SlotValor.BOOLEANO) == (valorBooleano != null);
        boolean escalaOk = (esperado == SlotValor.ESCALA) == (valorEscala != null);
        boolean jsonOk = (esperado == SlotValor.JSON) == (valorJson != null);
        if (!(textoOk && numeroOk && booleanoOk && escalaOk && jsonOk)) {
            throw new IllegalArgumentException(
                    "Una respuesta de tipo " + tipo + " requiere exactamente el valor " + esperado
                            + " y ningun otro slot");
        }
    }

    private static UserId requireUsuarioId(UserId usuarioId) {
        return Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
    }

    @Override
    public String toString() {
        return "Respuesta[" + id + ", " + usuarioId + ", pregunta=" + preguntaId + "]";
    }
}
