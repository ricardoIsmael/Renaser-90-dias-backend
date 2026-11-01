package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.categoria.ConsultarCategoriasDeMiFormacionUseCase;
import com.renaser.os.habits.application.ports.out.categoria.LoadCategoriaHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Las dimensiones de Training de la persona (spec 023, FR-009): las categorias ACTIVAS de su formacion, en el orden que Administracion les
 * dio. La formacion la decide el servidor —la de su programa, o la que tiene activa si el personal no hace programa—; pedir otra solo lo
 * puede quien ve las tres. Una categoria sin habitos que le toquen hoy a la persona no se dibuja: eso lo resuelve la app con su lista de hoy.
 */
@Service
public class CategoriasDeLaFormacionService implements ConsultarCategoriasDeMiFormacionUseCase {

    private final LoadCategoriaHabitoPort loadPort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final AlcanceDeFormacionFinder alcanceFinder;

    public CategoriasDeLaFormacionService(LoadCategoriaHabitoPort loadPort, ConsultarProgresoParticipanteHabitsPort progresoPort,
                                          AlcanceDeFormacionFinder alcanceFinder) {
        this.loadPort = loadPort;
        this.progresoPort = progresoPort;
        this.alcanceFinder = alcanceFinder;
    }

    @Override
    public CategoriasDeLaFormacion consultar(UserId actorId, Formacion formacionPedida) {
        AlcanceDeFormacion alcance = alcanceFinder.de(actorId);
        Optional<Formacion> formacion = formacionPedida != null
                ? Optional.of(alcance.acotadoA(formacionPedida).unica().orElseThrow(
                        com.renaser.os.shared.domain.ObjetoDeOtraFormacionException::new))
                : formacionDeLaPersona(actorId, alcance);
        return new CategoriasDeLaFormacion(formacion,
                formacion.map(loadPort::activasDe).orElse(List.of()));
    }

    private Optional<Formacion> formacionDeLaPersona(UserId actorId, AlcanceDeFormacion alcance) {
        Optional<Formacion> delPrograma = progresoPort.deParticipante(actorId).map(ProgresoParticipanteHabits::formacion);
        return delPrograma.isPresent() ? delPrograma : alcance.unica();
    }
}
