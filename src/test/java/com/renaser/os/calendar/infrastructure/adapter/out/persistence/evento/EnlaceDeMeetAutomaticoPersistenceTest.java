package com.renaser.os.calendar.infrastructure.adapter.out.persistence.evento;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que el evento guarda para el enlace de Meet automático (spec 040, V169): el acceso elegido va en el propio evento, y el enlace que cree
 * Google se escribe en {@code valor_ubicacion} SOLO si el evento pidió la sala automática (así un evento con el link pegado a mano nunca se pisa).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class EnlaceDeMeetAutomaticoPersistenceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-11-05T15:00:00Z"));
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final String ENLACE = "https://meet.google.com/abc-defg-hij";

    @Autowired
    private EventoPersistenceAdapter adapter;
    @Autowired
    private EntityManager entityManager;

    private UserId admin() {
        UserId id = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture Meet', CAST('ADMIN' AS renaser.rol_usuario), CAST('ACTIVO' AS renaser.estado_usuario))
                        """)
                .setParameter("id", id.value()).setParameter("email", id + "@renaser.test").executeUpdate();
        return id;
    }

    private Evento evento(UserId creador, String valorUbicacion, AccesoSalaMeet acceso) {
        return Evento.crear(EventoId.of(UUID.randomUUID()), "Clase en vivo", null, Instant.parse("2026-11-10T01:00:00Z"), 90, LIMA,
                TipoUbicacion.MEET, valorUbicacion, TipoAudiencia.ROLES, null, null, null, TipoEvento.ESPONTANEO, false, true, true, null,
                Set.of(RolUsuario.TRAINEE), List.of(), creador, CLOCK, Formacion.ESCUELA, acceso);
    }

    // spec 040 FR-001, FR-020
    @Test
    @DisplayName("FR-020: un evento de siempre se guarda sin acceso de sala (NULL) y vuelve igual: nada cambia para lo que ya existe")
    void unEventoDeSiempreNoTieneAcceso() {
        Evento viejo = evento(admin(), ENLACE, null);

        adapter.guardar(viejo);
        entityManager.clear();

        Evento recuperado = adapter.byId(viejo.id()).orElseThrow();
        assertThat(recuperado.accesoSalaMeet()).isNull();
        assertThat(recuperado.usaSalaDeMeetAutomatica()).isFalse();
        assertThat(recuperado.valorUbicacion()).isEqualTo(ENLACE);
    }

    // spec 040 FR-023
    @Test
    @DisplayName("FR-023: el acceso elegido (abierta o con permiso) se guarda en el evento y vuelve igual")
    void elAccesoSeGuardaEnElEvento() {
        UserId creador = admin();
        Evento abierto = evento(creador, null, AccesoSalaMeet.ABIERTA);
        Evento conPermiso = evento(creador, null, AccesoSalaMeet.CON_PERMISO);

        adapter.guardar(abierto);
        adapter.guardar(conPermiso);
        entityManager.clear();

        assertThat(adapter.byId(abierto.id()).orElseThrow().accesoSalaMeet()).isEqualTo(AccesoSalaMeet.ABIERTA);
        assertThat(adapter.byId(conPermiso.id()).orElseThrow().accesoSalaMeet()).isEqualTo(AccesoSalaMeet.CON_PERMISO);
    }

    // spec 040 FR-003
    @Test
    @DisplayName("FR-003: el enlace que crea Google se escribe en la ubicación del evento, y el resto del evento no cambia")
    void elEnlaceSePublicaEnElEvento() {
        Evento conSala = evento(admin(), null, AccesoSalaMeet.ABIERTA);
        adapter.guardar(conSala);
        entityManager.clear();

        adapter.fijarEnlaceDeMeet(conSala.id(), ENLACE);
        entityManager.clear();

        Evento recuperado = adapter.byId(conSala.id()).orElseThrow();
        assertThat(recuperado.valorUbicacion()).isEqualTo(ENLACE);
        assertThat(recuperado.titulo()).isEqualTo("Clase en vivo");
        assertThat(recuperado.accesoSalaMeet()).isEqualTo(AccesoSalaMeet.ABIERTA);
    }

    // spec 040 FR-020
    @Test
    @DisplayName("FR-020: un evento con el link pegado a mano (sin acceso de sala) nunca se pisa con un enlace automático")
    void unLinkPegadoNoSePisa() {
        Evento pegado = evento(admin(), ENLACE, null);
        adapter.guardar(pegado);
        entityManager.clear();

        adapter.fijarEnlaceDeMeet(pegado.id(), "https://meet.google.com/zzz-zzzz-zzz");
        entityManager.clear();

        assertThat(adapter.byId(pegado.id()).orElseThrow().valorUbicacion()).isEqualTo(ENLACE);
    }
}
