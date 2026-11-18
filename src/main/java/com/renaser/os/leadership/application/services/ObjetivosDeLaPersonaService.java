package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarObjetivosDeLaPersonaUseCase;
import com.renaser.os.rocks.api.RocasDelAprendizFinder;
import com.renaser.os.rocks.api.RocasDelAprendizFinder.DiaDeLaSemana;
import com.renaser.os.rocks.api.RocasDelAprendizFinder.ObjetivoDelMesDelEje;
import com.renaser.os.rocks.api.RocasDelAprendizFinder.RocasDeLaSemana;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Los objetivos de una persona para el líder (spec 056, FR-027). Autoriza primero ({@link AccesoDeLiderazgo}: líder, Administración o
 * Alquimista con la cuenta activa; y la persona dentro de sus formaciones) y lee después, por {@link RocasDelAprendizFinder}: el mismo
 * contrato con el que {@code rag} lee las rocas de un aprendiz, que ya resuelve la zona, el mes y la semana de la persona. Aquí solo se
 * junta y se le da forma; no hay ninguna regla ni cálculo propio salvo sumar las acciones de la semana.
 */
@Service
public class ObjetivosDeLaPersonaService implements ConsultarObjetivosDeLaPersonaUseCase {

    private final AccesoDeLiderazgo acceso;
    private final PersonasDelPrograma programa;
    private final RocasDelAprendizFinder rocas;
    private final Clock clock;

    ObjetivosDeLaPersonaService(AccesoDeLiderazgo acceso, PersonasDelPrograma programa, RocasDelAprendizFinder rocas, Clock clock) {
        this.acceso = acceso;
        this.programa = programa;
        this.rocas = rocas;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ObjetivosDeLaPersona objetivosDe(ConsultaObjetivosDeLaPersona consulta) {
        acceso.requireLiderazgoActivo(consulta.actorId());
        UserId persona = programa.mirarYDecirSiNoSigueSuPrograma(consulta.actorId(), UserId.of(consulta.personaId()), clock.now())
                .personaId();
        return new ObjetivosDeLaPersona(rocas.objetivosDeNoventaDias(persona), delMes(persona), deLaSemana(persona));
    }

    private ObjetivosDelMes delMes(UserId persona) {
        List<ObjetivoDelMesDelEje> delMes = rocas.delMes(persona);
        return delMes.isEmpty() ? null
                : new ObjetivosDelMes(delMes.getFirst().numeroMes(), delMes.getFirst().diaDeCierre(), delMes);
    }

    private ObjetivosDeLaSemana deLaSemana(UserId persona) {
        RocasDeLaSemana semana = rocas.deLaSemana(persona);
        if (semana.rocas().isEmpty()) {
            return null;
        }
        List<DiaDeLaSemana> dias = rocas.progresoDeLaSemana(persona).dias();
        return new ObjetivosDeLaSemana(semana.numeroSemana(), semana.inicio(), semana.fin(),
                semana.rocas().stream().map(roca -> new ObjetivoDeLaSemana(roca.eje(), roca.titulo())).toList(),
                suma(dias, DiaDeLaSemana::total), suma(dias, DiaDeLaSemana::completadas));
    }

    /** Los días que no llegaron traen null: no suman. */
    private static int suma(List<DiaDeLaSemana> dias, Function<DiaDeLaSemana, Integer> valor) {
        return dias.stream().map(valor).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
    }
}
