package com.renaser.os.habits.infrastructure.adapter.out.persistence.registro;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.api.EstadoObligacion;
import com.renaser.os.habits.api.ObligacionHabito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las obligaciones históricas contra Postgres real: lo que la ficha de seguimiento necesita saber de cada una. La
 * consulta proyecta columnas (no entidades) y se traduce a mano, así que compilar no prueba que cada columna caiga en
 * su campo (spec 005, E-628).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ObligacionesHistoricasPersistenceAdapterTest {

    private static final Instant AHORA = Instant.parse("2026-10-17T15:00:00Z");
    private static final LocalDate DIA = LocalDate.of(2026, 10, 17);

    @Autowired
    private ObligacionesHistoricasPersistenceAdapter adapter;
    @Autowired
    private RegistroHabitoPersistenceAdapter registroAdapter;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("trae lo que la persona escribio al cumplir y la clave de sistema del habito; sin respuesta, null")
    void traeLaRespuestaYLaClaveDeSistema() {
        UserId persona = nuevaPersona();
        HabitoId postDiario = habitoDeSistema("COMMUNITY_POST");
        HabitoId lectura = nuevoHabito("Lectura (respuesta)");
        guardar(persona, postDiario, "Publique mi avance", true);
        guardar(persona, lectura, null, false);

        List<ObligacionHabito> obligaciones = adapter.entre(List.of(persona), DIA, DIA);

        assertThat(obligaciones).hasSize(2);
        ObligacionHabito delPost = obligaciones.stream().filter(ObligacionHabito::seCumplePublicandoEnElMuro)
                .findFirst().orElseThrow();
        assertThat(delPost.respuestaTexto()).isEqualTo("Publique mi avance");
        assertThat(delPost.estado()).isEqualTo(EstadoObligacion.COMPLETADO);
        ObligacionHabito delaLectura = obligaciones.stream().filter(o -> !o.seCumplePublicandoEnElMuro())
                .findFirst().orElseThrow();
        assertThat(delaLectura.respuestaTexto()).isNull();
        assertThat(delaLectura.claveSistema()).isNull();
    }

    private void guardar(UserId persona, HabitoId habito, String respuesta, boolean completado) {
        RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), persona, habito, DIA,
                5, TipoDia.DISCIPLINA, false, AHORA);
        if (completado) {
            registro.completar(10, respuesta, null, null, AHORA);
        }
        registroAdapter.save(registro);
        entityManager.flush();
    }

    private HabitoId habitoDeSistema(String clave) {
        return HabitoId.of((UUID) entityManager
                .createNativeQuery("SELECT id FROM renaser.habitos WHERE clave_sistema = :clave")
                .setParameter("clave", clave).getSingleResult());
    }

    private HabitoId nuevoHabito(String titulo) {
        UUID id = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.habitos (id, ambito, titulo, tipo, categoria_clave)
                        VALUES (:id, 'SISTEMA', :titulo, 'CHECKBOX', 'MENTE')
                        """)
                .setParameter("id", id).setParameter("titulo", titulo).executeUpdate();
        return HabitoId.of(id);
    }

    private UserId nuevaPersona() {
        UserId id = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture', 'APRENDIZ', 'ACTIVO')
                        """)
                .setParameter("id", id.value()).setParameter("email", id + "@renaser.test").executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa)
                        VALUES (:id, 5)
                        """)
                .setParameter("id", id.value()).executeUpdate();
        return id;
    }
}
