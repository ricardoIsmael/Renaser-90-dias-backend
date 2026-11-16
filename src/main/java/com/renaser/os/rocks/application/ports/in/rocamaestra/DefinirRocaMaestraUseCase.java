package com.renaser.os.rocks.application.ports.in.rocamaestra;

import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Define el objetivo de 90 dias del aprendiz en un eje, o registra su avance si ya lo tenia.
 *
 * <p><b>Una vez definido, el objetivo queda fijo (D-234).</b> Mandar la misma definicion se
 * acepta; mandarla con otro {@code avance} registra el progreso; cambiar el objetivo, la meta, la
 * unidad o la linea base lanza {@code RocaMaestraFijaException} (409 {@code ROCA_MAESTRA_FIJA}).
 *
 * > <b>Corregido 2026-09-30 (D-234).</b> Decia: "Define el objetivo [...] o corrige el que ya
 * > tenia". Ya no se corrige: lo decidio el dueno.
 *
 * <p><b>Sin Mapa (spec 026, D-303).</b> El Mapa de Renacimiento se retiro y ya no decide nada ({@code desdeElMapa} se acepta y se ignora; lo manda la app
 * anterior). El objetivo queda fijo desde que se define, salvo el que nacio del diagnostico: llega «por confirmar» y el primer pedido lo reescribe (o lo deja
 * igual) y lo confirma. Antes de decidir se materializan los objetivos que nacieron del diagnostico de la persona, si los hay.
 *
 * > <b>Corregido 2026-10-31 (D-303).</b> Decia que la fijeza dependia del Mapa activo y que, con {@code renaser.rocks.exigir-mapa-activo} encendido, un pedido
 * > que no venia del Mapa lanzaba {@code MapaPendienteException} (409 {@code MAPA_PENDIENTE}). Ese interruptor y esa excepcion ya no existen.
 *
 * <p><b>Es una sola operacion y no un "crear" mas un "editar"</b> porque la tabla ya impone
 * {@code UNIQUE (participante_id, eje)}: por eje hay exactamente una Roca Maestra o ninguna.
 * Partirlo en dos obligaria a quien llama a saber de antemano cual de las dos usar, y a
 * manejar la carrera entre consultarlo y decidir. Definirlo es idempotente: mandar dos veces
 * lo mismo deja lo mismo.
 *
 * <p>La parte medible es opcional y va junta o no va: {@code meta}, {@code avance} y
 * {@code unidad} se mandan las tres o ninguna. Un objetivo sin numeros es valido (hay metas
 * que no se cuentan); medio numero no lo es. Lo valida {@code MetaCuantitativa}.
 */
public interface DefinirRocaMaestraUseCase {

    RocaMaestra definir(DefinirRocaMaestraCommand command);

    /** Lo mismo con el instante ya decidido (spec 048): lo hecho sin red corre sus reglas con la hora real, no con la de llegada. */
    RocaMaestra definir(DefinirRocaMaestraCommand command, java.time.Instant referencia);

    record DefinirRocaMaestraCommand(@NotNull UserId actorId,
                                      @NotNull EjeObjetivo eje,
                                      @NotBlank @Size(max = RocaMaestra.MAX_OBJETIVO) String objetivo,
                                      BigDecimal meta,
                                      BigDecimal avance,
                                      @Size(max = 20) String unidad,
                                      BigDecimal lineaBase,
                                      boolean desdeElMapa) {

        public DefinirRocaMaestraCommand {
            SelfValidating.validateConstructorArgs(DefinirRocaMaestraCommand.class, actorId, eje, objetivo,
                    meta, avance, unidad, lineaBase, desdeElMapa);
            boolean algunNumero = meta != null || avance != null || unidad != null;
            boolean todosLosNumeros = meta != null && avance != null && unidad != null;
            if (algunNumero && !todosLosNumeros) {
                throw new IllegalArgumentException(
                        "Para una meta medible hacen falta las tres: meta, avance y unidad");
            }
            // `lineaBase` es opcional (E-166), pero sola no significa nada: sin meta no hay camino
            // que medir. Se rechaza para que el error salga acá y no como violación de CHECK.
            if (lineaBase != null && meta == null) {
                throw new IllegalArgumentException(
                        "La linea base solo tiene sentido junto a una meta medible");
            }
        }

        /** {@code true} = el aprendiz mando la parte medible del objetivo. */
        public boolean tieneMeta() {
            return meta != null;
        }
    }
}
