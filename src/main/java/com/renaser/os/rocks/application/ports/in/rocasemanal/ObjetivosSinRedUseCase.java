package com.renaser.os.rocks.application.ports.in.rocasemanal;

import com.renaser.os.rocks.application.ports.in.rocamaestra.DefinirRocaMaestraUseCase.DefinirRocaMaestraCommand;
import com.renaser.os.rocks.application.ports.in.rocamensual.DefinirRocaMensualUseCase.DefinirRocaMensualCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CerrarSemanaUseCase.CerrarSemanaCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase.CrearPlanSemanalCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.EditarDentroDe48hUseCase.EditarRocaSemanalCommand;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensual;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.shared.domain.HechoSinRed;

import java.util.List;

/**
 * Spec 048 (FR-019, fase 3): los objetivos del Plan hechos SIN RED (semana, su revisión, objetivo de 90 días y del mes), con la hora real
 * en que se hicieron. Mismas reglas que con red corridas con esa hora; en un choque gana lo más nuevo (D13). Un mismo {@code idCliente}
 * se registra una sola vez. Cada método devuelve lo mismo que su equivalente con red.
 *
 * <p>Todos pueden terminar en un {@link com.renaser.os.shared.domain.NoAplicadoSinRedException} (409 con {@code codigo}):
 * {@code CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR}, {@code YA_ESTABA_HECHO} (la semana ya estaba planificada) o {@code RECURSO_YA_NO_EXISTE}.
 */
public interface ObjetivosSinRedUseCase {

    List<RocaSemanal> crearSemana(CrearPlanSemanalCommand command, HechoSinRed sinRed);

    RocaSemanal editarSemanal(EditarRocaSemanalCommand command, HechoSinRed sinRed);

    RocaSemanal cerrarSemana(CerrarSemanaCommand command, HechoSinRed sinRed);

    RocaMaestra definirObjetivoDe90Dias(DefinirRocaMaestraCommand command, HechoSinRed sinRed);

    RocaMensual definirObjetivoDelMes(DefinirRocaMensualCommand command, HechoSinRed sinRed);
}
