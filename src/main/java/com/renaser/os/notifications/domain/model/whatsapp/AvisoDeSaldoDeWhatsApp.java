package com.renaser.os.notifications.domain.model.whatsapp;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

/**
 * Si hay que avisarle a Administración que los WhatsApp no están saliendo porque la cuenta de YCloud se quedó sin saldo (spec 054). Es una regla pura sobre lo
 * que anotó {@code entregas_whatsapp}: hay aviso cuando, en las últimas 24 horas, hubo al menos una falla por saldo y ningún envío salió bien después de la última.
 *
 * @param avisar   si la tarjeta de Administración debe verse
 * @param fallaron cuántos envíos fallaron por saldo desde el último que salió bien (todos los de la ventana si ninguno salió bien); 0 si no hay aviso
 * @param desde    el momento del primero de esos envíos fallidos; {@code null} si no hay aviso
 */
public record AvisoDeSaldoDeWhatsApp(boolean avisar, int fallaron, Instant desde) {

    /** Cuánto hacia atrás se mira. */
    public static final Duration VENTANA = Duration.ofHours(24);

    /**
     * El texto de YCloud que dice «sin saldo» («The account balance is insufficient.»), en minúscula: es lo único que sabe el patrón, y la consulta de la base
     * y {@link #esFallaPorSaldo} lo toman de aquí.
     */
    public static final String PATRON_DEL_MOTIVO = "balance is insufficient";

    public static final AvisoDeSaldoDeWhatsApp NINGUNO = new AvisoDeSaldoDeWhatsApp(false, 0, null);

    /** Si el motivo anotado de un envío fallido es el de saldo insuficiente, sin distinguir mayúsculas. */
    public static boolean esFallaPorSaldo(String motivo) {
        return motivo != null && motivo.toLowerCase(Locale.ROOT).contains(PATRON_DEL_MOTIVO);
    }

    /**
     * @param fallasPorSaldo los momentos de los envíos FALLIDO por saldo dentro de la ventana
     * @param ultimoQueSalio el momento del último envío que salió bien (aceptado, enviado, entregado o leído) dentro de la ventana
     */
    public static AvisoDeSaldoDeWhatsApp evaluar(Collection<Instant> fallasPorSaldo, Optional<Instant> ultimoQueSalio) {
        Optional<Instant> ultimaFalla = fallasPorSaldo.stream().max(Instant::compareTo);
        if (ultimaFalla.isEmpty() || ultimoQueSalio.filter(salio -> salio.isAfter(ultimaFalla.get())).isPresent()) {
            return NINGUNO;
        }
        // El último que salió bien (si lo hubo) quedó antes de la última falla: cuenta solo la racha de fallas que viene después.
        var racha = fallasPorSaldo.stream().filter(falla -> ultimoQueSalio.map(salio -> falla.isAfter(salio)).orElse(true)).toList();
        Instant primera = racha.stream().min(Instant::compareTo).orElseThrow();
        return new AvisoDeSaldoDeWhatsApp(true, racha.size(), primera);
    }
}
