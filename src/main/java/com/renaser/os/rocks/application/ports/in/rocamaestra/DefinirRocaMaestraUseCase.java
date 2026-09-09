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
 * Define el objetivo de 90 dias del aprendiz en un eje, o corrige el que ya tenia.
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
                                      BigDecimal lineaBase) {

        public DefinirRocaMaestraCommand {
            SelfValidating.validateConstructorArgs(DefinirRocaMaestraCommand.class, actorId, eje, objetivo,
                    meta, avance, unidad, lineaBase);
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
