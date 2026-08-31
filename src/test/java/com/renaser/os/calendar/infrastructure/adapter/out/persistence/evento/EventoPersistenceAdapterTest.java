package com.renaser.os.calendar.infrastructure.adapter.out.persistence.evento;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.calendar.domain.model.evento.EstadoEvento;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.FrecuenciaRecurrencia;
import com.renaser.os.calendar.domain.model.evento.Recurrencia;
import com.renaser.os.calendar.domain.model.evento.ReglaRecordatorio;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El adaptador que sostiene el modulo, y el que mas cosas resuelve fuera del alcance de un
 * mock: el ensamblado del agregado en lote (4 consultas hijas, no 4*N), el reemplazo de las
 * tablas hijas al re-guardar, la traduccion {@code rol_id} &lt;-&gt; {@link RolUsuario} contra
 * {@code renaser.roles} ({@code RolesCatalogoCache}), la convencion de dia de semana
 * (ISO 1..7 en el dominio, 0..6 en Postgres) y el CASCADE del baseline al eliminar.
 *
 * <p><b>La base NO esta vacia</b> (V4/V6/V9/V10 siembran catalogo de produccion): toda
 * asercion sobre listados es {@code contains}/{@code doesNotContain} por id, nunca por
 * indice ni {@code containsExactly}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class EventoPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    private static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final Instant INICIA_EN = Instant.parse("2026-09-05T19:00:00Z");

    @Autowired
    private EventoPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Test
    void guardarYRecuperarConservaElAgregadoCompleto() {
        UserId creador = crearUsuario();
        Recurrencia recurrencia = new Recurrencia(FrecuenciaRecurrencia.SEMANAL, 2,
                Instant.parse("2026-12-01T00:00:00Z"), null, Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
        List<ReglaRecordatorio> reglas = List.of(ReglaRecordatorio.minutosAntes(1, 10),
                ReglaRecordatorio.horaDelDia(2, LocalTime.of(6, 0)));
        Evento evento = eventoCompleto(creador, recurrencia, Set.of(RolUsuario.ADMIN, RolUsuario.TRAINEE), reglas);

        adapter.guardar(evento);
        entityManager.clear();

        Evento recuperado = adapter.byId(evento.id()).orElseThrow();
        assertThat(recuperado.titulo()).isEqualTo("Sesion");
        assertThat(recuperado.estado()).isEqualTo(EstadoEvento.PUBLICADO);
        assertThat(recuperado.timezone()).isEqualTo(ZONA);
        assertThat(recuperado.creadoPor()).isEqualTo(creador);
        assertThat(recuperado.rolesDestino()).containsExactlyInAnyOrder(RolUsuario.ADMIN, RolUsuario.TRAINEE);
        assertThat(recuperado.recurrencia().frecuencia()).isEqualTo(FrecuenciaRecurrencia.SEMANAL);
        assertThat(recuperado.recurrencia().intervalo()).isEqualTo(2);
        assertThat(recuperado.recurrencia().diasSemana())
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        assertThat(recuperado.reglasRecordatorio()).containsExactlyInAnyOrderElementsOf(reglas);
    }

    @Test
    void volverAGuardarReemplazaRecurrenciaRolesYReglasSinDuplicar() {
        UserId creador = crearUsuario();
        Evento evento = eventoCompleto(creador,
                new Recurrencia(FrecuenciaRecurrencia.SEMANAL, 1, null, null,
                        Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
                Set.of(RolUsuario.ADMIN, RolUsuario.TRAINEE),
                List.of(ReglaRecordatorio.minutosAntes(1, 10), ReglaRecordatorio.minutosAntes(2, 30)));
        adapter.guardar(evento);

        evento.actualizar("Sesion editada", null, INICIA_EN, 30, ZONA, TipoUbicacion.MEET,
                "https://meet.google.com/abc", TipoAudiencia.ROLES, null, null, null, false, false, true,
                new Recurrencia(FrecuenciaRecurrencia.DIARIA, 3, null, 5, Set.of()), Set.of(RolUsuario.MENTOR),
                List.of(ReglaRecordatorio.diasAntes(1, 2)), CLOCK);
        adapter.guardar(evento);
        entityManager.clear();

        Evento recuperado = adapter.byId(evento.id()).orElseThrow();
        assertThat(recuperado.titulo()).isEqualTo("Sesion editada");
        assertThat(recuperado.rolesDestino()).containsExactly(RolUsuario.MENTOR);
        assertThat(recuperado.recurrencia().frecuencia()).isEqualTo(FrecuenciaRecurrencia.DIARIA);
        assertThat(recuperado.recurrencia().repeticiones()).isEqualTo(5);
        // dias_semana_recurrencia CASCADEa desde recurrencias_evento: los dias viejos se van solos.
        assertThat(recuperado.recurrencia().diasSemana()).isEmpty();
        assertThat(recuperado.reglasRecordatorio()).containsExactly(ReglaRecordatorio.diasAntes(1, 2));
    }

    @Test
    void candidatosParaVisorTraeLosDelRangoYDejaAfueraLosPosteriores() {
        UserId creador = crearUsuario();
        Evento enRango = eventoSuelto(creador, INICIA_EN);
        Evento posterior = eventoSuelto(creador, Instant.parse("2026-11-05T19:00:00Z"));
        adapter.guardar(enRango);
        adapter.guardar(posterior);
        entityManager.clear();

        var candidatos = adapter.candidatosParaVisor(Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-30T00:00:00Z"));

        assertThat(candidatos).extracting(Evento::id).contains(enRango.id()).doesNotContain(posterior.id());
    }

    @Test
    void candidatosParaVisorIgnoraLoQueNoEstaPublicado() {
        UserId creador = crearUsuario();
        Evento borrador = eventoSuelto(creador, INICIA_EN);
        adapter.guardar(borrador);
        entityManager.createNativeQuery(
                        "UPDATE renaser.eventos SET estado = CAST('BORRADOR' AS renaser.estado_evento) WHERE id = :id")
                .setParameter("id", borrador.id().value())
                .executeUpdate();
        entityManager.clear();

        var candidatos = adapter.candidatosParaVisor(Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-30T00:00:00Z"));

        assertThat(candidatos).extracting(Evento::id).doesNotContain(borrador.id());
    }

    @Test
    void candidatosParaRecordatoriosTraeLoQueArrancaDentroDeLaVentana() {
        UserId creador = crearUsuario();
        Instant ahora = Instant.parse("2026-09-04T00:00:00Z");
        Evento dentro = eventoSuelto(creador, INICIA_EN);
        Evento lejano = eventoSuelto(creador, Instant.parse("2026-11-05T19:00:00Z"));
        adapter.guardar(dentro);
        adapter.guardar(lejano);
        entityManager.clear();

        var candidatos = adapter.candidatosParaRecordatorios(ahora, ahora.plus(3, ChronoUnit.DAYS),
                ahora.minus(1, ChronoUnit.DAYS));

        assertThat(candidatos).extracting(Evento::id).contains(dentro.id()).doesNotContain(lejano.id());
    }

    @Test
    void eliminarBorraElEventoYTodasSusFilasHijas() {
        UserId creador = crearUsuario();
        Evento evento = eventoCompleto(creador,
                new Recurrencia(FrecuenciaRecurrencia.SEMANAL, 1, null, null, Set.of(DayOfWeek.TUESDAY)),
                Set.of(RolUsuario.MENTOR), List.of(ReglaRecordatorio.minutosAntes(1, 10)));
        adapter.guardar(evento);

        adapter.eliminar(evento.id());
        entityManager.flush();
        entityManager.clear();

        assertThat(adapter.byId(evento.id())).isEmpty();
        assertThat(contarHijas("recurrencias_evento", evento.id())).isZero();
        assertThat(contarHijas("dias_semana_recurrencia", evento.id())).isZero();
        assertThat(contarHijas("roles_destino_evento", evento.id())).isZero();
        assertThat(contarHijas("reglas_recordatorio_evento", evento.id())).isZero();
    }

    @Test
    void byIdDevuelveVacioParaUnEventoQueNoExiste() {
        assertThat(adapter.byId(EventoId.newId())).isEmpty();
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────────

    private UserId crearUsuario() {
        UserId id = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, :nombre, CAST('ADMIN' AS renaser.rol_usuario),
                                CAST('ACTIVO' AS renaser.estado_usuario))
                        """)
                .setParameter("id", id.value())
                .setParameter("email", id + "@renaser.test")
                .setParameter("nombre", "Fixture " + id)
                .executeUpdate();
        return id;
    }

    private Evento eventoSuelto(UserId creador, Instant iniciaEn) {
        return Evento.crear("Sesion", null, iniciaEn, 60, ZONA, TipoUbicacion.MEET, "https://meet.google.com/abc",
                TipoAudiencia.TODOS, null, null, null, TipoEvento.ESPONTANEO, false, false, false, null, Set.of(),
                List.of(), creador, CLOCK);
    }

    private Evento eventoCompleto(UserId creador, Recurrencia recurrencia, Set<RolUsuario> roles,
                                   List<ReglaRecordatorio> reglas) {
        return Evento.crear("Sesion", "Descripcion", INICIA_EN, 60, ZONA, TipoUbicacion.MEET,
                "https://meet.google.com/abc", TipoAudiencia.ROLES, null, null, null, TipoEvento.ESPONTANEO, false,
                true, true, recurrencia, roles, reglas, creador, CLOCK);
    }

    private long contarHijas(String tabla, EventoId eventoId) {
        Object total = entityManager
                .createNativeQuery("SELECT count(*) FROM renaser." + tabla + " WHERE evento_id = :id")
                .setParameter("id", eventoId.value())
                .getSingleResult();
        return ((Number) total).longValue();
    }
}
