package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase.CrearEventoCommand;
import com.renaser.os.calendar.application.ports.in.meet.EventoConMeetVista;
import com.renaser.os.calendar.application.ports.out.meet.GoogleNoRespondeException;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.EstadoDeCoanfitriones;
import com.renaser.os.calendar.domain.model.meet.EstadoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.SalaMeet;
import com.renaser.os.calendar.domain.model.meet.transcripcion.DiagnosticoDeReunion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.renaser.os.calendar.application.services.MundoDeMeet.ADMIN;
import static com.renaser.os.calendar.application.services.MundoDeMeet.LIMA;
import static com.renaser.os.calendar.application.services.MundoDeMeet.MENTOR;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisión del dueño (14/11/2026, spec 040 FR-038): quien dirige la sesión —quien creó el evento y el mentor del grupo— es coanfitrión de la sala de
 * Meet, porque Google solo graba y transcribe solo cuando entra el anfitrión o un coanfitrión. Con el Google simulado: sin red y sin credenciales.
 */
class CoanfitrionesDeLaSalaTest {

    private static final Instant HOY_19H_LIMA = Instant.parse("2026-11-06T00:00:00Z");

    private MundoDeMeet m;

    @BeforeEach
    void setUp() {
        m = new MundoDeMeet();
        m.conectarLaCuenta();
        m.coanfitriones = true;
        m.correosDeLogin.put(MENTOR, "mentor@gmail.com");
    }

    // spec 040 FR-038
    @Test
    @DisplayName("FR-038: al crear la sala, quien creó el evento queda como coanfitrión y la sala lo anota como OK")
    void quienCreaQuedaComoCoanfitrion() {
        EventoConMeetVista creado = m.servicio.crear(comando(ADMIN, null));

        assertThat(m.google.coanfitrionesPorEnlace.values()).singleElement().satisfies(c -> assertThat(c).containsExactly("admin@renaser.pe"));
        assertThat(m.salas.todas()).singleElement().satisfies(s -> {
            assertThat(s.coanfitriones()).isEqualTo(EstadoDeCoanfitriones.OK);
            assertThat(s.coanfitrionesMotivo()).isNull();
        });
        assertThat(creado.meet().coanfitriones()).isEqualTo("OK");
        assertThat(creado.meet().avisoDeCoanfitriones()).isNull();
    }

    // spec 040 FR-038
    @Test
    @DisplayName("FR-038: si el evento es de un grupo, su mentor también queda como coanfitrión")
    void elMentorDelGrupoTambien() {
        UUID grupo = UUID.randomUUID();
        m.mentorDeGrupo.put(grupo, MENTOR);

        m.servicio.crear(comando(ADMIN, grupo));

        assertThat(m.google.coanfitrionesPorEnlace.values()).singleElement()
                .satisfies(c -> assertThat(c).containsExactlyInAnyOrder("admin@renaser.pe", "mentor@gmail.com"));
    }

    // spec 040 FR-038
    @Test
    @DisplayName("FR-038: quien ya era coanfitrión no se vuelve a pedir ni se duplica, aunque la sala se vuelva a sincronizar")
    void yaEraMiembroNoSeDuplica() {
        EventoConMeetVista creado = m.servicio.crear(comando(ADMIN, null));
        assertThat(m.google.llamadasDe("alta-coanfitrion")).isEqualTo(1);

        // Una edición hace olvidar el estado (puede haber cambiado quién dirige) y la sala vuelve a pedir lo mismo.
        m.servicio.actualizar(new com.renaser.os.calendar.application.ports.in.evento.ActualizarEventoUseCase.ActualizarEventoCommand(
                ADMIN, creado.evento().evento().id(), "Otro título", null, HOY_19H_LIMA, 90, LIMA, TipoUbicacion.MEET,
                creado.evento().evento().valorUbicacion(), TipoAudiencia.TODOS, null, null, null, false, false, false, null, Set.of(),
                List.of(), AccesoSalaMeet.ABIERTA));

        assertThat(m.google.llamadasDe("coanfitriones:")).isEqualTo(2);
        assertThat(m.google.llamadasDe("alta-coanfitrion")).as("ninguna alta nueva: ya lo era").isEqualTo(1);
        assertThat(m.salas.todas()).singleElement().extracting(SalaMeet::coanfitriones).isEqualTo(EstadoDeCoanfitriones.OK);
    }

    // spec 040 FR-038
    @Test
    @DisplayName("FR-038: si Google rechaza (403), el evento y la sala se crean igual y el rechazo queda anotado sin correos")
    void googleRechazaNoRompeElEvento() {
        m.google.rechazaCoanfitrionesCon = 403;

        EventoConMeetVista creado = m.servicio.crear(comando(ADMIN, null));

        assertThat(creado.evento().evento().id()).isNotNull();
        assertThat(m.salas.todas()).singleElement().satisfies(s -> {
            assertThat(s.estado()).isEqualTo(EstadoSalaMeet.CREADO);
            assertThat(s.coanfitriones()).isEqualTo(EstadoDeCoanfitriones.FALLIDO);
            assertThat(s.coanfitrionesMotivo()).contains("403").doesNotContain("@");
        });
        assertThat(creado.meet().coanfitriones()).isEqualTo("FALLIDO");
        assertThat(creado.meet().avisoDeCoanfitriones()).isEqualTo(ResumenesDeMeet.COANFITRIONES_FALLIDO);
        // Un rechazo no se reintenta solo: ni siquiera con otra pasada del proceso de fondo.
        m.trabajo.mantenimiento(m.reloj.now());
        assertThat(m.google.llamadasDe("coanfitriones:")).isEqualTo(1);
    }

    // spec 040 FR-038
    @Test
    @DisplayName("FR-038: 404 o rechazo de un solo correo (de fuera de la organización): queda PARCIAL con los demás coanfitriones")
    void rechazoParcial() {
        UUID grupo = UUID.randomUUID();
        m.mentorDeGrupo.put(grupo, MENTOR);
        m.google.correosRechazados.add("mentor@gmail.com");

        m.servicio.crear(comando(ADMIN, grupo));

        assertThat(m.salas.todas()).singleElement().satisfies(s -> {
            assertThat(s.coanfitriones()).isEqualTo(EstadoDeCoanfitriones.PARCIAL);
            assertThat(s.coanfitrionesMotivo()).doesNotContain("@");
        });
        assertThat(m.google.coanfitrionesPorEnlace.values()).singleElement().satisfies(c -> assertThat(c).containsExactly("admin@renaser.pe"));
    }

    // spec 040 FR-038
    @Test
    @DisplayName("FR-038: un fallo pasajero de Google deja la sala sin pedir y el proceso de fondo lo vuelve a intentar")
    void falloPasajeroSeReintenta() {
        m.coanfitriones = false;
        m.servicio.crear(comando(ADMIN, null));
        m.coanfitriones = true;
        boolean[] yaFallo = {false};
        m.google.alLlamar = () -> {
            String ultima = m.google.llamadas.get(m.google.llamadas.size() - 1);
            if (ultima.startsWith("coanfitriones:") && !yaFallo[0]) {
                yaFallo[0] = true;
                m.google.fallaProxima = new GoogleNoRespondeException("5xx", 503);
            }
        };

        m.trabajo.mantenimiento(m.reloj.now());
        assertThat(m.salas.todas()).singleElement().extracting(SalaMeet::coanfitriones).as("sin pedir: se reintenta").isNull();

        m.trabajo.mantenimiento(m.reloj.now());
        assertThat(m.salas.todas()).singleElement().extracting(SalaMeet::coanfitriones).isEqualTo(EstadoDeCoanfitriones.OK);
    }

    // spec 040 FR-038
    @Test
    @DisplayName("FR-038: sin correo de quien dirige no hay a quién pedirle: queda SIN_CORREO y no se llama a Google")
    void sinCorreo() {
        m.correosDeLogin.remove(ADMIN);

        m.servicio.crear(comando(ADMIN, null));

        assertThat(m.google.llamadasDe("coanfitriones:")).isZero();
        assertThat(m.salas.todas()).singleElement().extracting(SalaMeet::coanfitriones).isEqualTo(EstadoDeCoanfitriones.SIN_CORREO);
    }

    // spec 040 FR-038
    @Test
    @DisplayName("FR-038: con la opción apagada (por defecto) no se llama a Google ni se anota nada")
    void conLaOpcionApagadaNoSeLlama() {
        m.coanfitriones = false;

        EventoConMeetVista creado = m.servicio.crear(comando(ADMIN, null));

        assertThat(m.google.llamadasDe("coanfitriones:")).isZero();
        assertThat(m.salas.todas()).singleElement().extracting(SalaMeet::coanfitriones).isNull();
        assertThat(creado.meet().coanfitriones()).isNull();
    }

    // spec 040 FR-038
    @Test
    @DisplayName("FR-038: las salas que ya existían se rellenan en la siguiente sincronización, solo las de hoy en adelante")
    void relleno() {
        m.coanfitriones = false;
        m.servicio.crear(comando(ADMIN, null));
        assertThat(m.google.llamadasDe("coanfitriones:")).isZero();

        m.coanfitriones = true;
        m.trabajo.mantenimiento(m.reloj.now());

        assertThat(m.salas.todas()).singleElement().extracting(SalaMeet::coanfitriones).isEqualTo(EstadoDeCoanfitriones.OK);
    }

    // spec 047 FR-022
    @Test
    @DisplayName("FR-022: con cuentas en la sala, sin archivos y quien dirige sin ser coanfitrión, el motivo probable lo dice")
    void diagnosticoDistingueQuienDirigeSinPermiso() {
        var conferencia = new DiagnosticoDeReunion.Conferencia(Instant.parse("2026-11-13T00:00:00Z"), Instant.parse("2026-11-13T01:00:00Z"), 60, 2, 0,
                0, List.of(), List.of());
        DiagnosticoDeReunion base = DiagnosticoDeReunion.de(List.of(conferencia), "ON", "ON");
        assertThat(base.motivoProbable()).isEqualTo("CON_CUENTAS_Y_SIN_ARCHIVOS");

        assertThat(base.conCoanfitriones(EstadoDeCoanfitriones.FALLIDO).motivoProbable()).isEqualTo("QUIEN_DIRIGE_NO_ERA_COANFITRION");
        assertThat(base.conCoanfitriones(EstadoDeCoanfitriones.FALLIDO).explicacion()).contains("no quedó como coanfitrión");
        assertThat(base.conCoanfitriones(EstadoDeCoanfitriones.OK).motivoProbable()).isEqualTo("CON_CUENTAS_Y_SIN_ARCHIVOS");
        assertThat(base.conCoanfitriones(null).motivoProbable()).isEqualTo("CON_CUENTAS_Y_SIN_ARCHIVOS");
    }

    private static CrearEventoCommand comando(com.renaser.os.shared.domain.UserId actor, UUID grupo) {
        return new CrearEventoCommand(actor, "Clase en vivo", null, HOY_19H_LIMA, 90, LIMA, TipoUbicacion.MEET, null,
                grupo == null ? TipoAudiencia.TODOS : TipoAudiencia.CELULA, null, null, grupo, TipoEvento.SESION_ESPECIAL, false, false, false, null,
                Set.of(), List.of(), null, AccesoSalaMeet.ABIERTA);
    }
}
