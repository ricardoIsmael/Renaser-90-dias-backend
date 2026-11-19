package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarDiasDeLaPersonaUseCase;
import com.renaser.os.leadership.domain.model.cumplimiento.RangoDeDias;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.DiasDelSemaforoFinder;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Los colores de los días de una persona para el líder (spec 056). Autoriza primero ({@link AccesoDeLiderazgo}: líder, Administración o
 * Alquimista con la cuenta activa; y la persona dentro de sus formaciones) y lee después; los días salen del semáforo de {@code points}
 * tal cual, sin una regla nueva.
 *
 * <p>El rango se recorta a [Día 1, hoy] en la zona de la PERSONA (regla 02), igual que el mentor ve a su aprendiz
 * ({@code DiasDelAprendizService}, spec 055): nada antes de que empezara su programa ni días por venir, y sin los días que están fuera
 * del programa. No deja registro de consulta: lo deja la semana de la ficha ({@link SemanaDeLaPersonaService}).
 */
@Service
public class DiasDeLaPersonaService implements ConsultarDiasDeLaPersonaUseCase {

    private final AccesoDeLiderazgo acceso;
    private final PersonasDelPrograma programa;
    private final DiasDelSemaforoFinder semaforo;
    private final Clock clock;

    DiasDeLaPersonaService(AccesoDeLiderazgo acceso, PersonasDelPrograma programa, DiasDelSemaforoFinder semaforo, Clock clock) {
        this.acceso = acceso;
        this.programa = programa;
        this.semaforo = semaforo;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public DiasDeLaPersona diasDe(ConsultaDiasDeLaPersona consulta) {
        acceso.requireLiderazgoActivo(consulta.actorId());
        RangoDeDias pedido = RangoDeDias.pedido(consulta.desde(), consulta.hasta());
        Instant ahora = clock.now();
        UserId persona = UserId.of(consulta.personaId());
        ParticipacionPrograma participacion = programa.mirarYDecirSiNoSigueSuPrograma(consulta.actorId(), persona, ahora)
                .miembro().participacion();
        LocalDate diaUno = participacion.diaUnoElegido();
        String zona = participacion.zona().getId();
        if (diaUno == null) {
            return new DiasDeLaPersona(zona, null, List.of());
        }
        LocalDate hoy = ahora.atZone(participacion.zona()).toLocalDate();
        List<DiaDelSemaforo> dias = pedido.recortadoA(diaUno, hoy)
                .flatMap(rango -> semaforo.diasDe(persona, rango.desde(), rango.hasta()))
                .orElse(List.of());
        return new DiasDeLaPersona(zona, diaUno,
                dias.stream().filter(dia -> dia.estado() != EstadoDiaSemaforo.FUERA_DEL_PROGRAMA).toList());
    }
}
