package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase.CrearEventoCommand;
import com.renaser.os.calendar.application.ports.out.meet.GoogleNoRespondeException;
import com.renaser.os.calendar.application.ports.out.meet.GoogleRechazoException;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.EstadoDeCoanfitriones;
import com.renaser.os.calendar.domain.model.meet.EstadoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.SalaMeet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.renaser.os.calendar.application.services.MundoDeMeet.ADMIN;
import static com.renaser.os.calendar.application.services.MundoDeMeet.LIMA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisión del dueño (16/11/2026, spec 040 FR-040 y FR-041): las salas se crean con la Meet API ({@code spaces.create}) y las ya creadas de hoy en
 * adelante se reemplazan por una nueva; la vieja se cierra. Todo detrás de {@code EVENTOS_MEET_SALAS_CON_MEET_API}, apagado por defecto. Con el
 * Google simulado: sin red y sin credenciales.
 */
class SalasConMeetApiTest {

    private static final Instant HOY_19H_LIMA = Instant.parse("2026-11-06T00:00:00Z");

    private MundoDeMeet m;

    @BeforeEach
    void setUp() {
        m = new MundoDeMeet();
        m.conectarLaCuenta();
        m.correosDeLogin.put(ADMIN, "admin@renaser.pe");
    }

    // spec 040 FR-040
    @Test
    @DisplayName("FR-040: con el interruptor prendido la sala nace con la Meet API, se publica en Calendar y el evento de la app lleva su enlace")
    void salaNuevaConLaMeetApi() {
        m.salasConMeetApi = true;

        m.servicio.crear(comando());

        SalaMeet sala = unicaSala();
        assertThat(sala.conMeetApi()).isTrue();
        assertThat(sala.estado()).isEqualTo(EstadoSalaMeet.CREADO);
        assertThat(sala.enlace()).startsWith("https://meet.google.com/bbb-");
        assertThat(m.google.llamadasDe("crearEspacio:")).isEqualTo(1);
        assertThat(m.google.llamadasDe("crear:")).isZero(); // no hay conferencia de Calendar
        assertThat(m.google.ubicacionPorEvento).containsEntry(sala.idGoogle(), sala.enlace());
        assertThat(sala.accesoConfirmado()).isEqualTo(AccesoSalaMeet.ABIERTA);
        assertThat(sala.artefactos().grabacion()).isTrue();
        assertThat(sala.artefactos().transcripcion()).isTrue();
        assertThat(sala.artefactos().notas()).isTrue();
        assertThat(eventoDeLaApp().valorUbicacion()).isEqualTo(sala.enlace());
    }

    // spec 040 FR-040
    @Test
    @DisplayName("FR-040: la sala de la Meet API recibe a quien dirige como coanfitrión")
    void conCoanfitriones() {
        m.salasConMeetApi = true;
        m.coanfitriones = true;

        m.servicio.crear(comando());

        SalaMeet sala = unicaSala();
        assertThat(m.google.coanfitrionesPorEnlace.get(sala.enlace())).containsExactly("admin@renaser.pe");
        assertThat(sala.coanfitriones()).isEqualTo(EstadoDeCoanfitriones.OK);
    }

    // spec 040 FR-042
    @Test
    @DisplayName("FR-042: con el interruptor apagado (por defecto) todo sigue como hoy: conferencia de Calendar y ninguna llamada a la Meet API")
    void interruptorApagadoNadaCambia() {
        m.servicio.crear(comando());
        m.trabajo.mantenimiento(m.reloj.now());

        SalaMeet sala = unicaSala();
        assertThat(sala.conMeetApi()).isFalse();
        assertThat(m.google.llamadasDe("crear:")).isEqualTo(1);
        assertThat(m.google.llamadasDe("crearEspacio:")).isZero();
        assertThat(m.google.llamadasDe("publicar:")).isZero();
        assertThat(m.google.llamadasDe("cerrar:")).isZero();
    }

    // spec 040 FR-041
    @Test
    @DisplayName("FR-041: una sala de Calendar de hoy se migra: enlace nuevo en la app y en Calendar, la vieja se cierra, y repetirlo no hace nada")
    void migraUnaSalaExistente() {
        m.servicio.crear(comando());
        String viejo = unicaSala().enlace();
        m.salasConMeetApi = true;

        m.trabajo.mantenimiento(m.reloj.now());

        SalaMeet sala = unicaSala();
        assertThat(sala.conMeetApi()).isTrue();
        assertThat(sala.enlace()).isNotEqualTo(viejo).startsWith("https://meet.google.com/bbb-");
        assertThat(sala.enlaceAnterior()).isNull();
        assertThat(sala.estado()).isEqualTo(EstadoSalaMeet.CREADO);
        assertThat(eventoDeLaApp().valorUbicacion()).isEqualTo(sala.enlace());
        assertThat(m.google.ubicacionPorEvento).containsEntry(sala.idGoogle(), sala.enlace());
        assertThat(m.google.cerradas).containsExactly(viejo);
        assertThat(sala.artefactos().grabacion()).isTrue();

        long llamadas = m.google.llamadas.size();
        m.reloj.avanzar(Duration.ofHours(1));
        m.trabajo.mantenimiento(m.reloj.now());
        assertThat(m.google.llamadas).hasSize((int) llamadas + 0);
        assertThat(m.google.espaciosCreados).isEqualTo(1);
    }

    // spec 040 FR-041
    @Test
    @DisplayName("FR-041: las fechas pasadas no se tocan")
    void laPasadaNoSeToca() {
        m.servicio.crear(comando());
        m.salasConMeetApi = true;
        m.reloj.avanzar(Duration.ofDays(2));

        m.trabajo.mantenimiento(m.reloj.now());

        assertThat(m.google.llamadasDe("crearEspacio:")).isZero();
        assertThat(unicaSala().conMeetApi()).isFalse();
    }

    // spec 040 FR-041
    @Test
    @DisplayName("FR-041: una sesión que ya empezó no se migra: nadie pierde su sala en plena reunión")
    void laQueYaEmpezoNoSeToca() {
        m.servicio.crear(comando());
        m.salasConMeetApi = true;
        m.reloj.avanzar(Duration.ofHours(10)); // 01:00Z: la sesión empezó a las 00:00Z

        m.trabajo.mantenimiento(m.reloj.now());

        assertThat(m.google.llamadasDe("crearEspacio:")).isZero();
        assertThat(m.google.llamadasDe("cerrar:")).isZero();
    }

    // spec 040 FR-041
    @Test
    @DisplayName("FR-041: si Google falla al crear la sala nueva, el evento sigue con su enlace de siempre y se reintenta con espera")
    void falloAlCrearElEspacioDuranteLaMigracion() {
        m.servicio.crear(comando());
        String viejo = unicaSala().enlace();
        m.salasConMeetApi = true;
        m.google.fallaAlCrearEspacio = new GoogleNoRespondeException("5xx", 503);

        m.trabajo.mantenimiento(m.reloj.now());

        SalaMeet sala = unicaSala();
        assertThat(sala.enlace()).isEqualTo(viejo);
        assertThat(sala.estado()).isEqualTo(EstadoSalaMeet.CREADO);
        assertThat(sala.intentos()).isEqualTo(1);
        assertThat(eventoDeLaApp().valorUbicacion()).isEqualTo(viejo);
        assertThat(m.google.cerradas).isEmpty();

        m.google.fallaAlCrearEspacio = null;
        m.trabajo.mantenimiento(m.reloj.now()); // todavía no toca: hay espera
        assertThat(m.google.espaciosCreados).isZero();
        m.reloj.avanzar(Duration.ofMinutes(20));
        m.trabajo.mantenimiento(m.reloj.now());
        assertThat(unicaSala().conMeetApi()).isTrue();
        assertThat(unicaSala().enlace()).isNotEqualTo(viejo);
    }

    // spec 040 FR-041
    @Test
    @DisplayName("FR-041: si Calendar falla al publicar el enlace nuevo, la sala no se vuelve a crear: se reintenta solo la publicación y luego se cierra la vieja")
    void falloAlPublicarEnCalendario() {
        m.servicio.crear(comando());
        String viejo = unicaSala().enlace();
        m.salasConMeetApi = true;
        m.google.fallaAlPublicar = new GoogleNoRespondeException("5xx", 503);

        m.trabajo.mantenimiento(m.reloj.now());

        SalaMeet sala = unicaSala();
        assertThat(sala.estado()).isEqualTo(EstadoSalaMeet.PENDIENTE_DE_SINCRONIZAR);
        assertThat(sala.enlaceAnterior()).isEqualTo(viejo);
        assertThat(m.google.cerradas).isEmpty(); // la vieja sigue abierta hasta que el evento tenga el enlace nuevo

        m.reloj.avanzar(Duration.ofMinutes(20));
        m.trabajo.reintentarLoPendiente(m.reloj.now());

        sala = unicaSala();
        assertThat(sala.estado()).isEqualTo(EstadoSalaMeet.CREADO);
        assertThat(sala.enlaceAnterior()).isNull();
        assertThat(m.google.espaciosCreados).isEqualTo(1);
        assertThat(m.google.cerradas).containsExactly(viejo);
        assertThat(m.google.ubicacionPorEvento).containsEntry(sala.idGoogle(), sala.enlace());
    }

    // spec 040 FR-041
    @Test
    @DisplayName("FR-041: si no se puede cerrar la vieja por red, queda anotada y se cierra en el turno siguiente; si Google dice que no, no se insiste")
    void cierreDeLaVieja() {
        m.servicio.crear(comando());
        String viejo = unicaSala().enlace();
        m.salasConMeetApi = true;
        m.google.fallaAlCerrar = new GoogleNoRespondeException("5xx", 503);

        m.trabajo.mantenimiento(m.reloj.now());
        assertThat(unicaSala().enlaceAnterior()).isEqualTo(viejo);
        assertThat(eventoDeLaApp().valorUbicacion()).isEqualTo(unicaSala().enlace()); // el enlace nuevo ya es el del evento

        m.reloj.avanzar(Duration.ofMinutes(20));
        m.trabajo.mantenimiento(m.reloj.now());
        assertThat(unicaSala().enlaceAnterior()).isNull();
        assertThat(m.google.cerradas).containsExactly(viejo);
        assertThat(m.google.espaciosCreados).isEqualTo(1);
    }

    // spec 040 FR-041
    @Test
    @DisplayName("FR-041: si Google rechaza cerrar la vieja (403), se anota y no se insiste")
    void cierreRechazado() {
        m.servicio.crear(comando());
        m.salasConMeetApi = true;
        m.google.fallaAlCerrar = new GoogleRechazoException("no", 403);

        m.trabajo.mantenimiento(m.reloj.now());

        assertThat(unicaSala().enlaceAnterior()).isNull();
        assertThat(m.google.llamadasDe("cerrar:")).isEqualTo(1);
    }

    // spec 040 FR-040
    @Test
    @DisplayName("FR-040: si Google falla al crear la sala nueva, el evento se guarda igual, queda FALLIDO y el reintento la crea")
    void falloAlCrearUnaSalaNueva() {
        m.salasConMeetApi = true;
        m.google.fallaAlCrearEspacio = new GoogleNoRespondeException("5xx", 503);

        m.servicio.crear(comando());

        assertThat(m.eventos.porId.values()).hasSize(1);
        assertThat(unicaSala().estado()).isEqualTo(EstadoSalaMeet.FALLIDO);
        assertThat(eventoDeLaApp().valorUbicacion()).isNull();

        m.google.fallaAlCrearEspacio = null;
        m.reloj.avanzar(Duration.ofMinutes(20));
        m.trabajo.reintentarLoPendiente(m.reloj.now());

        assertThat(unicaSala().estado()).isEqualTo(EstadoSalaMeet.CREADO);
        assertThat(unicaSala().conMeetApi()).isTrue();
        assertThat(m.google.espaciosCreados).isEqualTo(1);
    }

    // spec 040 FR-040
    @Test
    @DisplayName("FR-040: editar el evento actualiza el evento de Calendar sin crear otra sala")
    void editarNoCreaOtraSala() {
        m.salasConMeetApi = true;
        m.servicio.crear(comando());
        String enlace = unicaSala().enlace();

        m.salas.marcarParaSincronizar(unicaSala().eventoId(), m.reloj.now());
        m.trabajo.reintentarLoPendiente(m.reloj.now());

        assertThat(unicaSala().enlace()).isEqualTo(enlace);
        assertThat(m.google.espaciosCreados).isEqualTo(1);
        assertThat(m.google.llamadasDe("publicar:")).isEqualTo(2);
    }

    private SalaMeet unicaSala() {
        assertThat(m.salas.todas()).hasSize(1);
        return m.salas.todas().get(0);
    }

    private Evento eventoDeLaApp() {
        return m.eventos.porId.values().iterator().next();
    }

    private static CrearEventoCommand comando() {
        return new CrearEventoCommand(ADMIN, "Clase en vivo", null, HOY_19H_LIMA, 90, LIMA, TipoUbicacion.MEET, null, TipoAudiencia.TODOS, null,
                null, null, TipoEvento.SESION_ESPECIAL, false, false, false, null, Set.of(), List.of(), null, AccesoSalaMeet.ABIERTA);
    }
}
