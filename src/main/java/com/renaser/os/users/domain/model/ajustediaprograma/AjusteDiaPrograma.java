package com.renaser.os.users.domain.model.ajustediaprograma;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un ajuste manual del dia del programa hecho por un ADMIN/ALCHEMIST (tabla
 * `ajustes_dia_programa`, V21, D-82). Agregado propio y no un campo mas de
 * {@code ParticipacionPrograma}: tiene identidad, ciclo de vida y repositorio propios, y
 * un participante puede acumular varios a lo largo de sus 90 dias — es el criterio de
 * "subcarpeta por agregado" de CLAUDE.MD §5.1.2.
 *
 * <p><b>Inmutable de punta a punta.</b> Es un hecho histórico: paso, y no se edita. Un
 * ajuste equivocado se corrige registrando OTRO ajuste, y los dos quedan a la vista
 * (misma filosofia append-only que {@code AjustePuntos} en `points`). Por eso no tiene un
 * solo metodo que mute estado.
 *
 * <p>Guarda el dia Y el offset ({@code diasAjuste*}) porque desde V20 el offset es lo que
 * de verdad manda el reloj: con los dos valores se puede revertir un ajuste sin
 * recalcular nada contra el calendario.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class AjusteDiaPrograma {

    /** Tope de `motivo` en la tabla — un motivo es una nota corta, no una historia clinica. */
    public static final int MAX_MOTIVO = 280;
    /** Lo que se guarda cuando el panel admin todavia no manda `motivo` (ventana de
     * transicion de D-82). Explicito y feo a proposito: se prefiere ver que nadie lo
     * registro antes que un NULL que obligue a ramificar en cada lectura. */
    public static final String MOTIVO_NO_REGISTRADO = "(sin motivo registrado)";

    private final UUID id;
    private final UserId participanteId;
    private final int diaAnterior;
    private final int diaNuevo;
    private final int diasAjusteAnterior;
    private final int diasAjusteNuevo;
    private final String motivo;
    private final UserId ajustadoPor;
    private final Instant ajustadoEn;

    /**
     * Registra un ajuste recien ocurrido. El {@code id} lo provee el caso de uso via
     * {@code IdGenerator} y no se genera aca dentro (D-59): {@code domain/} es puro y
     * determinista, y asignar identidad es conceptualmente una operacion externa al
     * agregado. {@code motivo} nulo o en blanco se normaliza a
     * {@link #MOTIVO_NO_REGISTRADO} en vez de rechazarse: el endpoint ya existia sin ese
     * campo y romper al panel admin actual seria peor que guardar un ajuste sin explicar.
     * Un motivo mas largo que {@value #MAX_MOTIVO} se recorta, no explota — perder una
     * bitacora por un texto largo seria el peor resultado posible de los tres.
     */
    public static AjusteDiaPrograma registrar(UUID id, UserId participanteId, int diaAnterior, int diaNuevo,
                                               int diasAjusteAnterior, int diasAjusteNuevo, String motivo,
                                               UserId ajustadoPor, Clock clock) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Objects.requireNonNull(ajustadoPor, "ajustadoPor es obligatorio");
        return new AjusteDiaPrograma(id, participanteId, diaAnterior, diaNuevo,
                diasAjusteAnterior, diasAjusteNuevo, normalizarMotivo(motivo), ajustadoPor, clock.now());
    }

    /** Solo para el adaptador de persistencia: reconstruye una fila ya existente. */
    public static AjusteDiaPrograma rehydrate(UUID id, UserId participanteId, int diaAnterior, int diaNuevo,
                                               int diasAjusteAnterior, int diasAjusteNuevo, String motivo,
                                               UserId ajustadoPor, Instant ajustadoEn) {
        return new AjusteDiaPrograma(id, participanteId, diaAnterior, diaNuevo, diasAjusteAnterior,
                diasAjusteNuevo, motivo, ajustadoPor, ajustadoEn);
    }

    private static String normalizarMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            return MOTIVO_NO_REGISTRADO;
        }
        String limpio = motivo.strip();
        return limpio.length() > MAX_MOTIVO ? limpio.substring(0, MAX_MOTIVO) : limpio;
    }

    /** Cuantos dias se movio el reloj. Negativo = se lo retrocedio (el caso habitual). */
    public int diasMovidos() {
        return diaNuevo - diaAnterior;
    }

    @Override
    public String toString() {
        return "AjusteDiaPrograma[" + participanteId + ", " + diaAnterior + "->" + diaNuevo + "]";
    }
}
