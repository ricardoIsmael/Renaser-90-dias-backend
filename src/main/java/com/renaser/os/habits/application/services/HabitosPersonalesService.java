package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.HabitosPersonalesPort;
import com.renaser.os.habits.application.ports.in.habito.CrearHabitoPersonalUseCase;
import com.renaser.os.habits.application.ports.in.habito.CrearHabitoPersonalUseCase.CrearHabitoPersonalCommand;
import com.renaser.os.habits.domain.model.habito.PlantillaHabitoPersonal;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Implementa {@link HabitosPersonalesPort} (D-229). Fachada delgada, mismo patron que
 * {@link PlanDeHabitosService}: arma el mismo comando que {@code MisHabitosController} y delega en
 * {@link CrearHabitoPersonalUseCase}, que trae su propia transaccion y todas sus guardas. Sin
 * {@code @Transactional} propio.
 *
 * <p>CHECKBOX, OTRO, sin icono y sin hora limite: exactamente lo que manda el "Crear habito" de
 * Training ({@code PlanificarDimensionModal}), para que un habito creado por el acompanante no se
 * distinga de uno creado a mano.
 */
@Service
public class HabitosPersonalesService implements HabitosPersonalesPort {

    private final CrearHabitoPersonalUseCase crearUseCase;

    public HabitosPersonalesService(CrearHabitoPersonalUseCase crearUseCase) {
        this.crearUseCase = crearUseCase;
    }

    @Override
    public UUID crear(UserId actorId, HabitoPersonalNuevo nuevo) {
        var comando = new CrearHabitoPersonalCommand(actorId, nuevo.titulo(), TipoHabito.CHECKBOX,
                nuevo.categoriaClave(), PlantillaHabitoPersonal.OTRO, nuevo.meta(), null, nuevo.hora(), null,
                nuevo.dias());
        return crearUseCase.crear(comando).id().value();
    }
}
