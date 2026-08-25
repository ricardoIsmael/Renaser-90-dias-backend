package com.renaser.os.community.application.ports.in.celula;

import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.cohorte.Cohorte;
import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.PerfilBasico;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

/**
 * CM-01: la celula del aprendiz que pregunta. Solo TRAINEE (app/api/v1/me/cell/route.ts:26)
 * — a diferencia del ranking, este endpoint viejo nunca tuvo rama para MENTOR.
 *
 * <p>Sin celula NO es un error (misma app/api/v1/me/cell/route.ts:32-34): {@link Optional#empty()}
 * y el controller responde 404 {@code {assigned:false}}, no una excepcion.
 */
public interface ConsultarMiCelulaUseCase {

    Optional<MiCelula> miCelula(UserId traineeId);

    List<PerfilBasico> misCompaneros(UserId traineeId);

    record MiCelula(Celula celula, Cohorte cohorte, PerfilBasico mentor, int cantidadMiembros,
                     int totalCelulasEnCohorte) {
    }
}
