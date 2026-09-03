package com.renaser.os.calendar.domain.model.evento;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Puerto directo de las reglas de schema.ts (repo viejo): refineEventInput. */
class EventoTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    private static final Instant INICIA_EN = Instant.parse("2026-09-01T19:00:00Z");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final UserId CREADOR = UserId.of(UUID.randomUUID());
    /** El id ya no lo sortea crear(): entra por parametro, generado por el puerto IdGenerator. */
    private static final EventoId ID = EventoId.of(UUID.randomUUID());

    private static Evento eventoTodos() {
        return Evento.crear(ID, "Sesion", null, INICIA_EN, 60, LIMA, TipoUbicacion.MEET, "https://meet.google.com/abc",
                TipoAudiencia.TODOS, null, null, null, TipoEvento.ESPONTANEO, false, false, false, null, Set.of(),
                List.of(), CREADOR, CLOCK);
    }

    @Test
    void creaUnEventoValido() {
        Evento evento = eventoTodos();
        assertThat(evento.id()).isEqualTo(ID);
        assertThat(evento.titulo()).isEqualTo("Sesion");
        assertThat(evento.estado()).isEqualTo(EstadoEvento.PUBLICADO);
        assertThat(evento.creadoPor()).isEqualTo(CREADOR);
    }

    @Test
    void tituloMayorATreintaCaracteresRechazado() {
        assertThatThrownBy(() -> Evento.crear(ID, "x".repeat(31), null, INICIA_EN, 60, LIMA, TipoUbicacion.MEET,
                "https://meet.google.com/abc", TipoAudiencia.TODOS, null, null, null, TipoEvento.ESPONTANEO, false,
                false, false, null, Set.of(), List.of(), CREADOR, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tituloVacioRechazado() {
        assertThatThrownBy(() -> Evento.crear(ID, "  ", null, INICIA_EN, 60, LIMA, TipoUbicacion.MEET,
                "https://meet.google.com/abc", TipoAudiencia.TODOS, null, null, null, TipoEvento.ESPONTANEO, false,
                false, false, null, Set.of(), List.of(), CREADOR, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nivelMinimoSinAudienciaNivelMinimoRechazado() {
        assertThatThrownBy(() -> Evento.crear(ID, "Sesion", null, INICIA_EN, 60, LIMA, TipoUbicacion.MEET,
                "https://meet.google.com/abc", TipoAudiencia.TODOS, 1, null, null, TipoEvento.ESPONTANEO, false,
                false, false, null, Set.of(), List.of(), CREADOR, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audiencia_coherente");
    }

    @Test
    void audienciaNivelMinimoSinNivelIdRechazada() {
        assertThatThrownBy(() -> Evento.crear(ID, "Sesion", null, INICIA_EN, 60, LIMA, TipoUbicacion.MEET,
                "https://meet.google.com/abc", TipoAudiencia.NIVEL_MINIMO, null, null, null, TipoEvento.ESPONTANEO,
                false, false, false, null, Set.of(), List.of(), CREADOR, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void audienciaRolesSinRolesRechazada() {
        assertThatThrownBy(() -> Evento.crear(ID, "Sesion", null, INICIA_EN, 60, LIMA, TipoUbicacion.MEET,
                "https://meet.google.com/abc", TipoAudiencia.ROLES, null, null, null, TipoEvento.ESPONTANEO, false,
                false, false, null, Set.of(), List.of(), CREADOR, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void audienciaRolesConRolesValida() {
        Evento evento = Evento.crear(ID, "Sesion", null, INICIA_EN, 60, LIMA, TipoUbicacion.MEET,
                "https://meet.google.com/abc", TipoAudiencia.ROLES, null, null, null, TipoEvento.ESPONTANEO, false,
                false, false, null, Set.of(RolUsuario.MENTOR), List.of(), CREADOR, CLOCK);
        assertThat(evento.rolesDestino()).containsExactly(RolUsuario.MENTOR);
    }

    @Test
    void ubicacionZoomSinUrlRechazada() {
        assertThatThrownBy(() -> Evento.crear(ID, "Sesion", null, INICIA_EN, 60, LIMA, TipoUbicacion.ZOOM, null,
                TipoAudiencia.TODOS, null, null, null, TipoEvento.ESPONTANEO, false, false, false, null, Set.of(),
                List.of(), CREADOR, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ubicacionLlamadaInternaConValorRechazada() {
        assertThatThrownBy(() -> Evento.crear(ID, "Sesion", null, INICIA_EN, 60, LIMA, TipoUbicacion.LLAMADA_INTERNA,
                "algo", TipoAudiencia.TODOS, null, null, null, TipoEvento.ESPONTANEO, false, false, false, null,
                Set.of(), List.of(), CREADOR, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void masDeCincoRecordatoriosPersonalizadosRechazado() {
        List<ReglaRecordatorio> seis = List.of(
                ReglaRecordatorio.minutosAntes(1, 5), ReglaRecordatorio.minutosAntes(2, 10),
                ReglaRecordatorio.minutosAntes(3, 15), ReglaRecordatorio.minutosAntes(4, 20),
                ReglaRecordatorio.minutosAntes(5, 25), ReglaRecordatorio.minutosAntes(6, 30));

        assertThatThrownBy(() -> Evento.crear(ID, "Sesion", null, INICIA_EN, 60, LIMA, TipoUbicacion.MEET,
                "https://meet.google.com/abc", TipoAudiencia.TODOS, null, null, null, TipoEvento.ESPONTANEO, false,
                false, true, null, Set.of(), seis, CREADOR, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordatoriosDuplicadosRechazados() {
        List<ReglaRecordatorio> duplicadas = List.of(
                ReglaRecordatorio.minutosAntes(1, 10), ReglaRecordatorio.minutosAntes(2, 10));

        assertThatThrownBy(() -> Evento.crear(ID, "Sesion", null, INICIA_EN, 60, LIMA, TipoUbicacion.MEET,
                "https://meet.google.com/abc", TipoAudiencia.TODOS, null, null, null, TipoEvento.ESPONTANEO, false,
                false, true, null, Set.of(), duplicadas, CREADOR, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reglasEfectivasHeredanDelTipoCuandoNoPersonalizadas() {
        Evento evento = eventoTodos(); // ESPONTANEO, recordatoriosPersonalizados=false
        assertThat(evento.reglasRecordatorioEfectivas()).isEqualTo(ReglasPorTipoEvento.recordatoriosPorDefecto(TipoEvento.ESPONTANEO));
    }

    @Test
    void reglasEfectivasVaciasSignificaNoAvisaCuandoPersonalizadasSinFilas() {
        Evento evento = Evento.crear(ID, "Sesion", null, INICIA_EN, 60, LIMA, TipoUbicacion.MEET,
                "https://meet.google.com/abc", TipoAudiencia.TODOS, null, null, null, TipoEvento.ESPONTANEO, false,
                false, true, null, Set.of(), List.of(), CREADOR, CLOCK);
        assertThat(evento.reglasRecordatorioEfectivas()).isEmpty();
    }

    @Test
    void recurrenciaHastaAnteriorAIniciaEnRechazada() {
        Recurrencia recurrenciaInvalida = new Recurrencia(FrecuenciaRecurrencia.SEMANAL, 1,
                INICIA_EN.minusSeconds(3600), null, Set.of(java.time.DayOfWeek.MONDAY));

        assertThatThrownBy(() -> Evento.crear(ID, "Sesion", null, INICIA_EN, 60, LIMA, TipoUbicacion.MEET,
                "https://meet.google.com/abc", TipoAudiencia.TODOS, null, null, null, TipoEvento.ESPONTANEO, false,
                false, false, recurrenciaInvalida, Set.of(), List.of(), CREADOR, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancelarCambiaElEstado() {
        Evento evento = eventoTodos();
        evento.cancelar(CLOCK);
        assertThat(evento.estado()).isEqualTo(EstadoEvento.CANCELADO);
    }

    @Test
    void actualizarReemplazaLosCamposMutables() {
        Evento evento = eventoTodos();
        evento.actualizar("Nuevo titulo", "desc", INICIA_EN.plusSeconds(3600), 30, LIMA, TipoUbicacion.ENLACE,
                "https://enlace.example/x", TipoAudiencia.TODOS, null, null, null, true, true, false, null,
                Set.of(), List.of(), CLOCK);

        assertThat(evento.titulo()).isEqualTo("Nuevo titulo");
        assertThat(evento.duracionMinutos()).isEqualTo(30);
        assertThat(evento.notificarAlCrear()).isTrue();
        // id/tipoEvento/creadoPor/creadoEn son inmutables — no cambian con actualizar().
        assertThat(evento.tipoEvento()).isEqualTo(TipoEvento.ESPONTANEO);
        assertThat(evento.creadoPor()).isEqualTo(CREADOR);
    }

    /**
     * {@code rehydrate} es el camino del adaptador de persistencia (recarga desde la BD), a
     * diferencia de {@code crear}/{@code actualizar} no pasa por {@code aplicarCambios}.
     * {@code EnumSet.copyOf} lanza {@code IllegalArgumentException: Collection is empty}
     * con una coleccion vacia — {@code rehydrate} le faltaba la guarda que {@code crear}
     * y {@link Recurrencia} ya tenian, asi que CUALQUIER evento cargado sin filas en
     * `roles_destino_evento` (o sea casi todos: solo la audiencia ROLES llena esa tabla)
     * reventaba al leerlo de la base. Cubre esa reconstruccion explicitamente para que no
     * vuelva a faltar la guarda.
     */
    @Test
    void rehydrateConRolesDestinoVacioNoRevienta() {
        Evento evento = Evento.rehydrate(EventoId.of(UUID.randomUUID()), "Sesion", null, null, INICIA_EN, 60, LIMA,
                TipoUbicacion.MEET, "https://meet.google.com/abc", TipoAudiencia.TODOS, null, null, null,
                EstadoEvento.PUBLICADO, TipoEvento.ESPONTANEO, false, false, false, null, Set.of(), List.of(),
                CREADOR, CLOCK.now(), CLOCK.now());

        assertThat(evento.rolesDestino()).isEmpty();
    }

    @Test
    void rehydrateConRolesDestinoNuloNoRevienta() {
        Evento evento = Evento.rehydrate(EventoId.of(UUID.randomUUID()), "Sesion", null, null, INICIA_EN, 60, LIMA,
                TipoUbicacion.MEET, "https://meet.google.com/abc", TipoAudiencia.TODOS, null, null, null,
                EstadoEvento.PUBLICADO, TipoEvento.ESPONTANEO, false, false, false, null, null, null,
                CREADOR, CLOCK.now(), CLOCK.now());

        assertThat(evento.rolesDestino()).isEmpty();
        assertThat(evento.reglasRecordatorio()).isEmpty();
    }

    @Test
    void rehydrateConRolesDestinoNoVacioLosConserva() {
        Evento evento = Evento.rehydrate(EventoId.of(UUID.randomUUID()), "Sesion", null, null, INICIA_EN, 60, LIMA,
                TipoUbicacion.MEET, "https://meet.google.com/abc", TipoAudiencia.ROLES, null, null, null,
                EstadoEvento.PUBLICADO, TipoEvento.ESPONTANEO, false, false, false, null,
                Set.of(RolUsuario.MENTOR), List.of(), CREADOR, CLOCK.now(), CLOCK.now());

        assertThat(evento.rolesDestino()).containsExactly(RolUsuario.MENTOR);
    }
}
