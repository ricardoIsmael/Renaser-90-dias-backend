package com.renaser.os.rag.domain.model.analisis;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
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
 * El análisis 80/20 de UNA persona en UN periodo (una semana o un mes del programa) de UNA formación (spec 031, D-306). Una sola fila
 * por persona y periodo ({@code UNIQUE (participante_id, periodo, inicio)}): lo que decide si se vuelve a generar es su estado, no un
 * contador aparte.
 *
 * <p><b>Máquina de estados mínima</b>: nace {@link EstadoDelAnalisis#PENDIENTE} (o {@code SIN_DATOS}, si el periodo no alcanzó el mínimo
 * y no se llamó a la IA); un intento bueno la lleva a {@code LISTO}, que es definitivo; cada intento fallido suma uno y, al llegar al
 * máximo, a {@code AGOTADO}. Los tres estados finales no se mueven. Las cifras que el texto cita viajan con él ({@code cifrasEnJson},
 * FR-006): lo que se leyó hoy es lo que se lee mañana.
 *
 * <p>Contenido sensible (se hizo leyendo lo que la persona escribió): no se loguea nunca.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class AnalisisDelPeriodo {

    private final AnalisisDelPeriodoId id;
    private final UserId participanteId;
    private final Formacion formacion;
    private final TipoDePeriodo tipo;
    private final int numero;
    private final LocalDate inicio;
    private final LocalDate fin;
    private EstadoDelAnalisis estado;
    private int intentos;
    private Instant ultimoIntentoEn;
    private Instant generadoEn;
    private final Instant creadoEn;
    private TextoDelAnalisis texto;
    private String cifrasEnJson;

    /** Un periodo cerrado al que le falta su análisis. */
    public static AnalisisDelPeriodo pendiente(AnalisisDelPeriodoId id, UserId participanteId, Formacion formacion, TipoDePeriodo tipo,
                                               int numero, LocalDate inicio, LocalDate fin, Clock clock) {
        return nuevo(id, participanteId, formacion, tipo, numero, inicio, fin, EstadoDelAnalisis.PENDIENTE, clock);
    }

    /** Un periodo que no alcanzó el mínimo de datos: no se llamó a la IA y no se vuelve a evaluar. */
    public static AnalisisDelPeriodo sinDatosSuficientes(AnalisisDelPeriodoId id, UserId participanteId, Formacion formacion,
                                                         TipoDePeriodo tipo, int numero, LocalDate inicio, LocalDate fin, Clock clock) {
        return nuevo(id, participanteId, formacion, tipo, numero, inicio, fin, EstadoDelAnalisis.SIN_DATOS, clock);
    }

    private static AnalisisDelPeriodo nuevo(AnalisisDelPeriodoId id, UserId participanteId, Formacion formacion, TipoDePeriodo tipo,
                                            int numero, LocalDate inicio, LocalDate fin, EstadoDelAnalisis estado, Clock clock) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Objects.requireNonNull(formacion, "formacion es obligatoria");
        Objects.requireNonNull(tipo, "tipo es obligatorio");
        Objects.requireNonNull(inicio, "inicio es obligatorio");
        Objects.requireNonNull(fin, "fin es obligatorio");
        if (numero < 1 || fin.isBefore(inicio)) {
            throw new IllegalArgumentException("El periodo no es válido: " + numero + " " + inicio + " " + fin);
        }
        return new AnalisisDelPeriodo(id, participanteId, formacion, tipo, numero, inicio, fin, estado, 0, null, null, clock.now(),
                null, null);
    }

    /** Solo para el adaptador de persistencia. */
    public static AnalisisDelPeriodo rehydrate(AnalisisDelPeriodoId id, UserId participanteId, Formacion formacion, TipoDePeriodo tipo,
                                               int numero, LocalDate inicio, LocalDate fin, EstadoDelAnalisis estado, int intentos,
                                               Instant ultimoIntentoEn, Instant generadoEn, Instant creadoEn, TextoDelAnalisis texto,
                                               String cifrasEnJson) {
        return new AnalisisDelPeriodo(id, participanteId, formacion, tipo, numero, inicio, fin, estado, intentos, ultimoIntentoEn,
                generadoEn, creadoEn, texto, cifrasEnJson);
    }

    /** ¿Toca intentar (de nuevo) ahora? Solo un pendiente, y no antes de que pase la espera desde el último intento. */
    public boolean tocaIntentarEn(Instant ahora, ReglasDelAnalisis reglas) {
        return estado == EstadoDelAnalisis.PENDIENTE
                && (ultimoIntentoEn == null || !ahora.isBefore(ultimoIntentoEn.plus(reglas.esperaEntreIntentos())));
    }

    /** El intento salió bien: queda el texto y las cifras que cita. Definitivo. */
    public void completar(TextoDelAnalisis textoGenerado, String cifrasQueCita, Clock clock) {
        exigirPendiente();
        Objects.requireNonNull(textoGenerado, "texto es obligatorio");
        if (cifrasQueCita == null || cifrasQueCita.isBlank()) {
            throw new IllegalArgumentException("Las cifras que el texto cita se guardan junto al texto");
        }
        Instant ahora = clock.now();
        this.texto = textoGenerado;
        this.cifrasEnJson = cifrasQueCita;
        this.estado = EstadoDelAnalisis.LISTO;
        this.intentos += 1;
        this.ultimoIntentoEn = ahora;
        this.generadoEn = ahora;
    }

    /** El intento falló (la IA no respondió o el texto no se pudo mostrar): suma uno; al llegar al máximo el periodo queda sin análisis. */
    public void fallar(ReglasDelAnalisis reglas, Clock clock) {
        exigirPendiente();
        this.intentos += 1;
        this.ultimoIntentoEn = clock.now();
        if (intentos >= reglas.maximoDeIntentos()) {
            this.estado = EstadoDelAnalisis.AGOTADO;
        }
    }

    private void exigirPendiente() {
        if (estado != EstadoDelAnalisis.PENDIENTE) {
            throw new IllegalStateException("El análisis ya está " + estado + ": no se mueve");
        }
    }
}
