package com.renaser.os.leadership.application.services;

import com.renaser.os.habits.api.HorarioDelDiaFinder;
import com.renaser.os.habits.api.HorarioDelDiaFinder.HorarioResuelto;
import com.renaser.os.leadership.application.services.PersonasDelGrupo.Miembro;
import com.renaser.os.leadership.domain.model.cumplimiento.ProgramacionDelDia;
import com.renaser.os.rocks.api.AccionesDelDiaFinder;
import com.renaser.os.rocks.api.AccionesDelDiaFinder.AccionDelDia;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Lee lo que la ficha del líder necesita para decir si una persona «programó» su día (spec 018, D-291): sus hábitos de
 * hoy con la hora que ELLA eligió y sus acciones de hoy con hora de inicio. La regla del «Sí» no vive acá sino en
 * {@link ProgramacionDelDia}.
 *
 * <p>Por APIs públicas, sin reimplementar nada: el horario ya resuelto lo da {@code habits}
 * ({@link HorarioDelDiaFinder}, el mismo que ve la propia persona en Training) y las acciones, {@code rocks}
 * ({@link AccionesDelDiaFinder}, el mismo camino que las acciones de los siete días). «Hoy» es el de la persona, en su
 * zona (regla 02), no el del servidor.
 *
 * <p>Solo lee: no deja registro de consulta (lo deja la ficha) ni escribe nada. Una persona que todavía no activó su
 * programa no tiene un día que programar y no se le pregunta nada al otro módulo.
 */
@Component
class ProgramacionDeLaPersona {

    private final HorarioDelDiaFinder horarios;
    private final AccionesDelDiaFinder acciones;
    private final Clock clock;

    ProgramacionDeLaPersona(HorarioDelDiaFinder horarios, AccionesDelDiaFinder acciones, Clock clock) {
        this.horarios = horarios;
        this.acciones = acciones;
        this.clock = clock;
    }

    /** Vacío si no hay nada que programar: el programa sin activar, o ningún hábito que le toque hoy. */
    Optional<ProgramacionDelDia> deHoy(Miembro miembro) {
        if (!miembro.participacion().activado()) {
            return Optional.empty();
        }
        UserId persona = miembro.perfil().id();
        LocalDate hoy = clock.now().atZone(miembro.participacion().zona()).toLocalDate();
        List<HorarioResuelto> deHoy = habitosQueLeTocan(persona, hoy);
        List<AccionDelDia> accionesDeHoy = acciones.accionesEntre(persona, hoy, hoy);
        return ProgramacionDelDia.de((int) deHoy.stream().filter(HorarioResuelto::personalizado).count(), deHoy.size(),
                (int) accionesDeHoy.stream().filter(AccionDelDia::agendada).count(), accionesDeHoy.size());
    }

    /** Los que de verdad le tocan ese día: ni apagados por ella ni dentro de una pausa de su plan. */
    private List<HorarioResuelto> habitosQueLeTocan(UserId persona, LocalDate dia) {
        return horarios.deFecha(persona, dia).habitos().stream()
                .filter(habito -> !habito.apagado() && !habito.pausado())
                .toList();
    }
}
