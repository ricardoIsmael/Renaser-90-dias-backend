package com.renaser.os.habits.infrastructure.adapter.out.persistence.diario;

import com.renaser.os.habits.api.EntradaDiarioFinder;
import com.renaser.os.habits.api.EntradaDiarioSummary;
import com.renaser.os.habits.application.ports.out.diario.LoadEntradaDiarioPort;
import com.renaser.os.habits.application.ports.out.diario.SaveEntradaDiarioPort;
import com.renaser.os.habits.domain.model.diario.EntradaDiario;
import com.renaser.os.habits.domain.model.diario.TipoEntradaDiario;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Persistencia de `entradas_diario`. Los puertos {@code Load}/{@code Save} existian desde que
 * se construyo `habits` pero NADIE los implementaba (por eso la app arrancaba igual: Spring
 * nunca necesitaba cablearlos). Se implementan ahora porque el Espejo Sombra de `rag` necesita
 * leer las entradas de la semana (D-50).
 *
 * <p>Implementa ademas {@link EntradaDiarioFinder}, el contrato PUBLICO hacia otros modulos —
 * mismo patron que {@code ParticipacionProgramaService} de `users`, que implementa a la vez sus
 * puertos internos y el finder de su {@code api}.
 */
@Component
class EntradaDiarioPersistenceAdapter implements LoadEntradaDiarioPort, SaveEntradaDiarioPort, EntradaDiarioFinder {

    private final SpringDataEntradaDiarioRepository repository;

    EntradaDiarioPersistenceAdapter(SpringDataEntradaDiarioRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<EntradaDiario> porParticipanteFechaYTipo(UserId participanteId, LocalDate fecha,
                                                              TipoEntradaDiario tipo) {
        return repository
                .findByParticipanteIdAndFechaAndTipo(participanteId.value(), fecha,
                        TipoEntradaDiarioJpa.valueOf(tipo.name()))
                .map(EntradaDiarioPersistenceMapper::aDominio);
    }

    @Override
    @Transactional
    public EntradaDiario save(EntradaDiario entrada) {
        EntradaDiarioJpaEntity guardada = repository.save(EntradaDiarioPersistenceMapper.aEntidad(entrada));
        return EntradaDiarioPersistenceMapper.aDominio(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EntradaDiarioSummary> entradasEntre(UserId participanteId, LocalDate inicio, LocalDate fin) {
        return repository
                .findByParticipanteIdAndFechaBetweenOrderByFechaAsc(participanteId.value(), inicio, fin).stream()
                .map(EntradaDiarioPersistenceMapper::aResumen)
                .toList();
    }
}
