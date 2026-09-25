package com.renaser.os.rag.infrastructure.adapter.out.persistence.memoria;

import com.renaser.os.rag.application.ports.out.memoria.MemoriaDeRenasiaPort;
import com.renaser.os.rag.domain.model.memoria.CategoriaDeRecuerdo;
import com.renaser.os.rag.domain.model.memoria.MemoriaDeRenasia;
import com.renaser.os.rag.domain.model.memoria.Recuerdo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code recuerdos_renasia} y {@code memorias_renasia} (V67). Mapper a mano: la categoria viaja como
 * texto y se valida al volver ({@code CategoriaDeRecuerdo.valueOf}); el resumen puede ser NULL.
 *
 * <p>Las escrituras toman un candado de Postgres por persona ({@code pg_advisory_xact_lock}, se
 * suelta solo al terminar la transaccion): sin el, un borrado desde el perfil que cayera entre la
 * comparacion y la escritura de {@link #reemplazar} quedaria pisado, y lo borrado volveria. Es un
 * candado sobre una clave, no sobre filas: sirve aunque la persona todavia no tenga fila.
 */
@Component
class MemoriaDeRenasiaPersistenceAdapter implements MemoriaDeRenasiaPort {

    private final SpringDataRecuerdoRenasiaRepository recuerdos;
    private final SpringDataMemoriaRenasiaRepository memorias;
    private final JdbcClient jdbcClient;
    private final Clock clock;

    MemoriaDeRenasiaPersistenceAdapter(SpringDataRecuerdoRenasiaRepository recuerdos,
                                       SpringDataMemoriaRenasiaRepository memorias, JdbcClient jdbcClient,
                                       Clock clock) {
        this.recuerdos = recuerdos;
        this.memorias = memorias;
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public MemoriaDeRenasia de(UserId participanteId) {
        return leer(participanteId.value());
    }

    @Override
    @Transactional
    public boolean reemplazar(UserId participanteId, MemoriaDeRenasia antes, MemoriaDeRenasia nueva) {
        UUID id = participanteId.value();
        bloquear(id);
        if (!leer(id).equals(antes)) {
            return false;
        }
        reemplazarRecuerdos(id, antes.recuerdos(), nueva.recuerdos());
        memorias.save(new MemoriaRenasiaJpaEntity(id, nueva.resumen().orElse(null), nueva.compactadoHasta(),
                clock.now()));
        return true;
    }

    /**
     * Borra los que ya no estan y agrega los nuevos; los que siguen igual (mismo id) no se tocan.
     * Nada de borrar todo y volver a insertar: las filas que {@link #leer} dejo en el contexto de
     * persistencia quedarian desfasadas y el reinsertado con el mismo id terminaria en un UPDATE a
     * una fila que ya no existe.
     */
    private void reemplazarRecuerdos(UUID id, List<Recuerdo> antes, List<Recuerdo> nuevos) {
        Set<UUID> quedan = nuevos.stream().map(Recuerdo::id).collect(Collectors.toSet());
        Set<UUID> estaban = antes.stream().map(Recuerdo::id).collect(Collectors.toSet());
        recuerdos.deleteAllById(estaban.stream().filter(recuerdoId -> !quedan.contains(recuerdoId)).toList());
        recuerdos.saveAll(nuevos.stream()
                .filter(recuerdo -> !estaban.contains(recuerdo.id()))
                .map(recuerdo -> new RecuerdoRenasiaJpaEntity(recuerdo.id(), id, recuerdo.categoria().name(),
                        recuerdo.texto(), recuerdo.creadoEn()))
                .toList());
    }

    @Override
    @Transactional
    public boolean olvidarRecuerdo(UserId participanteId, UUID recuerdoId) {
        UUID id = participanteId.value();
        bloquear(id);
        if (recuerdos.borrarUno(id, recuerdoId) == 0) {
            return false;
        }
        memorias.findById(id).ifPresent(fila -> {
            fila.setResumen(null);
            fila.setActualizadoEn(clock.now());
        });
        return true;
    }

    @Override
    @Transactional
    public void olvidarTodo(UserId participanteId, Instant hasta) {
        UUID id = participanteId.value();
        bloquear(id);
        recuerdos.borrarDe(id);
        MemoriaRenasiaJpaEntity fila = memorias.findById(id)
                .orElseGet(() -> new MemoriaRenasiaJpaEntity(id, null, Instant.EPOCH, null));
        fila.setResumen(null);
        // Nunca hacia atras: si retrocediera, se releerian mensajes ya resumidos.
        if (hasta.isAfter(fila.getCompactadoHasta())) {
            fila.setCompactadoHasta(hasta);
        }
        fila.setActualizadoEn(clock.now());
        memorias.save(fila);
    }

    private MemoriaDeRenasia leer(UUID id) {
        List<Recuerdo> suyos = recuerdos.findByParticipanteIdOrderByCreadoEnAsc(id).stream()
                .map(fila -> new Recuerdo(fila.getId(), CategoriaDeRecuerdo.valueOf(fila.getCategoria()),
                        fila.getTexto(), fila.getCreadoEn()))
                .toList();
        Optional<MemoriaRenasiaJpaEntity> fila = memorias.findById(id);
        return new MemoriaDeRenasia(suyos, fila.map(MemoriaRenasiaJpaEntity::getResumen),
                fila.map(MemoriaRenasiaJpaEntity::getCompactadoHasta).orElse(Instant.EPOCH));
    }

    private void bloquear(UUID id) {
        jdbcClient.sql("SELECT pg_advisory_xact_lock(hashtextextended(:clave, 0))")
                .param("clave", "memoria_renasia:" + id)
                .query((fila, numero) -> numero)
                .list();
    }
}
