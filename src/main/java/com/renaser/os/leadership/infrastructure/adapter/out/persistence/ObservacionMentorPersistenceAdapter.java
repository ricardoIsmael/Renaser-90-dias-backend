package com.renaser.os.leadership.infrastructure.adapter.out.persistence;

import com.renaser.os.leadership.application.ports.in.ConsultarReporteDeMentoresUseCase.ConteoDeObservaciones;
import com.renaser.os.leadership.application.ports.out.GuardarObservacionPort;
import com.renaser.os.leadership.application.ports.out.LeerObservacionesPort;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor.Contenido;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor.Envio;
import com.renaser.os.leadership.domain.model.observacion.TipoObservacion;
import com.renaser.os.leadership.infrastructure.adapter.out.persistence.SpringDataObservacionMentorRepository.ConteoPorTipo;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code observaciones_mentor} (V88). Solo {@code persist}, nunca {@code merge}: un id repetido no puede
 * terminar en un UPDATE silencioso de una fila append-only. La {@code (autor, clave)} repetida la ataja
 * el caso de uso antes de insertar; el indice unico de la base cubre la carrera.
 */
@Component
class ObservacionMentorPersistenceAdapter implements GuardarObservacionPort, LeerObservacionesPort {

    private final SpringDataObservacionMentorRepository repository;
    private final EntityManager entityManager;

    ObservacionMentorPersistenceAdapter(SpringDataObservacionMentorRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public ObservacionDeMentor insertar(ObservacionDeMentor observacion) {
        try {
            entityManager.persist(aEntidad(observacion));
            entityManager.flush();
            return observacion;
        } catch (DataIntegrityViolationException | PersistenceException e) {
            /* Dos toques casi juntos con la misma clave: el caso de uso ya busco la clave antes de
               insertar, asi que esto es la carrera. La transaccion quedo marcada para rollback y no se
               puede releer aca; 409 y el reintento del cliente encuentra la que gano. */
            throw new IllegalStateException("Esa observacion ya se esta guardando: vuelve a intentarlo", e);
        }
    }

    @Override
    public Optional<ObservacionDeMentor> porAutorYClave(UserId autorId, String claveOperacion) {
        return repository.findByAutorIdAndClaveOperacion(autorId.value(), claveOperacion).map(this::aDominio);
    }

    @Override
    public List<ObservacionDeMentor> deMentor(UserId mentorId, Instant antesDe, int limite) {
        List<ObservacionMentorJpaEntity> filas = antesDe == null
                ? repository.findByMentorIdOrderByCreadoEnDescIdDesc(mentorId.value(), Limit.of(limite))
                : repository.findByMentorIdAndCreadoEnBeforeOrderByCreadoEnDescIdDesc(mentorId.value(), antesDe,
                        Limit.of(limite));
        return filas.stream().map(this::aDominio).toList();
    }

    @Override
    public Map<UserId, ConteoDeObservaciones> conteoPorMentor(Instant desde, Instant hasta) {
        Map<UserId, ConteoDeObservaciones> conteos = new HashMap<>();
        for (ConteoPorTipo fila : repository.contarPorMentorYTipo(desde, hasta)) {
            conteos.merge(UserId.of(fila.getMentorId()), conteoDe(fila), OBSERVACIONES_SUMADAS);
        }
        return conteos;
    }

    private static final java.util.function.BinaryOperator<ConteoDeObservaciones> OBSERVACIONES_SUMADAS =
            (a, b) -> new ConteoDeObservaciones(a.reconocimientos() + b.reconocimientos(),
                    a.sugerencias() + b.sugerencias(), a.alertas() + b.alertas());

    private static ConteoDeObservaciones conteoDe(ConteoPorTipo fila) {
        int cantidad = Math.toIntExact(fila.getCantidad());
        return switch (TipoObservacion.de(fila.getTipo())) {
            case RECONOCIMIENTO -> new ConteoDeObservaciones(cantidad, 0, 0);
            case SUGERENCIA -> new ConteoDeObservaciones(0, cantidad, 0);
            case ALERTA -> new ConteoDeObservaciones(0, 0, cantidad);
        };
    }

    private ObservacionMentorJpaEntity aEntidad(ObservacionDeMentor o) {
        return new ObservacionMentorJpaEntity(o.id(), o.mentorId().value(), o.autorId().value(), o.tipo().name(),
                o.texto(), o.enviadaPorChat(), o.mensajeId(), o.claveOperacion(), o.creadoEn());
    }

    private ObservacionDeMentor aDominio(ObservacionMentorJpaEntity e) {
        return ObservacionDeMentor.rehydrate(e.getId(), new Contenido(UserId.of(e.getMentorId()),
                UserId.of(e.getAutorId()), TipoObservacion.de(e.getTipo()), e.getTexto(),
                new Envio(e.isEnviadaPorChat(), e.getMensajeId()), e.getClaveOperacion()), e.getCreadoEn());
    }
}
