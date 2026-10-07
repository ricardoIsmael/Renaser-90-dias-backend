package com.renaser.os.chat.domain.model.ranking;

import com.renaser.os.chat.domain.model.mensaje.MensajeId;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * La semana del podio (D-262): de lunes a domingo, en hora de Lima, y ya CERRADA.
 *
 * <p><b>Una foto global, no por persona</b> (como el corte diario del ranking, E-561): el podio es uno solo para
 * todo el grupo general, así que su semana se mide en la zona de todo el padrón. La fecha sale del instante en
 * Lima ({@link #ultimaCerradaAl}), nunca de la fecha del servidor: el domingo a las 20:00 de Lima el servidor
 * ya está en el lunes (01:00 UTC), y con su fecha la «última semana cerrada» sería la que todavía no terminó
 * (regla 02 §1).
 *
 * <p><b>Los ids de sus dos mensajes se derivan de la semana</b> ({@link UUID#nameUUIDFromBytes}), como la tarjeta
 * del semáforo (D-223): publicar dos veces, tarde, o desde dos instancias, no duplica nada.
 */
public record SemanaDelRanking(LocalDate lunes) {

    /** La zona de todo el padrón (el default de {@code participantes_programa.timezone}). */
    public static final ZoneId ZONA_DEL_PROGRAMA = ZoneId.of("America/Lima");

    /** La versión del diseño de la imagen: va en su ruta, así un cambio de diseño no pisa los podios viejos. */
    static final String VERSION_DE_LA_IMAGEN = "v1";

    private static final Locale ESPANOL = Locale.forLanguageTag("es");
    private static final DateTimeFormatter DIA_Y_MES = DateTimeFormatter.ofPattern("d 'de' MMMM", ESPANOL);

    public SemanaDelRanking {
        Objects.requireNonNull(lunes, "lunes es obligatorio");
        if (lunes.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("La semana del podio empieza un lunes, no un " + lunes.getDayOfWeek());
        }
    }

    /** La última semana de lunes a domingo que ya terminó en Lima en {@code ahora}. */
    public static SemanaDelRanking ultimaCerradaAl(Instant ahora) {
        LocalDate hoyEnLima = ahora.atZone(ZONA_DEL_PROGRAMA).toLocalDate();
        LocalDate lunesDeEstaSemana = hoyEnLima.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new SemanaDelRanking(lunesDeEstaSemana.minusWeeks(1));
    }

    /** El corte del ranking: los 7 días de hábitos que terminan este domingo son justo esta semana. */
    public LocalDate domingo() {
        return lunes.plusDays(6);
    }

    /** «Del lunes 29 de septiembre al domingo 5 de octubre». */
    public String rango() {
        return "Del lunes " + lunes.format(DIA_Y_MES) + " al domingo " + domingo().format(DIA_Y_MES);
    }

    /** Cuántas semanas pasaron desde el lunes 5 de enero de 1970: rota las plantillas del texto. */
    long numero() {
        return Math.floorDiv(lunes.toEpochDay() - 4, 7);
    }

    public MensajeId idDeLaImagen() {
        return idDe("imagen");
    }

    public MensajeId idDelTexto() {
        return idDe("texto");
    }

    /** Fuera de {@code chat/<conversación>/}, como las tarjetas del semáforo: la imagen no es de ningún chat. */
    public String rutaDeLaImagen() {
        return "ranking-semanal/" + lunes + "-" + VERSION_DE_LA_IMAGEN + ".jpg";
    }

    private MensajeId idDe(String pieza) {
        String clave = "ranking-semanal|" + lunes + "|" + pieza;
        return MensajeId.of(UUID.nameUUIDFromBytes(clave.getBytes(StandardCharsets.UTF_8)));
    }
}
