package com.renaser.os.mentoring.domain.model.resumen;

import com.renaser.os.mentoring.domain.model.semaforo.ConteoPorColor;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.SemanaDelSemaforo;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.shared.domain.UserId;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Cuándo sale el resumen semanal del semáforo para el mentor y para el líder (D-168,
 * docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §1.2). Puras: reciben el instante y la zona ya
 * resueltos, y no leen ningún reloj.
 *
 * <p><b>Una ventana, no un instante.</b> La semana cierra el sábado 00:00 en la zona del grupo y el
 * barrido corre cada hora. Atar el resumen a UNA corrida lo perdería entero si justo esa hora hubo
 * un despliegue; las seis primeras horas del sábado son seis oportunidades, y el índice único de
 * {@code notificaciones} hace que las siguientes no repitan nada. Pasada la ventana no se manda:
 * un "tu grupo cerró la semana" que llega el martes ya no es un aviso de cierre.
 *
 * <p><b>El día es el de la zona del grupo, nunca el del servidor</b> (regla 02 §1): a las 04:30 UTC
 * de un sábado, en Lima todavía es viernes 23:30 y la semana no cerró.
 */
public final class ReglasDelResumenSemanal {

    /** Del sábado 00:00 al 05:59, hora local del grupo. */
    static final Duration DURACION_DE_LA_VENTANA = Duration.ofHours(6);

    private ReglasDelResumenSemanal() {
    }

    /**
     * La semana que toca resumir en {@code ahora} para un grupo de esa zona.
     *
     * @return el viernes que cierra esa semana ({@code semanaHasta}), o vacío si el instante cae
     *         fuera de la ventana. Qué viernes es lo decide {@link SemanaDelSemaforo}, que es el
     *         único lugar donde vive el corte sábado→viernes.
     */
    public static Optional<LocalDate> semanaQueToca(Instant ahora, ZoneId zona) {
        LocalDateTime horaLocal = LocalDateTime.ofInstant(ahora, zona);
        LocalDate semanaHasta = SemanaDelSemaforo.ultimaCerradaAl(horaLocal.toLocalDate());
        // Nunca es anterior al cierre: la última semana cerrada terminó, como tarde, ayer.
        LocalDateTime cierre = semanaHasta.plusDays(1).atStartOfDay();
        boolean dentro = Duration.between(cierre, horaLocal).compareTo(DURACION_DE_LA_VENTANA) < 0;
        return dentro ? Optional.of(semanaHasta) : Optional.empty();
    }

    /**
     * Si el semáforo del grupo ya tiene algo que informar: al menos uno de sus aprendices tiene la
     * semana CERRADA.
     *
     * <p>Sin ninguna cerrada, el cierre de {@code points} todavía no pasó por el grupo (corre en su
     * propio barrido y puede venir atrasado o haber fallado): avisar "ya está listo" sería falso, y
     * la deduplicación lo dejaría fijo. La corrida de la hora siguiente vuelve a mirar.
     */
    public static boolean semaforoListo(Collection<UserId> aprendices, Map<UserId, VentanaDelSemaforo> semanas) {
        return aprendices.stream()
                .map(semanas::get)
                .filter(Objects::nonNull)
                .anyMatch(VentanaDelSemaforo::cerrada);
    }

    /**
     * Cuántos aprendices cerraron la semana en cada color: el contenido del resumen del sábado.
     *
     * <p><b>Solo cuenta el color de una semana CERRADA.</b> El resumen informa lo que se reportó, y lo
     * único que ya no cambia es la foto del cierre ({@code VentanaDelSemaforo.cerrada}). Un aprendiz
     * sin semana cerrada —el barrido de {@code points} todavía no lo alcanzó, o no se mide— cuenta
     * como sin datos: no saber no es cumplir ni incumplir, y nunca se pinta verde por falta de datos
     * (D-128). El conteo es el mismo tipo que usan la tabla del mentor y el resumen del líder.
     *
     * @param semanas la semana de cada aprendiz, tal como la devuelve {@code SemaforoFinder.semanaDe};
     *                sin clave = esa persona no se mide
     */
    public static ConteoPorColor conteoDelCierre(Collection<UserId> aprendices,
                                                 Map<UserId, VentanaDelSemaforo> semanas) {
        return ConteoPorColor.de(aprendices.stream().map(a -> colorReportado(semanas.get(a))).toList());
    }

    private static ColorSemaforo colorReportado(VentanaDelSemaforo semana) {
        return semana != null && semana.cerrada() ? semana.color() : ColorSemaforo.SIN_DATOS;
    }

    /** Una por grupo y semana: las corridas repetidas de la ventana calculan la misma. */
    public static UUID claveDelGrupo(UUID grupoId, LocalDate semanaHasta) {
        return clave("semaforo-grupo:" + grupoId + ":" + semanaHasta);
    }

    /** Una por semana: cada destinatario recibe UNA, por más grupos y corridas que haya. */
    public static UUID claveGeneral(LocalDate semanaHasta) {
        return clave("semaforo-general:" + semanaHasta);
    }

    private static UUID clave(String semilla) {
        return UUID.nameUUIDFromBytes(semilla.getBytes(StandardCharsets.UTF_8));
    }
}
