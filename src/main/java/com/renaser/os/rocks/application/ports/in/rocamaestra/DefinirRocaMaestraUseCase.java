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
 * <p><b>Fija desde que el Mapa esta activo, no desde que se definio (D-284, 2026-10-27).</b> El Mapa de
 * Renacimiento es opcional y Plan deja definir el objetivo antes, pero esa roca provisional no puede dejar
 * sin activar al Mapa (issue #20). Segun el estado del Mapa de la persona:
 * <ul>
 *   <li><b>Mapa activo:</b> rige D-234 de arriba, venga de donde venga el pedido.
 *   <li><b>Mapa sin activar y {@code desdeElMapa}:</b> es la activacion escribiendo lo que la persona puso en
 *       su Mapa; reemplaza a la roca que hubiera (objetivo, meta, unidad, punto de partida y avance).
 *   <li><b>Mapa sin activar y no viene del Mapa:</b> con {@code renaser.rocks.exigir-mapa-activo} apagado
 *       (el default, para que el APK que todavia no manda {@code desdeElMapa} pueda activar su Mapa)
 *       se comporta como el caso anterior; encendido, solo acepta repetir la misma definicion y cualquier
 *       otra cosa lanza {@code MapaPendienteException} (409 {@code MAPA_PENDIENTE}).
 * </ul>
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
