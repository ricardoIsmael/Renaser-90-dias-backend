package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.api.EdicionDeObjetivoSemanalPort;
import com.renaser.os.rocks.api.PlanificacionDeRocasPort;
import com.renaser.os.rocks.application.ports.in.rocamaestra.ConsultarRocasMaestrasUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.ConsultarRocasSemanalesUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.EditarDentroDe48hUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.EditarDentroDe48hUseCase.EditarRocaSemanalCommand;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocasemanal.EstadoPlazo;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.rocks.domain.model.rocasemanal.VentanaPlanificacionSemanal;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Implementa {@link EdicionDeObjetivoSemanalPort} (D-177) delegando en {@link EditarDentroDe48hUseCase}.
 * Resuelve la roca semanal del eje con los casos de uso de lectura de la app (que ya corren la guarda
 * de cuenta y programa) y mira la ventana con {@link VentanaPlanificacionSemanal}, la misma regla que el
 * caso de uso vuelve a aplicar adentro.
 */
@Service
class EdicionDeObjetivoSemanalService implements EdicionDeObjetivoSemanalPort {

    private static final Logger log = LoggerFactory.getLogger(EdicionDeObjetivoSemanalService.class);

    private final ConsultarRocasMaestrasUseCase rocasMaestras;
    private final ConsultarRocasSemanalesUseCase rocasSemanales;
    private final EditarDentroDe48hUseCase editar;
    private final ConsultarProgresoParticipanteRocksPort progresoPort;
    private final Clock clock;

    EdicionDeObjetivoSemanalService(ConsultarRocasMaestrasUseCase rocasMaestras,
                                    ConsultarRocasSemanalesUseCase rocasSemanales, EditarDentroDe48hUseCase editar,
                                    ConsultarProgresoParticipanteRocksPort progresoPort, Clock clock) {
        this.rocasMaestras = rocasMaestras;
        this.rocasSemanales = rocasSemanales;
        this.editar = editar;
        this.progresoPort = progresoPort;
        this.clock = clock;
    }

    @Override
    public ResultadoEdicion editar(UserId aprendizId, int numeroSemana, String eje, CambioDelObjetivo cambio) {
        if (cambio == null || cambio.vacio() || eje == null || !PlanificacionDeRocasPort.EJES.contains(eje)) {
            return rechazo(MotivoRechazo.DATOS_INVALIDOS, new IllegalArgumentException("eje o cambio invalido"));
        }
        try {
            Optional<RocaSemanal> roca = rocaDelEje(aprendizId, numeroSemana, EjeObjetivo.valueOf(eje));
            if (roca.isEmpty()) {
                return rechazo(MotivoRechazo.SIN_OBJETIVO_SEMANAL, new IllegalArgumentException("sin objetivo en " + eje));
            }
            if (!editableAhora(aprendizId, roca.get())) {
                return rechazo(MotivoRechazo.VENTANA_CERRADA, new IllegalStateException("ventana cerrada"));
            }
            editar.editar(new EditarRocaSemanalCommand(aprendizId, roca.get().id(), cambio.titulo(),
                    cambio.obstaculo(), cambio.contingencia(), cambio.autoevaluacionInicio()));
            return new ResultadoEdicion.Editado(eje);
        } catch (NoSuchElementException sinParticipante) {
            return rechazo(MotivoRechazo.SIN_PROGRAMA, sinParticipante);
        } catch (NotAuthorizedException sinAcceso) {
            return rechazo(MotivoRechazo.SIN_ACCESO, sinAcceso);
        } catch (IllegalArgumentException | ConstraintViolationException invalido) {
            return rechazo(MotivoRechazo.DATOS_INVALIDOS, invalido);
        }
    }

    private Optional<RocaSemanal> rocaDelEje(UserId aprendizId, int numeroSemana, EjeObjetivo eje) {
        Optional<RocaMaestra> maestra = rocasMaestras.misRocasMaestras(aprendizId).stream()
                .filter(m -> m.eje() == eje).findFirst();
        return maestra.flatMap(m -> rocasSemanales.misRocasSemanales(aprendizId, numeroSemana).stream()
                .filter(roca -> roca.rocaMaestraId().equals(m.id())).findFirst());
    }

    /** Lo mismo que {@code DashboardRocasService.esEditable}: la ventana real de W-03 en la zona de la persona. */
    private boolean editableAhora(UserId aprendizId, RocaSemanal roca) {
        ZoneId zona = progresoPort.deParticipante(aprendizId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + aprendizId)).zona();
        EstadoPlazo plazoAlCrear = VentanaPlanificacionSemanal.plazoAlCrear(roca.creadoEn(), zona);
        return VentanaPlanificacionSemanal.puedeEditar(plazoAlCrear, roca.creadoEn(), clock.now(), zona);
    }

    private static ResultadoEdicion rechazo(MotivoRechazo motivo, RuntimeException causa) {
        log.info("[rocks] edicion de objetivo semanal rechazada por {}: {}", motivo, causa.toString());
        return new ResultadoEdicion.Rechazado(motivo);
    }
}
