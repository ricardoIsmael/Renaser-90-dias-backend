package com.renaser.os.rocks.application.ports.in.rocamensual;

import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamensual.MesPrograma;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensual;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Define el tramo mensual de un eje —que tiene que estar logrado al cierre del mes 1, 2 o 3— o
 * corrige el que ya estaba.
 *
 * <p><b>Una sola operacion, igual que en la Roca Maestra</b>, y por el mismo motivo: la tabla
 * impone {@code UNIQUE (roca_maestra_id, numero_mes)}, asi que por (eje, mes) hay exactamente un
 * objetivo mensual o ninguno. Partirlo en "crear" y "editar" obligaria a quien llama a saber de
 * antemano cual usar y a manejar la carrera entre consultarlo y decidir.
 *
 * <p><b>El eje viaja, no la Roca Maestra.</b> El cliente conoce sus tres ejes; los UUID de las
 * maestras son un detalle interno que no tiene por que manejar, y aceptarlos desde afuera abriria
 * la puerta a mandar el id de la maestra de otra persona. El caso de uso resuelve la maestra a
 * partir de (actor, eje).
 *
 * <p>La parte medible es opcional y va junta o no va: {@code meta}, {@code avance} y
 * {@code unidad} se mandan las tres o ninguna. Un tramo sin numeros es valido (hay metas que no se
 * cuentan); medio numero no lo es.
 */
public interface DefinirRocaMensualUseCase {

    RocaMensual definir(DefinirRocaMensualCommand command);

    record DefinirRocaMensualCommand(@NotNull UserId actorId,
                                      @NotNull EjeObjetivo eje,
                                      @Min(1) @Max(MesPrograma.MESES) int numeroMes,
                                      @NotBlank @Size(max = RocaMensual.MAX_TITULO) String titulo,
                                      BigDecimal meta,
                                      BigDecimal avance,
                                      @Size(max = 20) String unidad) {

        public DefinirRocaMensualCommand {
            SelfValidating.validateConstructorArgs(DefinirRocaMensualCommand.class, actorId, eje, numeroMes,
                    titulo, meta, avance, unidad);
            boolean algunNumero = meta != null || avance != null || unidad != null;
            boolean todosLosNumeros = meta != null && avance != null && unidad != null;
            if (algunNumero && !todosLosNumeros) {
                throw new IllegalArgumentException(
                        "Para una meta medible hacen falta las tres: meta, avance y unidad");
            }
        }

        /** {@code true} = el aprendiz mando la parte medible del tramo. */
        public boolean tieneMeta() {
            return meta != null;
        }
    }
}
