package com.renaser.os.phasecontracts.infrastructure.adapter.out.persistence.contrato;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.phasecontracts.domain.model.contrato.ContratoFase;
import com.renaser.os.phasecontracts.domain.model.contrato.ContratoFaseId;
import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ContratoPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private ContratoPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private UserId participanteId;

    @BeforeEach
    void seedParticipante() {
        participanteId = UserId.of(UUID.randomUUID());
        insertarUsuario(participanteId, "APRENDIZ");
        insertarParticipante(participanteId, 30);
    }

    private void insertarUsuario(UserId id, String rolCrudo) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, :nombre, CAST(:rol AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .setParameter("id", id.value())
                .setParameter("email", id + "@renaser.test")
                .setParameter("nombre", "Fixture " + id)
                .setParameter("rol", rolCrudo)
                .executeUpdate();
    }

    private void insertarParticipante(UserId usuarioId, int diaPrograma) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa)
                        VALUES (:usuarioId, :dia)
                        """)
                .setParameter("usuarioId", usuarioId.value())
                .setParameter("dia", diaPrograma)
                .executeUpdate();
    }

    @Test
    void guardaYRecuperaUnContratoPorParticipanteYFase() {
        ContratoFase contrato = ContratoFase.firmar(ContratoFaseId.of(UUID.randomUUID()), participanteId, 30, CLOCK);

        adapter.save(contrato);

        var recuperado = adapter.porParticipanteYFase(participanteId, FasePrograma.FASE_2_DESARROLLO);
        assertThat(recuperado).isPresent();
        assertThat(recuperado.get().bucket()).isEqualTo(ContratoFase.BUCKET_DEFAULT);
        assertThat(recuperado.get().rutaFirma()).isEqualTo(ContratoFase.rutaFirma(participanteId,
                FasePrograma.FASE_2_DESARROLLO));
    }

    @Test
    void noEncuentraUnaFaseQueNoFueFirmada() {
        var resultado = adapter.porParticipanteYFase(participanteId, FasePrograma.FASE_4_ASCENSION);

        assertThat(resultado).isEmpty();
    }

    @Test
    void listaTodosLosContratosDeUnParticipanteOrdenadosPorFirma() {
        // dos fixtures propias, dias distintos, misma persona, dos fases.
        adapter.save(ContratoFase.firmar(ContratoFaseId.of(UUID.randomUUID()), participanteId, 20, CLOCK));
        FixedClock clockMasTarde = FixedClock.at(CLOCK.now().plusSeconds(3600));
        adapter.save(ContratoFase.firmar(ContratoFaseId.of(UUID.randomUUID()), participanteId, 40, clockMasTarde));

        List<ContratoFase> contratos = adapter.todosDeParticipante(participanteId);

        assertThat(contratos).hasSize(2);
        assertThat(contratos.get(0).fase()).isEqualTo(FasePrograma.FASE_2_DESARROLLO);
        assertThat(contratos.get(1).fase()).isEqualTo(FasePrograma.FASE_3_GUERRERO_ALQUIMISTA);
    }

    @Test
    @DisplayName("UNIQUE (participante_id, fase) del baseline: dos contratos para el mismo participante y fase colisionan en Postgres")
    void dosContratosParaElMismoParticipanteYFaseColisionanEnLaBaseDeDatos() {
        adapter.save(ContratoFase.firmar(ContratoFaseId.of(UUID.randomUUID()), participanteId, 20, CLOCK));
        entityManager.flush();

        // segundo contrato para la MISMA fase, saltando la verificacion de idempotencia que
        // ContratoService.firmar() hace antes de guardar (aca se prueba la base de datos, no el servicio).
        // ContratoPersistenceAdapter.save() usa repository.save() sin flush -- la violacion solo
        // se ve al forzar el flush explicitamente.
        assertThatThrownBy(() -> {
            adapter.save(ContratoFase.firmar(ContratoFaseId.of(UUID.randomUUID()), participanteId, 25, CLOCK));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void traduceLasCuatroFasesEnAmbasDirecciones() {
        UserId otro = UserId.of(UUID.randomUUID());
        insertarUsuario(otro, "APRENDIZ");
        insertarParticipante(otro, 70);

        adapter.save(ContratoFase.firmar(ContratoFaseId.of(UUID.randomUUID()), otro, 70, CLOCK)); // Fase IV

        assertThat(adapter.porParticipanteYFase(otro, FasePrograma.FASE_4_ASCENSION)).isPresent();
        assertThat(adapter.porParticipanteYFase(otro, FasePrograma.FASE_2_DESARROLLO)).isEmpty();
    }
}
