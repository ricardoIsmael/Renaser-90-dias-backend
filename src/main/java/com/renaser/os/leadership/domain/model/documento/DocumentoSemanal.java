package com.renaser.os.leadership.domain.model.documento;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Un documento del liderazgo ya generado y guardado (spec 056, FR-039 y FR-040): de quién es, de qué período, la huella de los datos
 * con que se hizo, dónde está guardado, cuándo y quién lo generó. <b>Se guarda para siempre</b>: no tiene un solo método que lo cambie;
 * si los datos cambian, sale otro documento con otra huella y el anterior queda.
 *
 * <p>La ruta es determinista —{@code reportes/{persona}/{inicio}-{tipo}-{huella8}.html}— y la huella es el SHA-256 de los datos escritos de
 * una sola forma ({@link JsonCanonico}): pedir el mismo documento sin cambios da la misma ruta, y por eso se reutiliza en vez de
 * generarse otra vez. Dos pedidos a la vez generan, a lo sumo, el mismo objeto.
 *
 * <p>{@code personaId} es la persona de quien se habla (el aprendiz o el mentor) y, en el reporte del mes, quien lo pidió, porque ese reporte
 * depende de las formaciones que ve esa persona. El id y la hora entran por {@code IdGenerator} y {@code Clock}.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class DocumentoSemanal {

    private static final Pattern HUELLA_VALIDA = Pattern.compile("[0-9a-f]{64}");
    private static final int LARGO_DE_LA_HUELLA_EN_LA_RUTA = 8;

    private final UUID id;
    private final UserId personaId;
    private final TipoDeDocumento tipo;
    private final LocalDate periodoInicio;
    private final LocalDate periodoFin;
    private final String huella;
    private final String ruta;
    private final Instant generadoEn;
    private final UserId generadoPor;

    /** El documento que se acaba de generar: su ruta sale de lo que lo identifica y su hora, del reloj. */
    public static DocumentoSemanal generar(UUID id, Contenido contenido, Clock clock) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(contenido, "contenido es obligatorio");
        return new DocumentoSemanal(id, contenido.personaId(), contenido.tipo(), contenido.periodoInicio(), contenido.periodoFin(),
                contenido.huella(), rutaDe(contenido), clock.now(), contenido.generadoPor());
    }

    /** Solo para el adaptador de persistencia: la ruta y la hora son las que se guardaron, no se recalculan. */
    public static DocumentoSemanal rehydrate(UUID id, Contenido contenido, Constancia constancia) {
        return new DocumentoSemanal(id, contenido.personaId(), contenido.tipo(), contenido.periodoInicio(), contenido.periodoFin(),
                contenido.huella(), constancia.ruta(), constancia.generadoEn(), contenido.generadoPor());
    }

    /** El SHA-256, en hexadecimal, de los datos escritos de forma canónica junto con la versión de la plantilla. */
    public static String huellaDe(DatosDelDocumento datos) {
        Objects.requireNonNull(datos, "datos es obligatorio");
        String canonico = "{\"plantilla\":" + DatosDelDocumento.VERSION_DE_LA_PLANTILLA + ",\"datos\":" + JsonCanonico.de(datos) + "}";
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Todo JDK trae SHA-256", e);
        }
    }

    public static String rutaDe(Contenido contenido) {
        return "reportes/" + contenido.personaId() + "/" + contenido.periodoInicio() + "-" + contenido.tipo().name() + "-"
                + contenido.huella().substring(0, LARGO_DE_LA_HUELLA_EN_LA_RUTA) + ".html";
    }

    /**
     * Lo que identifica a un documento y no cambia.
     *
     * @param huella el SHA-256 de {@link #huellaDe}: 64 caracteres en hexadecimal minúscula
     */
    public record Contenido(UserId personaId, TipoDeDocumento tipo, LocalDate periodoInicio, LocalDate periodoFin, String huella,
                            UserId generadoPor) {

        public Contenido {
            Objects.requireNonNull(personaId, "personaId es obligatorio");
            Objects.requireNonNull(tipo, "tipo es obligatorio");
            Objects.requireNonNull(periodoInicio, "periodoInicio es obligatorio");
            Objects.requireNonNull(periodoFin, "periodoFin es obligatorio");
            Objects.requireNonNull(generadoPor, "generadoPor es obligatorio");
            if (periodoFin.isBefore(periodoInicio)) {
                throw new IllegalArgumentException("El documento termina antes de empezar: " + periodoInicio + " a " + periodoFin);
            }
            if (huella == null || !HUELLA_VALIDA.matcher(huella).matches()) {
                throw new IllegalArgumentException("La huella es un SHA-256 en hexadecimal minúscula: " + huella);
            }
        }
    }

    /** Lo que se guardó al generarlo. */
    public record Constancia(String ruta, Instant generadoEn) {

        public Constancia {
            Objects.requireNonNull(ruta, "ruta es obligatoria");
            Objects.requireNonNull(generadoEn, "generadoEn es obligatorio");
        }
    }
}
