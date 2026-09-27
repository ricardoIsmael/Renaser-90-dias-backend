package com.renaser.os.habits.domain.model.diario;

import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Entrada de diario consolidada (tabla `entradas_diario`) — un habito
 * JOURNALING cuelga su respuesta de aca en vez de duplicarla solo en
 * `registros_habito.respuesta_texto` (UNIQUE participante+fecha+tipo).
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class EntradaDiario {

    /**
     * Largo maximo del texto, en caracteres (TRN-18 del e2e, 2026-09-27). No habia ninguno: un
     * {@code PUT /journal/today} con 1 MB respondia 200 y se guardaba entero. 4000 es el tope que ya
     * tiene el texto de un habito de escritura ({@code CompletarRegistroRequest.respuestaTexto}),
     * que es el mismo tipo de escrito; el numero lo confirma el dueño (pregunta de D-216).
     */
    public static final int TEXTO_MAXIMO_CARACTERES = 4_000;

    private final EntradaDiarioId id;
    private final UserId participanteId;
    private final LocalDate fecha;
    private final TipoEntradaDiario tipo;
    private String contenidoTexto;
    private String audioBucket;
    private String audioRuta;
    private String transcripcion;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    /**
     * El {@code id} entra por parametro, no se genera aca: la identidad viene del puerto
     * {@code IdGenerator} que inyecta el caso de uso ({@code BitacoraNocturnaService.escribir}).
     * Asi {@code escribir} es referencialmente transparente y un test puede fijar el id que
     * espera, en vez de tener que caer a {@link #rehydrate} para lograrlo.
     */
    public static EntradaDiario escribir(EntradaDiarioId id, UserId participanteId, LocalDate fecha,
                                          TipoEntradaDiario tipo, String contenidoTexto, Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Objects.requireNonNull(fecha, "fecha es obligatoria");
        Objects.requireNonNull(tipo, "tipo es obligatorio");
        return new EntradaDiario(id, participanteId, fecha, tipo, requireDentroDelTope(contenidoTexto), null, null,
                null, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static EntradaDiario rehydrate(EntradaDiarioId id, UserId participanteId, LocalDate fecha,
                                           TipoEntradaDiario tipo, String contenidoTexto, String audioBucket,
                                           String audioRuta, String transcripcion, Instant creadoEn,
                                           Instant actualizadoEn) {
        return new EntradaDiario(id, participanteId, fecha, tipo, contenidoTexto, audioBucket, audioRuta,
                transcripcion, creadoEn, actualizadoEn);
    }

    public void actualizarTexto(String contenidoTexto, Instant ahora) {
        this.contenidoTexto = requireDentroDelTope(contenidoTexto);
        this.actualizadoEn = ahora;
    }

    /**
     * Cuenta caracteres como los cuenta una persona (un emoji es uno), no unidades UTF-16. Sin texto
     * vale: una entrada puede ser solo audio. Una fila vieja mas larga se sigue leyendo
     * ({@link #rehydrate} no pasa por aca); lo que no se puede es escribir una nueva.
     */
    private static String requireDentroDelTope(String texto) {
        if (texto == null) {
            return null;
        }
        int caracteres = texto.codePointCount(0, texto.length());
        if (caracteres > TEXTO_MAXIMO_CARACTERES) {
            throw new IllegalArgumentException("El texto puede tener hasta " + TEXTO_MAXIMO_CARACTERES
                    + " caracteres y este tiene " + caracteres);
        }
        return texto;
    }

    public void adjuntarAudio(String bucket, String ruta, Instant ahora) {
        this.audioBucket = Objects.requireNonNull(bucket, "bucket es obligatorio");
        this.audioRuta = Objects.requireNonNull(ruta, "ruta es obligatoria");
        this.actualizadoEn = ahora;
    }

    @Override
    public String toString() {
        return "EntradaDiario[" + id + ", " + participanteId + ", " + fecha + ", " + tipo + "]";
    }
}
