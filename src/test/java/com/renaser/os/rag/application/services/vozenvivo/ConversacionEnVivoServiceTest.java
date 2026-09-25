package com.renaser.os.rag.application.services.vozenvivo;

import com.renaser.os.rag.application.ports.in.memoria.CompactarMemoriaUseCase;
import com.renaser.os.rag.application.ports.in.memoria.ConsultarMemoriaUseCase;
import com.renaser.os.rag.domain.model.memoria.MemoriaDeRenasia;
import com.renaser.os.rag.application.ports.in.herramienta.EjecutarHerramientaAgenteUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ConsultarPropuestasDelTurnoUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase.PropuestaCreada;
import com.renaser.os.rag.application.ports.in.seguridad.RevisarPatronDeMalestarUseCase;
import com.renaser.os.rag.application.ports.in.voz.ConversarEnVivoUseCase.ConversacionEnVivo;
import com.renaser.os.rag.application.ports.in.voz.ConversarEnVivoUseCase.MotivoDeCierre;
import com.renaser.os.rag.application.ports.in.voz.ConversarEnVivoUseCase.SalidaDeVozEnVivo;
import com.renaser.os.rag.application.ports.out.conversacion.LoadConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.cuota.ControlCuotaVozEnVivoPort;
import com.renaser.os.rag.application.ports.out.ia.ConversacionEnVivoPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.rag.application.ports.out.tiempo.ProgramarTareaPeriodicaPort;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.CuotaDeVozEnVivo;
import com.renaser.os.rag.domain.model.conversacion.EventoDeVozEnVivo;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.RolMensaje;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La voz en vivo (D-162) sin red ni base: un proveedor falso, un temporizador que se dispara a mano
 * y un reloj que se mueve. El reloj arranca a las 03:00 UTC a proposito (regla 02): en Lima todavia
 * es el dia anterior, y un contador que usara la fecha del servidor lo pasaria por alto.
 */
class ConversacionEnVivoServiceTest {

    private static final Instant TRES_AM_UTC = Instant.parse("2026-09-24T03:00:00Z");
    private static final LocalDate DIA_EN_LIMA = LocalDate.parse("2026-09-23");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final UserId actor = UserId.of(UUID.randomUUID());
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final ProveedorFalso proveedor = new ProveedorFalso();
    private final CuotaEnMemoria cuotaPort = new CuotaEnMemoria();
    private final TemporizadorManual temporizador = new TemporizadorManual();
    private final RelojMovible reloj = new RelojMovible(TRES_AM_UTC);
    private final EjecutarHerramientaAgenteUseCase herramientas = mock(EjecutarHerramientaAgenteUseCase.class);
    private final ConsultarPropuestasDelTurnoUseCase propuestas = mock(ConsultarPropuestasDelTurnoUseCase.class);
    private final ConsultarSituacionDelAprendizPort situacion = mock(ConsultarSituacionDelAprendizPort.class);
    private final SaveMensajeRenasiaPort mensajes = mock(SaveMensajeRenasiaPort.class);
    private final LoadConversacionRenasiaPort loadConversacion = mock(LoadConversacionRenasiaPort.class);
    private final SaveConversacionRenasiaPort saveConversacion = mock(SaveConversacionRenasiaPort.class);
    private final RevisarPatronDeMalestarUseCase malestar = mock(RevisarPatronDeMalestarUseCase.class);
    private final ConsultarMemoriaUseCase memoria = mock(ConsultarMemoriaUseCase.class);
    /** Anota a quien se le pidio compactar, sin correr nada. */
    private final List<UserId> compactaciones = new ArrayList<>();
    private final CompactarMemoriaUseCase compactar = compactaciones::add;
    private final SalidaGrabada salida = new SalidaGrabada();

    private ConversacionEnVivoService service;

    @BeforeEach
    void armar() {
        conCuenta(UserRole.TRAINEE, UserStatus.ACTIVE);
        when(loadConversacion.porUsuarioId(actor)).thenReturn(Optional.empty());
        when(malestar.revisar(any(), any())).thenReturn(Optional.empty());
        when(situacion.de(actor)).thenReturn(Optional.of(new SituacionDelAprendiz(12, 1)));
        when(herramientas.disponibles(AgenteConversacional.COMPANION)).thenReturn(List.of(
                DefinicionHerramienta.sinParametros("consultar_habitos_del_dia", "Los habitos de hoy.")));
        when(propuestas.pendientesCreadasDesde(any(), any())).thenReturn(List.of());
        TiempoDeVozEnVivo tiempo = new TiempoDeVozEnVivo(cuotaPort, id -> LIMA,
                new CuotaDeVozEnVivo(Duration.ofMinutes(10), Duration.ofMinutes(15)), reloj);
        TurnosDeVozEnVivo turnos = new TurnosDeVozEnVivo(loadConversacion, saveConversacion, mensajes, malestar,
                compactar, reloj, UUID::randomUUID);
        service = new ConversacionEnVivoService(usuarios, proveedor, situacion, memoria, herramientas, propuestas,
                turnos, tiempo, temporizador, reloj);
    }

    private void conCuenta(UserRole rol, UserStatus estado) {
        when(usuarios.findById(actor)).thenReturn(Optional.of(new UserSummary(actor, "Ana", null, rol, estado)));
    }

    @Test
    @DisplayName("al abrir manda listo con los segundos que quedan hoy, y el prompt lleva la situacion y las herramientas")
    void abreYAvisaListo() {
        cuotaPort.usado.put(DIA_EN_LIMA, Duration.ofMinutes(4));

        service.iniciar(actor, salida);

        assertThat(salida.eventos).containsExactly(new EventoDeVozEnVivo.Listo(360));
        assertThat(proveedor.apertura.situacion()).isEqualTo(new SituacionDelAprendiz(12, 1));
        assertThat(proveedor.apertura.herramientas()).extracting(DefinicionHerramienta::nombre)
                .containsExactly("consultar_habitos_del_dia");
        verify(saveConversacion).save(any());
    }

    @Test
    @DisplayName("D-167: la memoria entra en la apertura; apagada, la apertura va sin ella")
    void memoriaEnLaApertura() {
        var recuerdos = new MemoriaDeRenasia(List.of(), Optional.of("Armaron su rutina."), Instant.EPOCH);
        when(memoria.paraConversar(actor)).thenReturn(Optional.of(recuerdos));

        service.iniciar(actor, salida);
        assertThat(proveedor.apertura.memoria()).isEqualTo(recuerdos);

        when(memoria.paraConversar(actor)).thenReturn(Optional.empty());
        service.iniciar(actor, new SalidaGrabada());
        assertThat(proveedor.apertura.memoria()).isNull();
    }

    @Test
    @DisplayName("D-167: despues de guardar la respuesta hablada se pide compactar, igual que en el chat")
    void pideCompactarDespuesDelTurno() {
        service.iniciar(actor, salida);

        proveedor.oyente.oido("Hola");
        proveedor.oyente.turnoCompleto();
        assertThat(compactaciones).as("sin respuesta no hay turno completo").isEmpty();

        proveedor.oyente.oido("Como voy?");
        proveedor.oyente.dicho("Vas bien.");
        proveedor.oyente.turnoCompleto();
        assertThat(compactaciones).containsExactly(actor);
    }

    @Test
    @DisplayName("a las 03:00 UTC la cuota que cuenta es la del dia anterior en Lima, no la de la fecha UTC")
    void diaDeLimaALasTresUtc() {
        cuotaPort.usado.put(DIA_EN_LIMA, Duration.ofMinutes(10));
        cuotaPort.usado.put(LocalDate.parse("2026-09-24"), Duration.ZERO);

        service.iniciar(actor, salida);

        assertThat(salida.eventos).containsExactly(new EventoDeVozEnVivo.CuotaAgotada());
        assertThat(salida.motivo).isEqualTo(MotivoDeCierre.CUOTA_AGOTADA);
        assertThat(proveedor.apertura).isNull();
    }

    @Test
    @DisplayName("lo hablado se cobra en el dia local de la persona")
    void cobraEnElDiaLocal() {
        ConversacionEnVivo conversacion = service.iniciar(actor, salida);
        reloj.avanzar(Duration.ofSeconds(7));
        temporizador.disparar();
        reloj.avanzar(Duration.ofMillis(1_500));
        conversacion.terminar();

        // 7 s en el cobro periodico + 1,5 s al cerrar, que se redondea para arriba.
        assertThat(cuotaPort.usado).containsExactly(Map.entry(DIA_EN_LIMA, Duration.ofSeconds(9)));
        assertThat(temporizador.cancelada).isTrue();
        assertThat(proveedor.sesion.cerrada).isTrue();
        assertThat(salida.motivo).isEqualTo(MotivoDeCierre.NORMAL);
    }

    @Test
    @DisplayName("los segundos que tarda Gemini en aceptar la sesion no se le cobran a la persona")
    void noCobraElSetup() {
        proveedor.alAbrir = () -> reloj.avanzar(Duration.ofSeconds(4));
        ConversacionEnVivo conversacion = service.iniciar(actor, salida);

        reloj.avanzar(Duration.ofSeconds(3));
        conversacion.terminar();

        assertThat(cuotaPort.usado).containsExactly(Map.entry(DIA_EN_LIMA, Duration.ofSeconds(3)));
    }

    @Test
    @DisplayName("si la cuota se acaba a mitad de la conversacion, llega cuotaAgotada y se cierra")
    void cuotaSeAcabaEnLaConversacion() {
        cuotaPort.usado.put(DIA_EN_LIMA, Duration.ofMinutes(9).plusSeconds(55));
        service.iniciar(actor, salida);

        reloj.avanzar(Duration.ofSeconds(5));
        temporizador.disparar();

        assertThat(salida.eventos).containsExactly(new EventoDeVozEnVivo.Listo(5), new EventoDeVozEnVivo.CuotaAgotada());
        assertThat(salida.motivo).isEqualTo(MotivoDeCierre.CUOTA_AGOTADA);
        assertThat(proveedor.sesion.cerrada).isTrue();
    }

    @Test
    @DisplayName("la herramienta se ejecuta con el actor de la sesion y su resultado vuelve al modelo")
    void herramientaConElActor() {
        InvocacionHerramienta pedido = InvocacionHerramienta.sinArgumentos("consultar_habitos_del_dia");
        when(herramientas.ejecutar(actor, pedido)).thenReturn(ResultadoHerramienta.exito("Meditar, pendiente"));
        service.iniciar(actor, salida);

        proveedor.oyente.pedidoDeHerramienta("llamada-1", pedido);

        verify(herramientas).ejecutar(actor, pedido);
        assertThat(proveedor.sesion.respuestas).containsExactly(
                "llamada-1|consultar_habitos_del_dia|" + ResultadoHerramienta.exito("Meditar, pendiente"));
    }

    @Test
    @DisplayName("una escritura deja una propuesta: llega el evento propuesta y el resumen queda en el mensaje guardado")
    void propuestaComoEnElChat() {
        UUID idPropuesta = UUID.randomUUID();
        Instant vence = TRES_AM_UTC.plus(Duration.ofMinutes(10));
        InvocacionHerramienta pedido = new InvocacionHerramienta("marcar_habito_completado",
                Map.of("registro_id", UUID.randomUUID().toString()));
        when(herramientas.ejecutar(actor, pedido)).thenReturn(ResultadoHerramienta.exito("Propuesta pendiente"));
        when(propuestas.pendientesCreadasDesde(eq(actor), any()))
                .thenReturn(List.of(new PropuestaCreada(idPropuesta, "Marcar Meditar", vence)));
        service.iniciar(actor, salida);

        proveedor.oyente.oido("Ya medite");
        proveedor.oyente.pedidoDeHerramienta("llamada-1", pedido);
        // Gemini real manda un turnComplete tras el pedido, sin haber dicho nada: no cierra el turno.
        proveedor.oyente.turnoCompleto();
        proveedor.oyente.dicho("Te deje la propuesta en el chat.");
        proveedor.oyente.turnoCompleto();

        assertThat(salida.eventos).contains(new EventoDeVozEnVivo.Propuesta(idPropuesta, "Marcar Meditar", vence));
        assertThat(salida.eventos).filteredOn(EventoDeVozEnVivo.TurnoCompleto.class::isInstance).hasSize(1);
        List<MensajeRenasia> guardados = guardados(2);
        // La propuesta nacio antes de que el modelo terminara de hablar, y aun asi va al final, como en el chat.
        assertThat(guardados.get(1).contenido())
                .isEqualTo("Te deje la propuesta en el chat.\n\nPropuesta: Marcar Meditar");
    }

    @Test
    @DisplayName("cada turno completo se guarda: lo que dijo la persona y lo que respondio el acompanante")
    void guardaElTurno() {
        service.iniciar(actor, salida);

        proveedor.oyente.oido(" Hola,");
        proveedor.oyente.oido(" como voy hoy?");
        proveedor.oyente.dicho("Vas muy bien.");
        proveedor.oyente.audio(new byte[]{1, 2});
        proveedor.oyente.turnoCompleto();

        List<MensajeRenasia> guardados = guardados(2);
        assertThat(guardados.get(0).rol()).isEqualTo(RolMensaje.USUARIO);
        assertThat(guardados.get(0).contenido()).isEqualTo("Hola, como voy hoy?");
        assertThat(guardados.get(0).agente()).isEqualTo(AgenteConversacional.COMPANION);
        assertThat(guardados.get(1).rol()).isEqualTo(RolMensaje.ASISTENTE);
        assertThat(guardados.get(1).contenido()).isEqualTo("Vas muy bien.");
        assertThat(guardados.get(1).creadoEn()).isAfter(guardados.get(0).creadoEn());
        assertThat(salida.audios).hasSize(1);
        assertThat(salida.eventos).contains(new EventoDeVozEnVivo.Oido(" Hola,"),
                new EventoDeVozEnVivo.Dicho("Vas muy bien."), new EventoDeVozEnVivo.TurnoCompleto());
    }

    @Test
    @DisplayName("si la persona interrumpe, lo que alcanzo a decir el acompanante queda como su respuesta y empieza otro turno")
    void interrupcionCierraLaRespuesta() {
        service.iniciar(actor, salida);

        proveedor.oyente.oido("Cuentame del programa");
        proveedor.oyente.dicho("Son noventa dias, y el primero");
        proveedor.oyente.interrumpido();
        proveedor.oyente.oido("No, espera, solo hoy");
        proveedor.oyente.dicho("Hoy te toca meditar.");
        proveedor.oyente.turnoCompleto();

        assertThat(guardados(4)).extracting(MensajeRenasia::contenido).containsExactly("Cuentame del programa",
                "Son noventa dias, y el primero", "No, espera, solo hoy", "Hoy te toca meditar.");
        assertThat(salida.eventos).contains(new EventoDeVozEnVivo.Interrumpido());
    }

    @Test
    @DisplayName("una interrupcion antes de que el acompanante hable no parte el turno")
    void interrupcionSinRespuesta() {
        service.iniciar(actor, salida);

        proveedor.oyente.oido("Hola,");
        proveedor.oyente.interrumpido();
        proveedor.oyente.oido(" como voy?");
        proveedor.oyente.dicho("Vas bien.");
        proveedor.oyente.turnoCompleto();

        assertThat(guardados(2)).extracting(MensajeRenasia::contenido).containsExactly("Hola, como voy?", "Vas bien.");
    }

    @Test
    @DisplayName("el audio de la persona se reenvia al modelo; despues de cerrar, ya no")
    void audioAlModelo() {
        ConversacionEnVivo conversacion = service.iniciar(actor, salida);

        conversacion.recibirAudio(new byte[]{1, 2, 3, 4});
        conversacion.terminar();
        conversacion.recibirAudio(new byte[]{5, 6});

        assertThat(proveedor.sesion.audios).hasSize(1);
    }

    @Test
    @DisplayName("si el proveedor corta, la app recibe un error y se cierra")
    void proveedorCorta() {
        service.iniciar(actor, salida);

        proveedor.oyente.cerrada("cerrada por Gemini (1011)");

        assertThat(salida.eventos).last().isEqualTo(new EventoDeVozEnVivo.Error(SesionDeVozEnVivo.MENSAJE_SE_CORTO));
        assertThat(salida.motivo).isEqualTo(MotivoDeCierre.ERROR);
    }

    @Test
    @DisplayName("una cuenta suspendida no puede conversar, ni por el handshake ni al iniciar")
    void suspendidaRechazada() {
        conCuenta(UserRole.TRAINEE, UserStatus.SUSPENDED);

        assertThat(service.puedeConversar(actor)).isFalse();
        service.iniciar(actor, salida);

        assertThat(salida.eventos).containsExactly(new EventoDeVozEnVivo.Error(ConversacionEnVivoService.MENSAJE_NO_AUTORIZADO));
        assertThat(salida.motivo).isEqualTo(MotivoDeCierre.ERROR);
        assertThat(proveedor.apertura).isNull();
    }

    @Test
    @DisplayName("una cuenta que no existe no puede conversar")
    void inexistenteRechazada() {
        when(usuarios.findById(actor)).thenReturn(Optional.empty());

        assertThat(service.puedeConversar(actor)).isFalse();
    }

    @Test
    @DisplayName("con la voz en vivo apagada responde no disponible y no abre nada")
    void apagada() {
        proveedor.disponible = false;

        ConversacionEnVivo conversacion = service.iniciar(actor, salida);
        conversacion.recibirAudio(new byte[]{1});

        assertThat(salida.eventos).containsExactly(new EventoDeVozEnVivo.Error(ConversacionEnVivoService.MENSAJE_NO_DISPONIBLE));
        assertThat(salida.motivo).isEqualTo(MotivoDeCierre.NO_DISPONIBLE);
        assertThat(proveedor.apertura).isNull();
    }

    @Test
    @DisplayName("si Gemini no acepta la sesion responde no disponible")
    void geminiNoAbre() {
        proveedor.falla = true;

        service.iniciar(actor, salida);

        assertThat(salida.eventos).containsExactly(new EventoDeVozEnVivo.Error(ConversacionEnVivoService.MENSAJE_NO_DISPONIBLE));
        assertThat(salida.motivo).isEqualTo(MotivoDeCierre.NO_DISPONIBLE);
        assertThat(temporizador.tarea).isNull();
    }

    @Test
    @DisplayName("al llegar a los 15 minutos de sesion se corta aunque quede cuota")
    void duracionMaxima() {
        TiempoDeVozEnVivo tiempo = new TiempoDeVozEnVivo(cuotaPort, id -> LIMA,
                new CuotaDeVozEnVivo(Duration.ofMinutes(60), Duration.ofMinutes(15)), reloj);
        service = new ConversacionEnVivoService(usuarios, proveedor, situacion, memoria, herramientas, propuestas,
                new TurnosDeVozEnVivo(loadConversacion, saveConversacion, mensajes, malestar, compactar, reloj,
                        UUID::randomUUID),
                tiempo, temporizador, reloj);
        service.iniciar(actor, salida);

        reloj.avanzar(Duration.ofMinutes(15));
        temporizador.disparar();

        assertThat(salida.eventos).last().isEqualTo(new EventoDeVozEnVivo.Error(SesionDeVozEnVivo.MENSAJE_DURACION_MAXIMA));
        assertThat(salida.motivo).isEqualTo(MotivoDeCierre.NORMAL);
    }

    @Test
    @DisplayName("si Redis no responde al abrir, no se abre: no hay sesion sin limite")
    void redisCaidoAlAbrir() {
        cuotaPort.falla = true;

        service.iniciar(actor, salida);

        assertThat(salida.eventos).containsExactly(new EventoDeVozEnVivo.Error(ConversacionEnVivoService.MENSAJE_NO_DISPONIBLE));
        assertThat(salida.motivo).isEqualTo(MotivoDeCierre.NO_DISPONIBLE);
        assertThat(proveedor.apertura).isNull();
    }

    @Test
    @DisplayName("si un cobro falla, la conversacion sigue y el cobro siguiente suma tambien esos segundos")
    void cobroQueFallaNoPierdeSegundos() {
        service.iniciar(actor, salida);

        cuotaPort.falla = true;
        reloj.avanzar(Duration.ofSeconds(5));
        temporizador.disparar();
        cuotaPort.falla = false;
        reloj.avanzar(Duration.ofSeconds(5));
        temporizador.disparar();

        assertThat(salida.motivo).isNull();
        assertThat(cuotaPort.usado).containsExactly(Map.entry(DIA_EN_LIMA, Duration.ofSeconds(10)));
    }

    @Test
    @DisplayName("con Redis caido la sesion igual se corta en su maximo")
    void redisCaidoIgualSeCortaEnElMaximo() {
        service.iniciar(actor, salida);
        cuotaPort.falla = true;

        reloj.avanzar(Duration.ofMinutes(15));
        temporizador.disparar();

        assertThat(salida.eventos).last().isEqualTo(new EventoDeVozEnVivo.Error(SesionDeVozEnVivo.MENSAJE_DURACION_MAXIMA));
        assertThat(salida.motivo).isEqualTo(MotivoDeCierre.NORMAL);
    }

    @Test
    @DisplayName("terminar dos veces cierra una sola vez")
    void cierreIdempotente() {
        ConversacionEnVivo conversacion = service.iniciar(actor, salida);

        conversacion.terminar();
        conversacion.terminar();
        proveedor.oyente.cerrada("tarde");

        assertThat(salida.cierres).isEqualTo(1);
        verify(mensajes, never()).save(any());
    }

    private List<MensajeRenasia> guardados(int cuantos) {
        ArgumentCaptor<MensajeRenasia> captor = ArgumentCaptor.forClass(MensajeRenasia.class);
        verify(mensajes, times(cuantos)).save(captor.capture());
        return captor.getAllValues();
    }

    // ---- dobles ------------------------------------------------------------------------------

    private static final class ProveedorFalso implements ConversacionEnVivoPort {
        boolean disponible = true;
        boolean falla;
        Runnable alAbrir = () -> { };
        Apertura apertura;
        Oyente oyente;
        SesionFalsa sesion;

        @Override
        public boolean disponible() {
            return disponible;
        }

        @Override
        public SesionEnVivo abrir(Apertura apertura, Oyente oyente) {
            alAbrir.run();
            if (falla) {
                throw new ConversacionEnVivoNoDisponibleException("no acepto el setup");
            }
            this.apertura = apertura;
            this.oyente = oyente;
            this.sesion = new SesionFalsa();
            return sesion;
        }
    }

    private static final class SesionFalsa implements ConversacionEnVivoPort.SesionEnVivo {
        final List<byte[]> audios = new ArrayList<>();
        final List<String> respuestas = new ArrayList<>();
        boolean cerrada;

        @Override
        public void enviarAudio(byte[] pcm16kHz) {
            audios.add(pcm16kHz);
        }

        @Override
        public void responderHerramienta(String id, String nombre, ResultadoHerramienta resultado) {
            respuestas.add(id + "|" + nombre + "|" + resultado);
        }

        @Override
        public void cerrar() {
            cerrada = true;
        }
    }

    private static final class SalidaGrabada implements SalidaDeVozEnVivo {
        final List<EventoDeVozEnVivo> eventos = new ArrayList<>();
        final List<byte[]> audios = new ArrayList<>();
        MotivoDeCierre motivo;
        int cierres;

        @Override
        public void audio(byte[] pcm16kHz) {
            audios.add(pcm16kHz);
        }

        @Override
        public void evento(EventoDeVozEnVivo evento) {
            eventos.add(evento);
        }

        @Override
        public void cerrar(MotivoDeCierre motivo) {
            this.motivo = motivo;
            cierres++;
        }
    }

    private static final class CuotaEnMemoria implements ControlCuotaVozEnVivoPort {
        final Map<LocalDate, Duration> usado = new HashMap<>();
        boolean falla;

        @Override
        public Duration usadoEn(UserId actorId, LocalDate dia) {
            fallarSiCorresponde();
            return usado.getOrDefault(dia, Duration.ZERO);
        }

        @Override
        public Duration sumar(UserId actorId, LocalDate dia, Duration tramo) {
            fallarSiCorresponde();
            return usado.merge(dia, tramo, Duration::plus);
        }

        private void fallarSiCorresponde() {
            if (falla) {
                throw new IllegalStateException("Redis no responde");
            }
        }
    }

    private static final class TemporizadorManual implements ProgramarTareaPeriodicaPort {
        Runnable tarea;
        boolean cancelada;

        @Override
        public TareaProgramada cada(Duration intervalo, Runnable tarea) {
            this.tarea = tarea;
            return () -> cancelada = true;
        }

        void disparar() {
            tarea.run();
        }
    }

    private static final class RelojMovible implements Clock {
        private Instant ahora;

        RelojMovible(Instant ahora) {
            this.ahora = ahora;
        }

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

        @Override
        public Instant now() {
            return ahora;
        }

        @Override
        public LocalDate today() {
            return ahora.atZone(ZoneOffset.UTC).toLocalDate();
        }
    }
}
