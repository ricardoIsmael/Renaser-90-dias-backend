package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.api.CierreDeSemanaPort;
import com.renaser.os.rocks.api.PlanificacionDeRocasPort;
import com.renaser.os.rocks.application.ports.in.rocamaestra.ConsultarRocasMaestrasUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CerrarSemanaUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CerrarSemanaUseCase.CerrarSemanaCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.ConsultarRocasSemanalesUseCase;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementa {@link CierreDeSemanaPort} (2026-09-23) delegando en {@link CerrarSemanaUseCase}, el mismo
 * de {@code PATCH /rocks/weekly/{id}/review}. Para saber que roca es la de cada eje usa
 * {@link ConsultarRocasSemanalesUseCase} y {@link ConsultarRocasMaestrasUseCase}, que ya corren las
 * guardas de cuenta activa y programa andando.
 *
 * <p>Arma y valida TODOS los comandos antes de escribir el primero: un eje sin objetivo, ya revisado
 * o con datos invalidos rechaza el cierre entero y no deja ejes cerrados a medias.
 *
 * <p><b>Sin {@code @Transactional} propio</b>, igual que {@link PlanificacionDeRocasService}: cada
 * revision es la transaccion del caso de uso.
 */
@Service
class CierreDeSemanaService implements CierreDeSemanaPort {

    private static final Logger log = LoggerFactory.getLogger(CierreDeSemanaService.class);

    private final ConsultarRocasMaestrasUseCase rocasMaestras;
    private final ConsultarRocasSemanalesUseCase rocasSemanales;
    private final CerrarSemanaUseCase cerrarSemana;

    CierreDeSemanaService(ConsultarRocasMaestrasUseCase rocasMaestras, ConsultarRocasSemanalesUseCase rocasSemanales,
                          CerrarSemanaUseCase cerrarSemana) {
        this.rocasMaestras = rocasMaestras;
        this.rocasSemanales = rocasSemanales;
        this.cerrarSemana = cerrarSemana;
    }

    @Override
    public ResultadoCierre cerrarSemana(UserId aprendizId, int numeroSemana, List<RevisionDelEje> revisiones) {
        try {
            List<CerrarSemanaCommand> comandos = comandosValidados(aprendizId,
                    rocasDeLaSemanaPorEje(aprendizId, numeroSemana), revisiones);
            comandos.forEach(cerrarSemana::cerrar);
            return new ResultadoCierre.Cerrada(revisiones.stream().map(RevisionDelEje::eje).toList());
        } catch (NoSuchElementException sinParticipante) {
            return rechazo(MotivoRechazo.SIN_PROGRAMA, sinParticipante);
        } catch (NotAuthorizedException sinAcceso) {
            return rechazo(MotivoRechazo.SIN_ACCESO, sinAcceso);
        } catch (CierreImposibleException imposible) {
            return rechazo(imposible.motivo, imposible);
        } catch (IllegalArgumentException | ConstraintViolationException invalido) {
            return rechazo(MotivoRechazo.DATOS_INVALIDOS, invalido);
        }
    }

    /** La roca semanal de cada eje en esa semana, por el nombre del eje. */
    private Map<String, RocaSemanal> rocasDeLaSemanaPorEje(UserId aprendizId, int numeroSemana) {
        Map<RocaMaestraId, EjeObjetivo> ejePorMaestra = rocasMaestras.misRocasMaestras(aprendizId).stream()
                .collect(Collectors.toMap(RocaMaestra::id, RocaMaestra::eje, (a, b) -> a));
        Map<String, RocaSemanal> porEje = new HashMap<>();
        for (RocaSemanal roca : rocasSemanales.misRocasSemanales(aprendizId, numeroSemana)) {
            EjeObjetivo eje = ejePorMaestra.get(roca.rocaMaestraId());
            if (eje != null) {
                porEje.putIfAbsent(eje.name(), roca);
            }
        }
        return porEje;
    }

    private static List<CerrarSemanaCommand> comandosValidados(UserId aprendizId, Map<String, RocaSemanal> porEje,
                                                               List<RevisionDelEje> revisiones) {
        requireUnEjePorRevision(revisiones);
        List<CerrarSemanaCommand> comandos = new ArrayList<>();
        for (RevisionDelEje revision : revisiones) {
            RocaSemanal roca = rocaSinRevisar(porEje, revision.eje());
            comandos.add(new CerrarSemanaCommand(aprendizId, roca.id(), revision.autoevaluacionFin(),
                    revision.bloqueoPrincipal(), revision.correccion()));
        }
        return List.copyOf(comandos);
    }

    private static void requireUnEjePorRevision(List<RevisionDelEje> revisiones) {
        Set<String> ejes = revisiones.stream().map(RevisionDelEje::eje).collect(Collectors.toSet());
        if (revisiones.isEmpty() || ejes.size() != revisiones.size()) {
            throw new CierreImposibleException(MotivoRechazo.DATOS_INVALIDOS, "se pide una revision por eje, sin repetir");
        }
    }

    private static RocaSemanal rocaSinRevisar(Map<String, RocaSemanal> porEje, String eje) {
        if (eje == null || !PlanificacionDeRocasPort.EJES.contains(eje)) {
            throw new CierreImposibleException(MotivoRechazo.DATOS_INVALIDOS, "eje inexistente: " + eje);
        }
        RocaSemanal roca = porEje.get(eje);
        if (roca == null) {
            throw new CierreImposibleException(MotivoRechazo.SIN_OBJETIVO_SEMANAL, "sin objetivo semanal en " + eje);
        }
        if (roca.autoevaluacionFin() != null) {
            throw new CierreImposibleException(MotivoRechazo.YA_REVISADA, "ya revisada: " + roca.id());
        }
        return roca;
    }

    private static ResultadoCierre rechazo(MotivoRechazo motivo, RuntimeException causa) {
        log.info("[rocks] cierre de semana rechazado por {}: {}", motivo, causa.toString());
        return new ResultadoCierre.Rechazado(motivo);
    }

    /** Un rechazo decidido aca, antes de escribir nada. No sale de esta clase. */
    private static final class CierreImposibleException extends RuntimeException {

        private final MotivoRechazo motivo;

        CierreImposibleException(MotivoRechazo motivo, String detalle) {
            super(detalle);
            this.motivo = motivo;
        }
    }
}
