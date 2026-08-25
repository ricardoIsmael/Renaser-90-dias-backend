package com.renaser.os.habits.domain.model.registro;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * La ventana de entrega de un habito, resuelta de punta a punta — equivalente a
 * `HabitWindow`/`habitWindowFrom`/`limitInstantFor`/`effectiveExtensionMs` de
 * `service.ts` (repo viejo, paso 0 en docs/MODULO_HABITS.md).
 *
 * <p>{@code instanteAncla}: el instante contra el que se mide la entrega — la
 * hora FIN del habito si la tiene, la de INICIO si no (fallback documentado en
 * el repo viejo: "sin hora de fin, el ancla es la hora de inicio").
 *
 * <p>{@code extension}: cuanto dura la extension para ESTE habito, ya recortada
 * contra la medianoche siguiente (el cron nocturno expira TODO lo PENDIENTE de
 * ayer sin mirar ningun margen — la extension nunca puede prometer mas de lo
 * que el cron va a respetar).
 *
 * <p>{@code plazoEvidencia} = instanteAncla + 10 min de gracia + extension.
 * Pasado esto, el registro queda bloqueado (no acepta evidencia ni completacion)
 * y pasa a EXPIRADO.
 */
public record VentanaEntrega(Instant instanteAncla, boolean tieneHoraLimite, Duration extension,
                              Instant plazoEvidencia) {

    /** Minutos de gracia tras la hora fin para entregar evidencia (points.ts:41, GRACE_WINDOW_MINUTES). */
    public static final int GRACIA_MINUTOS = 10;

    /** Horas de extension por DEFAULT cuando el habito no tiene `horas_extra_evidencia` propia (points.ts:59). */
    public static final int EXTENSION_DEFAULT_HORAS = 3;

    /**
     * Calcula la ventana a partir del horario YA resuelto (preferencia -&gt; horario
     * del catalogo -&gt; ninguno). Devuelve {@code null} si el habito no tiene NINGUNA
     * hora configurada (ni fin ni inicio): no hay nada contra que medir y el
     * registro no vence nunca (mismo criterio que `habitWindowFrom` cuando
     * `base` es null).
     *
     * @param horasExtensionConfiguradas horas de extension propias del habito
     *                                    (columna `horas_extra_evidencia`), o
     *                                    {@code null} para usar el default global
     */
    public static VentanaEntrega calcular(LocalDate fechaEjecucion, LocalTime horaDisparo, LocalTime horaLimite,
                                           ZoneId zonaHoraria, Integer horasExtensionConfiguradas) {
        boolean tieneHoraLimite = horaLimite != null;
        LocalTime base = tieneHoraLimite ? horaLimite : horaDisparo;
        if (base == null) {
            return null;
        }

        Instant inicioDia = fechaEjecucion.atStartOfDay(zonaHoraria).toInstant();
        Instant instanteAncla = instanteParaHora(inicioDia, horaDisparo, base, tieneHoraLimite);

        Duration deseada = Duration.ofHours(
                horasExtensionConfiguradas != null ? horasExtensionConfiguradas : EXTENSION_DEFAULT_HORAS);
        Instant medianocheSiguiente = fechaEjecucion.plusDays(1).atStartOfDay(zonaHoraria).toInstant();
        Duration margen = Duration.between(instanteAncla.plus(Duration.ofMinutes(GRACIA_MINUTOS)), medianocheSiguiente);
        Duration extension = margen.isNegative() ? Duration.ZERO
                : (deseada.compareTo(margen) > 0 ? margen : deseada);

        Instant plazo = instanteAncla.plus(Duration.ofMinutes(GRACIA_MINUTOS)).plus(extension);
        return new VentanaEntrega(instanteAncla, tieneHoraLimite, extension, plazo);
    }

    /** Ventanas 22:00 -&gt; 02:00: si el disparo no es anterior a la base, la base cae al dia siguiente. */
    private static Instant instanteParaHora(Instant inicioDia, LocalTime horaDisparo, LocalTime horaBase,
                                             boolean tieneHoraLimite) {
        long minutosBase = horaBase.getHour() * 60L + horaBase.getMinute();
        if (tieneHoraLimite && horaDisparo != null && !horaDisparo.isBefore(horaBase)) {
            minutosBase += 24 * 60L;
        }
        return inicioDia.plus(Duration.ofMinutes(minutosBase));
    }

    public boolean vencida(Instant ahora) {
        return ahora.isAfter(plazoEvidencia);
    }
}
