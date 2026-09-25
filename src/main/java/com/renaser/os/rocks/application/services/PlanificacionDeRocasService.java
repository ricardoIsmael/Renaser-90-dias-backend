package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.api.PlanificacionDeRocasPort;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CrearPlanDiarioUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CrearPlanDiarioUseCase.CrearPlanDiarioCommand;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CrearPlanDiarioUseCase.ItemRocaDiaria;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase.CrearPlanSemanalCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase.ItemRocaSemanal;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.IntSupplier;

/**
 * Implementa {@link PlanificacionDeRocasPort} (2026-09-23) delegando en {@link CrearPlanDiarioUseCase}
 * y {@link CrearPlanSemanalUseCase}, los mismos que sirven a la app. Solo traduce: los tipos de
 * {@code rocks.api} al comando, y el rechazo del caso de uso a un {@link MotivoRechazo}.
 *
 * <p><b>Sin {@code @Transactional} propio, a proposito.</b> La transaccion es la del caso de uso: si
 * rechaza, ya hizo rollback cuando la excepcion llega aca, y atraparla no deja nada a medias.
 */
@Service
class PlanificacionDeRocasService implements PlanificacionDeRocasPort {

    private static final Logger log = LoggerFactory.getLogger(PlanificacionDeRocasService.class);

    private final CrearPlanDiarioUseCase crearPlanDiario;
    private final CrearPlanSemanalUseCase crearPlanSemanal;

    PlanificacionDeRocasService(CrearPlanDiarioUseCase crearPlanDiario, CrearPlanSemanalUseCase crearPlanSemanal) {
        this.crearPlanDiario = crearPlanDiario;
        this.crearPlanSemanal = crearPlanSemanal;
    }

    @Override
    public ResultadoPlanificacion crearPlanDelDia(UserId aprendizId, LocalDate fecha, List<AccionDelDia> acciones) {
        return traduciendoRechazos(() -> crearPlanDiario.crear(new CrearPlanDiarioCommand(aprendizId, fecha,
                acciones.stream().map(PlanificacionDeRocasService::aItemDiario).toList())).size());
    }

    @Override
    public ResultadoPlanificacion crearPlanDeLaSemana(UserId aprendizId, List<ObjetivoDeLaSemana> objetivos) {
        return traduciendoRechazos(() -> crearPlanSemanal.crear(new CrearPlanSemanalCommand(aprendizId,
                objetivos.stream().map(PlanificacionDeRocasService::aItemSemanal).toList())).size());
    }

    /** Sin acciones internas ni descripcion: el acompanante agenda acciones con su titulo, igual que la app. */
    private static ItemRocaDiaria aItemDiario(AccionDelDia accion) {
        return new ItemRocaDiaria(ejeDe(accion.eje()), accion.posicion(), accion.titulo(), null,
                accion.puntajeImpacto(), accion.esDelegable(), accion.horaInicio(), accion.horaFin(), List.of());
    }

    private static ItemRocaSemanal aItemSemanal(ObjetivoDeLaSemana objetivo) {
        return new ItemRocaSemanal(ejeDe(objetivo.eje()), objetivo.titulo(), objetivo.obstaculo(),
                objetivo.contingencia(), null);
    }

    /** {@code null} si no viene: el item lo rechaza con su propio mensaje. Un nombre inventado, {@code IllegalArgumentException}. */
    private static EjeObjetivo ejeDe(String eje) {
        return eje == null ? null : EjeObjetivo.valueOf(eje);
    }

    private static ResultadoPlanificacion traduciendoRechazos(IntSupplier crear) {
        try {
            return new ResultadoPlanificacion.Creado(crear.getAsInt());
        } catch (NoSuchElementException sinParticipante) {
            return rechazo(MotivoRechazo.SIN_PROGRAMA, sinParticipante);
        } catch (NotAuthorizedException sinAcceso) {
            return rechazo(tieneCodigo(sinAcceso, "ROCKS_LOCKED") ? MotivoRechazo.ROCAS_BLOQUEADAS
                    : MotivoRechazo.SIN_ACCESO, sinAcceso);
        } catch (IllegalStateException conflicto) {
            if (!tieneCodigo(conflicto, "ALREADY_PLANNED")) {
                throw conflicto;
            }
            return rechazo(MotivoRechazo.YA_PLANIFICADO, conflicto);
        } catch (IllegalArgumentException | ConstraintViolationException invalido) {
            return rechazo(motivoDe(invalido), invalido);
        }
    }

    private static MotivoRechazo motivoDe(RuntimeException invalido) {
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

    private static ResultadoPlanificacion rechazo(MotivoRechazo motivo, RuntimeException causa) {
        log.info("[rocks] plan rechazado por {}: {}", motivo, causa.toString());
        return new ResultadoPlanificacion.Rechazado(motivo);
    }
}
