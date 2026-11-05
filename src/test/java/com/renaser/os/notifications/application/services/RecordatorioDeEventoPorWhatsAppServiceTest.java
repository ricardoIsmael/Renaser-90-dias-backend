package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.evento.AvisoDeMensajeDelEvento;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.application.ports.out.preferencia.LoadPreferenciasPort;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.notifications.domain.model.whatsapp.CausaDeFalla;
import com.renaser.os.notifications.domain.model.whatsapp.EntregaDeWhatsApp;
import com.renaser.os.notifications.domain.model.whatsapp.EstadoDeEntrega;
import com.renaser.os.notifications.domain.model.whatsapp.PalabrasDeBaja;
import com.renaser.os.notifications.domain.model.whatsapp.ResultadoDelEnvio;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FormacionConfigurada;
import com.renaser.os.users.api.FormacionesFinder;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El WhatsApp de un evento (spec 041). Se prueba el servicio REAL con un proveedor falso que cuenta las llamadas y una tabla de entregas en memoria con la regla de
 * la base (clave unica): lo que se mide es CUANTAS veces se llama al proveedor, a quien y con que, no el cableado. Las pruebas nunca llaman a Meta ni a YCloud.
 *
 * <p>El reloj esta a las 23:50 UTC (18:50 en Lima) y el evento empieza a las 00:00 UTC (19:00 en Lima): la fecha UTC del aviso y la del evento son distintas.
 */
class RecordatorioDeEventoPorWhatsAppServiceTest {

    private static final Instant INICIO = Instant.parse("2026-11-07T00:00:00Z");
    private static final Instant A_LOS_DIEZ = INICIO.minus(Duration.ofMinutes(10));
    private static final UUID EVENTO = UUID.randomUUID();

    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final LoadPreferenciasPort preferencias = mock(LoadPreferenciasPort.class);
    private final FormacionesFinder formaciones = mock(FormacionesFinder.class);
    private final EmitirNotificacionUseCase emitir = mock(EmitirNotificacionUseCase.class);
    private final ParticipacionProgramaFinder participaciones = mock(ParticipacionProgramaFinder.class);
    private final EntregasDeWhatsAppEnMemoria entregas = new EntregasDeWhatsAppEnMemoria();
    private final EnvioDeWhatsAppFalso envio = new EnvioDeWhatsAppFalso();
    private final InterruptoresDePrueba interruptores = InterruptoresDePrueba.todoPrendido();
    private final MockEnvironment entorno = new MockEnvironment();
    private final Map<UserId, String> fichas = new java.util.HashMap<>();
    private final AtomicInteger secuencia = new AtomicInteger();

    private final UserId aprendiz = UserId.of(UUID.randomUUID());
    private FixedClock reloj;
    private RecordatorioDeEventoPorWhatsAppService servicio;

    @BeforeEach
    void armar() {
        reloj = FixedClock.at(A_LOS_DIEZ);
        persona(aprendiz, UserRole.TRAINEE, UserStatus.ACTIVE, false);
        fichas.put(aprendiz, "+51 987654321");
        when(preferencias.habilitadaPara(any(), any())).thenReturn(Optional.empty());
        for (Formacion f : Formacion.values()) {
            when(formaciones.de(f)).thenReturn(new FormacionConfigurada(f, nombreDe(f), "", true, List.of(90), 1));
        }
        when(participaciones.usuariosActivosConRol(any())).thenReturn(List.of(UserId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID())));
        servicio = new RecordatorioDeEventoPorWhatsAppService(new DestinatarioDelMensajeDeEvento(usuarios, new ReglasDelMensajeDeEvento(entorno)),
                new ReglasDelMensajeDeEvento(entorno), preferencias, entregas, envio, personas -> fichas, formaciones,
                new AvisoDeFallaDeWhatsApp(emitir, participaciones, new IgualAlReloj()), interruptores,
                () -> new UUID(0, secuencia.incrementAndGet()), new IgualAlReloj());
    }

    /** El reloj se lee a traves de una referencia para poder moverlo en una prueba. */
    private final class IgualAlReloj implements com.renaser.os.shared.domain.Clock {
        @Override
        public Instant now() {
            return reloj.now();
        }

        @Override
        public java.time.LocalDate today() {
            return reloj.today();
        }
    }

    private static String nombreDe(Formacion f) {
        return switch (f) {
            case ESCUELA -> "Escuela Renaser";
            case DIPLOMADO -> "Diplomado Renaser";
            case CERTIFICACION -> "Certificación Renaser";
        };
    }

    private void persona(UserId id, UserRole rol, UserStatus estado, boolean deLaPrueba) {
        when(usuarios.findById(id)).thenReturn(Optional.of(new UserSummary(id, "Rosa Quispe", null, rol, estado, null, deLaPrueba)));
    }

    private AvisoDeMensajeDelEvento aviso(long recordatorio, int avisoMin, boolean dijoVoy) {
        return aviso(recordatorio, aprendiz, avisoMin, dijoVoy, "ESCUELA");
    }

    private AvisoDeMensajeDelEvento aviso(long recordatorio, UserId quien, int avisoMin, boolean dijoVoy, String formacion) {
        return new AvisoDeMensajeDelEvento(recordatorio, EVENTO, quien, INICIO, "America/Lima", "Clase de manifestación", avisoMin, dijoVoy,
                formacion, "https://meet.google.com/abc-defg-hij", null);
    }

    // ── Sin activacion previa, con baja ─────────────────────────────────────────

    // spec 041 FR-012, FR-017, SC-003
    @Test
    @DisplayName("quien dijo «Voy» y tiene WhatsApp en la ficha lo recibe SIN haber activado nada, con la plantilla y sus cinco huecos")
    void llegaSinActivarNada() {
        servicio.recordar(aviso(1, 10, true));

        assertThat(envio.llamadas).hasSize(1);
        var llamada = envio.llamadas.getFirst();
        assertThat(llamada.destino()).isEqualTo("+51987654321");
        assertThat(llamada.huecos()).containsExactly("Rosa", "Escuela Renaser", "Clase de manifestación", "empieza en 10 min, a las 19:00",
                "Entra aquí: https://meet.google.com/abc-defg-hij");
        assertThat(entregas.todas()).singleElement().satisfies(e -> {
            assertThat(e.estado()).isEqualTo(EstadoDeEntrega.ACEPTADO);
            assertThat(e.usuarioId()).isEqualTo(aprendiz);
            assertThat(e.avisoMin()).isEqualTo(10);
        });
    }

    // spec 041 FR-017
    @Test
    @DisplayName("el boton «No recibir más» lleva la carga BAJA:<id de la entrega>, y ese id es el externalId que ve el proveedor")
    void elBotonDeBaja() {
        servicio.recordar(aviso(1, 10, true));

        var llamada = envio.llamadas.getFirst();
        assertThat(llamada.cargaDelBoton()).isEqualTo(PalabrasDeBaja.cargaDelBoton(llamada.externalId()));
        assertThat(entregas.porId(llamada.externalId())).isPresent();
    }

    // spec 041 FR-012, SC-003
    @Test
    @DisplayName("quien dio de baja el WhatsApp nunca lo recibe, aunque tenga numero, diga «Voy» y la audiencia sea TODOS")
    void laBajaSeRespeta() {
        entorno.setProperty(ReglasDelMensajeDeEvento.PROPIEDAD_AUDIENCIA, "TODOS");
        when(preferencias.habilitadaPara(aprendiz, TipoNotificacion.RECORDATORIO_EVENTO_WHATSAPP)).thenReturn(Optional.of(false));

        servicio.recordar(aviso(1, 10, true));

        assertThat(envio.llamadas).isEmpty();
        assertThat(entregas.todas()).as("sin baja no hay ni fila: no se le escribe").isEmpty();
    }

    // spec 041 FR-012
    @Test
    @DisplayName("apagar el aviso de la app (RECORDATORIO_EVENTO) no es darse de baja del WhatsApp: son dos preferencias")
    void sonDosPreferencias() {
        when(preferencias.habilitadaPara(aprendiz, TipoNotificacion.RECORDATORIO_EVENTO)).thenReturn(Optional.of(false));

        servicio.recordar(aviso(1, 10, true));

        assertThat(envio.llamadas).hasSize(1);
    }

    // spec 041 FR-013, SC-004
    @Test
    @DisplayName("sin numero en la ficha no sale nada: la entrega queda OMITIDO con el motivo «Sin WhatsApp»")
    void sinNumero() {
        fichas.remove(aprendiz);

        servicio.recordar(aviso(1, 10, true));

        assertThat(envio.llamadas).isEmpty();
        assertThat(entregas.todas()).singleElement().satisfies(e -> {
            assertThat(e.estado()).isEqualTo(EstadoDeEntrega.OMITIDO);
            assertThat(e.motivo()).isEqualTo("Sin WhatsApp");
        });
    }

    // spec 041 FR-013
    @Test
    @DisplayName("un valor de la ficha que no tiene forma de numero tampoco sale")
    void numeroInvalido() {
        fichas.put(aprendiz, "pendiente");

        servicio.recordar(aviso(1, 10, true));

        assertThat(envio.llamadas).isEmpty();
        assertThat(entregas.todas()).singleElement().extracting(EntregaDeWhatsApp::motivo).isEqualTo("Sin WhatsApp");
    }

    // ── Quien recibe: audiencia y roles ────────────────────────────────────────

    // spec 041 FR-011, FR-015, SC-016
    @Test
    @DisplayName("con VOY (lo normal) quien no dijo «Voy» no recibe; al cambiar el valor a TODOS, recibe")
    void audienciaVoyYTodos() {
        servicio.recordar(aviso(1, 10, false));
        assertThat(envio.llamadas).isEmpty();

        entorno.setProperty(ReglasDelMensajeDeEvento.PROPIEDAD_AUDIENCIA, "TODOS");
        servicio.recordar(aviso(2, 10, false));
        assertThat(envio.llamadas).hasSize(1);
    }

    // spec 041 FR-015, SC-016
    @Test
    @DisplayName("por defecto solo el aprendiz: el mentor, el lider y Administracion no reciben; al sumar el rol por configuracion, el mentor recibe")
    void soloElAprendizPorDefecto() {
        for (UserRole rol : new UserRole[] {UserRole.MENTOR, UserRole.TRAINEE_GUIDE, UserRole.MENTOR_LEAD, UserRole.ADMIN, UserRole.ALCHEMIST, UserRole.IT_SUPPORT}) {
            UserId personal = UserId.of(UUID.randomUUID());
            persona(personal, rol, UserStatus.ACTIVE, false);
            fichas.put(personal, "+51 911111111");
            servicio.recordar(aviso(10, personal, 10, true, "ESCUELA"));
        }
        assertThat(envio.llamadas).as("ningun rol de personal recibe por defecto").isEmpty();

        entorno.setProperty(ReglasDelMensajeDeEvento.PROPIEDAD_ROLES, "TRAINEE, MENTOR");
        UserId mentor = UserId.of(UUID.randomUUID());
        persona(mentor, UserRole.MENTOR, UserStatus.ACTIVE, false);
        fichas.put(mentor, "+51 922222222");
        servicio.recordar(aviso(11, mentor, 10, true, "ESCUELA"));

        assertThat(envio.llamadas).singleElement().extracting(EnvioDeWhatsAppFalso.Llamada::destino).isEqualTo("+51922222222");
    }

    // spec 041 FR-015
    @Test
    @DisplayName("una cuenta suspendida o de prueba, nunca")
    void suspendidasYDePrueba() {
        UserId suspendida = UserId.of(UUID.randomUUID());
        UserId dePrueba = UserId.of(UUID.randomUUID());
        persona(suspendida, UserRole.TRAINEE, UserStatus.SUSPENDED, false);
        persona(dePrueba, UserRole.TRAINEE, UserStatus.ACTIVE, true);
        fichas.put(suspendida, "+51 933333333");
        fichas.put(dePrueba, "+51 944444444");

        servicio.recordar(aviso(1, suspendida, 10, true, "ESCUELA"));
        servicio.recordar(aviso(2, dePrueba, 10, true, "ESCUELA"));

        assertThat(envio.llamadas).isEmpty();
    }

    // spec 041 FR-015, SC-017
    @Test
    @DisplayName("«Voy» se lee al despachar: quien lo dice despues del aviso de 1 dia recibe los otros tres; quien pasa a «No voy» antes del de 30 min, no")
    void voyEsAlDespachar() {
        servicio.recordar(aviso(1, 1440, false));
        reloj = FixedClock.at(INICIO.minus(Duration.ofHours(1)));
        servicio.recordar(aviso(2, 60, true));
        reloj = FixedClock.at(INICIO.minus(Duration.ofMinutes(30)));
        servicio.recordar(aviso(3, 30, false));
        reloj = FixedClock.at(A_LOS_DIEZ);
        servicio.recordar(aviso(4, 10, true));

        assertThat(envio.llamadas).extracting(l -> l.huecos().get(3)).containsExactly("empieza en 1 hora, a las 19:00", "empieza en 10 min, a las 19:00");
    }

    // ── Sin duplicados ni pérdidas ─────────────────────────────────────────────

    // spec 041 FR-020, SC-005
    @Test
    @DisplayName("reentregar el mismo recordatorio diez veces llama al proveedor UNA sola vez: la entrega se aparta antes")
    void unaSolaLlamada() {
        for (int i = 0; i < 10; i++) {
            servicio.recordar(aviso(1, 10, true));
        }

        assertThat(envio.llamadas).hasSize(1);
        assertThat(entregas.todas()).hasSize(1);
        assertThat(entregas.llamadasAApartar).isEqualTo(10);
    }

    // spec 041 FR-020, SC-005
    @Test
    @DisplayName("la entrega esta apartada ANTES de llamar al proveedor (si el proveedor corta la luz, la fila ya existe)")
    void seApartaAntesDeLlamar() {
        envio.respuesta = () -> {
            assertThat(entregas.todas()).as("al momento de llamar ya existe, en ENVIANDO").singleElement()
                    .extracting(EntregaDeWhatsApp::estado).isEqualTo(EstadoDeEntrega.ENVIANDO);
            return ResultadoDelEnvio.aceptado("y-1", "wamid.1");
        };

        servicio.recordar(aviso(1, 10, true));

        assertThat(envio.llamadas).hasSize(1);
    }

    // spec 041 FR-020
    @Test
    @DisplayName("cada uno de los cuatro avisos y cada persona es otra entrega: no se confunden entre si")
    void cuatroAvisosCuatroEntregas() {
        UserId otra = UserId.of(UUID.randomUUID());
        persona(otra, UserRole.TRAINEE, UserStatus.ACTIVE, false);
        fichas.put(otra, "+51 955555555");
        servicio.recordar(aviso(1, 10, true));
        servicio.recordar(aviso(2, 10, true));
        servicio.recordar(aviso(3, otra, 10, true, "ESCUELA"));

        assertThat(envio.llamadas).hasSize(3);
    }

    // ── Si el proveedor falla ──────────────────────────────────────────────────

    // spec 041 FR-021, FR-023, SC-006
    @Test
    @DisplayName("un corte o un 5xx es «no se supo»: no lanza (el outbox no reintenta) y no se reenvia")
    void unCorteNoLanzaNiSeReintenta() {
        envio.respuesta = () -> ResultadoDelEnvio.noSeSupo("YCloud respondió 503");

        assertThatCode(() -> servicio.recordar(aviso(1, 10, true))).doesNotThrowAnyException();
        servicio.recordar(aviso(1, 10, true));

        assertThat(envio.llamadas).hasSize(1);
        assertThat(entregas.todas()).singleElement().extracting(EntregaDeWhatsApp::estado).isEqualTo(EstadoDeEntrega.NO_SE_SUPO);
    }

    // spec 041 FR-021, SC-006
    @Test
    @DisplayName("si el adaptador mismo lanza una excepcion inesperada, tampoco sube: queda «no se supo»")
    void unaExcepcionDelAdaptadorNoSube() {
        envio.respuesta = () -> {
            throw new IllegalStateException("se cayo la conexion");
        };

        assertThatCode(() -> servicio.recordar(aviso(1, 10, true))).doesNotThrowAnyException();

        assertThat(entregas.todas()).singleElement().extracting(EntregaDeWhatsApp::estado).isEqualTo(EstadoDeEntrega.NO_SE_SUPO);
    }

    // spec 041 FR-023
    @Test
    @DisplayName("ni un 429 se reintenta: queda fallido con el motivo y no hay una segunda llamada")
    void ni429SeReintenta() {
        envio.respuesta = () -> ResultadoDelEnvio.fallido(CausaDeFalla.LIMITE, "YCloud respondió 429");

        servicio.recordar(aviso(1, 10, true));
        servicio.recordar(aviso(1, 10, true));

        assertThat(envio.llamadas).hasSize(1);
        assertThat(entregas.todas()).singleElement().satisfies(e -> {
            assertThat(e.estado()).isEqualTo(EstadoDeEntrega.FALLIDO);
            assertThat(e.motivo()).isEqualTo("YCloud respondió 429");
        });
        verify(emitir, never()).emitir(any(EmitirNotificacionCommand.class));
    }

    // spec 041 FR-024, SC-012
    @Test
    @DisplayName("si la clave, la plantilla o el saldo fallan, Administracion y Alquimista reciben UN aviso por dia y por causa (el mismo origen, para que no se repita)")
    void laClaveLaPlantillaOElSaldo() {
        envio.respuesta = () -> ResultadoDelEnvio.fallido(CausaDeFalla.SALDO, "Insufficient balance");

        servicio.recordar(aviso(1, 10, true));
        servicio.recordar(aviso(2, 10, true));

        ArgumentCaptor<EmitirNotificacionCommand> avisos = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitir, atLeastOnce()).emitir(avisos.capture());
        assertThat(avisos.getAllValues()).hasSize(4).allSatisfy(c -> {
            assertThat(c.tipo()).isEqualTo(TipoNotificacion.OPERACION_PLATAFORMA);
            assertThat(c.cuerpo()).contains("sin saldo");
        });
        assertThat(avisos.getAllValues().stream().map(EmitirNotificacionCommand::origenEventoId).distinct().count())
                .as("el mismo dia y la misma causa: el mismo origen, y el indice unico de notificaciones lo deja en un aviso").isEqualTo(1);
        verify(participaciones, atLeastOnce()).usuariosActivosConRol(eq(Set.of(UserRole.ADMIN, UserRole.ALCHEMIST)));
    }

    // spec 041 FR-024
    @Test
    @DisplayName("otro dia el aviso tiene otro origen: vuelve a avisar si sigue sin arreglarse")
    void otroDiaOtroAviso() {
        envio.respuesta = () -> ResultadoDelEnvio.fallido(CausaDeFalla.CLAVE, "YCloud respondió 401");
        servicio.recordar(aviso(1, 10, true));
        reloj = FixedClock.at(A_LOS_DIEZ.plus(Duration.ofDays(1)));
        AvisoDeMensajeDelEvento delOtroDia = new AvisoDeMensajeDelEvento(2, EVENTO, aprendiz, INICIO.plus(Duration.ofDays(1)), "America/Lima",
                "Clase", 10, true, "ESCUELA", null, null);

        servicio.recordar(delOtroDia);

        ArgumentCaptor<EmitirNotificacionCommand> avisos = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitir, atLeastOnce()).emitir(avisos.capture());
        assertThat(avisos.getAllValues().stream().map(EmitirNotificacionCommand::origenEventoId).distinct().count()).isEqualTo(2);
    }

    // ── Atraso, evento empezado, sin horario ──────────────────────────────────

    // spec 041 FR-016, SC-007
    @Test
    @DisplayName("un aviso que llega con mas de 15 minutos de atraso no sale: queda OMITIDO «Llegó tarde»")
    void llegoTarde() {
        // El aviso de 30 minutos (su hora fue INICIO-30) llega 16 minutos tarde: INICIO-14.
        reloj = FixedClock.at(INICIO.minus(Duration.ofMinutes(14)));

        servicio.recordar(aviso(1, 30, true));

        assertThat(envio.llamadas).isEmpty();
        assertThat(entregas.todas()).singleElement().satisfies(e -> {
            assertThat(e.estado()).isEqualTo(EstadoDeEntrega.OMITIDO);
            assertThat(e.motivo()).isEqualTo("Llegó tarde");
        });
    }

    // spec 041 FR-016, SC-007
    @Test
    @DisplayName("una ocurrencia que ya empezo no recibe nada: queda OMITIDO «El evento ya empezó»")
    void elEventoYaEmpezo() {
        reloj = FixedClock.at(INICIO.plusSeconds(30));

        servicio.recordar(aviso(1, 10, true));

        assertThat(envio.llamadas).isEmpty();
        assertThat(entregas.todas()).singleElement().extracting(EntregaDeWhatsApp::motivo).isEqualTo("El evento ya empezó");
    }

    // spec 041 FR-016, SC-007
    @Test
    @DisplayName("un aviso a las 03:00 de la madrugada sale igual: no hay horario")
    void aLasTresDeLaMadrugada() {
        Instant inicio = Instant.parse("2026-11-07T08:30:00Z");   // 03:30 en Lima
        reloj = FixedClock.at(inicio.minus(Duration.ofMinutes(30)));   // 03:00 en Lima
        AvisoDeMensajeDelEvento deMadrugada = new AvisoDeMensajeDelEvento(1, EVENTO, aprendiz, inicio, "America/Lima", "Meditación", 30, true,
                "ESCUELA", null, null);

        servicio.recordar(deMadrugada);

        assertThat(envio.llamadas).hasSize(1);
        assertThat(envio.llamadas.getFirst().huecos().get(3)).isEqualTo("empieza en 30 min, a las 03:30");
    }

    // ── Interruptor, configuracion y tope ──────────────────────────────────────

    // spec 041 FR-010
    @Test
    @DisplayName("con el interruptor eventos.whatsapp apagado no se llama a nadie ni se deja una fila: lo que toca no se acumula")
    void interruptorApagado() {
        interruptores.apagar("eventos.whatsapp");

        servicio.recordar(aviso(1, 10, true));

        assertThat(envio.llamadas).isEmpty();
        assertThat(entregas.todas()).isEmpty();
    }

    // spec 041 FR-028
    @Test
    @DisplayName("sin la configuracion de YCloud el canal no hace nada (ni llama, ni deja filas) y no lanza")
    void sinConfiguracion() {
        envio.configurado = false;

        assertThatCode(() -> servicio.recordar(aviso(1, 10, true))).doesNotThrowAnyException();

        assertThat(envio.llamadas).isEmpty();
        assertThat(entregas.todas()).isEmpty();
    }

    // spec 041 FR-025, SC-012
    @Test
    @DisplayName("sin tope configurado (el valor por defecto) no hay limite")
    void sinTopeNoHayLimite() {
        for (int i = 1; i <= 25; i++) {
            UserId otra = UserId.of(UUID.randomUUID());
            persona(otra, UserRole.TRAINEE, UserStatus.ACTIVE, false);
            fichas.put(otra, "+51 9" + String.format("%08d", i));
            servicio.recordar(aviso(i, otra, 10, true, "ESCUELA"));
        }

        assertThat(envio.llamadas).hasSize(25);
    }

    // spec 041 FR-025, SC-012
    @Test
    @DisplayName("con un tope puesto, al alcanzarlo no sale ninguno mas ese mes («Tope mensual») y Administracion recibe el aviso")
    void conTope() {
        entorno.setProperty(ReglasDelMensajeDeEvento.PROPIEDAD_TOPE, "2");
        for (int i = 1; i <= 4; i++) {
            UserId otra = UserId.of(UUID.randomUUID());
            persona(otra, UserRole.TRAINEE, UserStatus.ACTIVE, false);
            fichas.put(otra, "+51 9" + String.format("%08d", i));
            servicio.recordar(aviso(i, otra, 10, true, "ESCUELA"));
        }

        assertThat(envio.llamadas).hasSize(2);
        assertThat(entregas.todas()).filteredOn(e -> "Tope mensual".equals(e.motivo())).hasSize(2)
                .allSatisfy(e -> assertThat(e.estado()).isEqualTo(EstadoDeEntrega.OMITIDO));
        verify(emitir, atLeastOnce()).emitir(any(EmitirNotificacionCommand.class));
    }

    // spec 041 FR-025
    @Test
    @DisplayName("un tope que no es un numero positivo se ignora: sin tope")
    void unTopeRoto() {
        entorno.setProperty(ReglasDelMensajeDeEvento.PROPIEDAD_TOPE, "mucho");
        assertThat(new ReglasDelMensajeDeEvento(entorno).topeMensual()).isEmpty();
        entorno.setProperty(ReglasDelMensajeDeEvento.PROPIEDAD_TOPE, "0");
        assertThat(new ReglasDelMensajeDeEvento(entorno).topeMensual()).isEmpty();
        entorno.setProperty(ReglasDelMensajeDeEvento.PROPIEDAD_TOPE, " 500 ");
        assertThat(new ReglasDelMensajeDeEvento(entorno).topeMensual()).hasValue(500);
    }

    // ── Formacion ──────────────────────────────────────────────────────────────

    // spec 041 FR-032, SC-009
    @ParameterizedTest
    @CsvSource({"ESCUELA,Escuela Renaser", "DIPLOMADO,Diplomado Renaser", "CERTIFICACION,Certificación Renaser"})
    @DisplayName("cada formacion dice su nombre en el segundo hueco, y el Diplomado y la Certificacion nunca llevan el texto de la Escuela")
    void cadaFormacionDiceSuNombre(String formacion, String nombre) {
        servicio.recordar(aviso(1, aprendiz, 10, true, formacion));

        String hueco = envio.llamadas.getFirst().huecos().get(1);
        assertThat(hueco).isEqualTo(nombre);
        if (!formacion.equals("ESCUELA")) {
            assertThat(String.join(" ", envio.llamadas.getFirst().huecos())).doesNotContain("Escuela");
        }
    }

    // spec 041 FR-032
    @Test
    @DisplayName("una publicacion vieja sin formacion se trata como de la Escuela")
    void sinFormacionEsLaEscuela() {
        servicio.recordar(aviso(1, aprendiz, 10, true, null));

        assertThat(envio.llamadas.getFirst().huecos().get(1)).isEqualTo("Escuela Renaser");
    }

    // ── Privacidad de lo guardado ──────────────────────────────────────────────

    // spec 041 FR-030, SC-014
    @Test
    @DisplayName("la entrega guarda solo lo minimo: ni el texto del mensaje ni el numero, ni siquiera como campo")
    void laEntregaNoGuardaElTextoNiElNumero() {
        servicio.recordar(aviso(1, 10, true));

        var componentes = java.util.Arrays.stream(EntregaDeWhatsApp.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList();
        assertThat(componentes).doesNotContain("texto", "mensaje", "cuerpo", "numero", "telefono", "whatsapp", "huecos");
        assertThat(entregas.todas().getFirst().toString()).doesNotContain("987654321").doesNotContain("Rosa").doesNotContain("manifestación");
    }
}
