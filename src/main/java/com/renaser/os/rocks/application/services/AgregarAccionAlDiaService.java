package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.api.AgregarAccionAlDiaPort;
import com.renaser.os.rocks.application.ports.in.rocadiaria.AgregarRocaDiariaUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.AgregarRocaDiariaUseCase.AgregarRocaDiariaCommand;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.NoSuchElementException;

/**
 * Implementa {@link AgregarAccionAlDiaPort} (D-177) delegando en {@link AgregarRocaDiariaUseCase}. Solo
 * traduce: el pedido al comando, y los codigos de rechazo del caso de uso a {@link MotivoRechazo}, para
 * que ningun otro modulo tenga que leer el texto de una excepcion ajena. Mismo patron que
 * {@link PlanificacionDeRocasService}; sin {@code @Transactional} propio por la misma razon.
 */
@Service
class AgregarAccionAlDiaService implements AgregarAccionAlDiaPort {

    private static final Logger log = LoggerFactory.getLogger(AgregarAccionAlDiaService.class);

    private final AgregarRocaDiariaUseCase agregarRocaDiaria;

    AgregarAccionAlDiaService(AgregarRocaDiariaUseCase agregarRocaDiaria) {
        this.agregarRocaDiaria = agregarRocaDiaria;
    }

    @Override
    public ResultadoAgregado agregar(UserId aprendizId, LocalDate fecha, AccionNueva accion) {
        try {
            RocaDiaria agregada = agregarRocaDiaria.agregar(new AgregarRocaDiariaCommand(aprendizId, fecha,
                    ejeDe(accion.eje()), accion.titulo(), accion.puntajeImpacto(), accion.esDelegable(),
                    accion.horaInicio(), accion.horaFin()));
            return new ResultadoAgregado.Agregada(agregada.eje().name(), agregada.posicion(), agregada.color().name());
        } catch (NoSuchElementException sinParticipante) {
            return rechazo(MotivoRechazo.SIN_PROGRAMA, sinParticipante);
        } catch (NotAuthorizedException sinAcceso) {
            return rechazo(tieneCodigo(sinAcceso, "ROCKS_LOCKED") ? MotivoRechazo.ROCAS_BLOQUEADAS
                    : MotivoRechazo.SIN_ACCESO, sinAcceso);
        } catch (IllegalStateException sinLugar) {
            return rechazo(motivoDeConflicto(sinLugar), sinLugar);
        } catch (IllegalArgumentException | ConstraintViolationException invalido) {
            return rechazo(motivoDeInvalido(invalido), invalido);
        }
    }

    /** {@code null} si no viene: el comando lo rechaza. Un nombre inventado, {@code IllegalArgumentException}. */
    private static EjeObjetivo ejeDe(String eje) {
        return eje == null ? null : EjeObjetivo.valueOf(eje);
    }

    private static MotivoRechazo motivoDeConflicto(IllegalStateException conflicto) {
        if (tieneCodigo(conflicto, "CURRENT_DAY")) {
            return MotivoRechazo.DIA_EN_CURSO;
        }
        if (tieneCodigo(conflicto, "AXIS_FULL")) {
            return MotivoRechazo.EJE_COMPLETO;
        }
        if (tieneCodigo(conflicto, "DAY_FULL")) {
            return MotivoRechazo.DIA_COMPLETO;
        }
        throw conflicto;
    }

    private static MotivoRechazo motivoDeInvalido(RuntimeException invalido) {
        if (tieneCodigo(invalido, "INVALID_DATE")) {
            return MotivoRechazo.FECHA_NO_PLANIFICABLE;
        }
        if (tieneCodigo(invalido, "NO_WEEKLY_ROCK")) {
            return MotivoRechazo.SIN_OBJETIVO_SEMANAL;
        }
        return MotivoRechazo.DATOS_INVALIDOS;
    }

    private static boolean tieneCodigo(RuntimeException excepcion, String codigo) {
        return excepcion.getMessage() != null && excepcion.getMessage().startsWith(codigo);
    }

    private static ResultadoAgregado rechazo(MotivoRechazo motivo, RuntimeException causa) {
        log.info("[rocks] accion no agregada por {}: {}", motivo, causa.toString());
        return new ResultadoAgregado.Rechazado(motivo);
    }
}
