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
        return new EntradaDiario(id, participanteId, fecha, tipo, contenidoTexto, null, null,
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
        this.contenidoTexto = contenidoTexto;
        this.actualizadoEn = ahora;
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
