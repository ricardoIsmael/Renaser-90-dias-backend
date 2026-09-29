package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.AvisoDeLaPieza;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.EntregaDelPrograma;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.PiezaDelPrograma;
import com.renaser.os.chat.application.ports.in.semaforo.EnviarTarjetasDelSemaforoUseCase.ResultadoDeTarjetas;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.chat.domain.model.semaforo.TarjetaDelSemaforo;
import com.renaser.os.chat.domain.model.semaforo.ColorDeTarjeta;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.api.SemaforoDelDiaFinder;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ProgramasActivadosFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder.ProgramaActivado;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El barrido de la tarjeta diaria (D-223) con dobles: quién la recibe, de qué día y con qué piezas. El reloj
 * se fija a las 04:50 UTC, que en Lima son las 23:50 del día ANTERIOR (regla 02 §3, E-91).
 */
class TarjetasDelSemaforoServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Instant LAS_2350_DEL_28_EN_LIMA = Instant.parse("2026-09-29T04:50:00Z");
    private static final LocalDate DIA_28 = LocalDate.of(2026, 9, 28);

    private final ProgramasActivadosFinder programas = mock(ProgramasActivadosFinder.class);
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final LoadConversacionPort conversaciones = mock(LoadConversacionPort.class);
    private final LoadMensajePort mensajes = mock(LoadMensajePort.class);
    private final AlmacenamientoEnMemoria almacenamiento = new AlmacenamientoEnMemoria();

    private final List<ProgramaActivado> padron = new ArrayList<>();
    private final Map<UserId, UserSummary> perfiles = new HashMap<>();
    private final Map<UserId, DiaDelSemaforo> dias = new HashMap<>();
    private final List<LocalDate> fechasPedidas = new ArrayList<>();
    private final List<EntregaDelPrograma> entregas = new ArrayList<>();
    private final Set<UserId> fallan = new HashSet<>();

    private final SemaforoDelDiaFinder semaforo = (ids, fecha) -> {
        fechasPedidas.add(fecha);
        Map<UserId, DiaDelSemaforo> suyos = new LinkedHashMap<>();
        ids.forEach(id -> Optional.ofNullable(dias.get(id)).ifPresent(d -> suyos.put(id, d)));
        return suyos;
    };

    private final EnviarMensajeDelProgramaUseCase delPrograma = new EnviarMensajeDelProgramaUseCase() {
        @Override
        public Mensaje enviarDelPrograma(ConversacionId c, UserId s, com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma x) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int enviarUnaVez(EntregaDelPrograma entrega) {
            if (fallan.contains(entrega.sobreQuien())) {
                throw new IllegalStateException("falla simulada");
            }
            entregas.add(entrega);
            return entrega.piezas().size();
        }
    };

    private TarjetasDelSemaforoService servicio(Instant ahora) {
        TarjetasDelSemaforoPublicadas publicadas = new TarjetasDelSemaforoPublicadas(almacenamiento,
                color -> ("jpeg-" + color).getBytes());
        TarjetaEnSoporte enSoporte = new TarjetaEnSoporte(conversaciones, mensajes, publicadas, delPrograma);
        return new TarjetasDelSemaforoService(programas, usuarios, semaforo, enSoporte, FixedClock.at(ahora));
    }

    @BeforeEach
    void dobles() {
        when(programas.pagina(anyInt(), anyInt())).thenAnswer(inv -> inv.<Integer>getArgument(0) == 0 ? padron : List.of());
        when(usuarios.findByIds(anyCollection())).thenAnswer(inv -> {
            Map<UserId, UserSummary> pedidos = new HashMap<>();
            inv.<Collection<UserId>>getArgument(0).forEach(id -> Optional.ofNullable(perfiles.get(id)).ifPresent(p -> pedidos.put(id, p)));
            return pedidos;
        });
        when(conversaciones.porClaveDirecta(anyString())).thenAnswer(inv -> {
            String clave = inv.getArgument(0);
            UUID aprendiz = UUID.fromString(clave.substring(clave.indexOf(':') + 1));
            return Optional.of(Conversacion.crearSoporte(ConversacionId.of(UUID.nameUUIDFromBytes(clave.getBytes())),
                    UserId.of(aprendiz), "Ana – Formación Renaser", LAS_2350_DEL_28_EN_LIMA));
        });
        when(mensajes.porId(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
    }

    private UserId persona(UserRole rol, UserStatus estado, DiaDelSemaforo dia) {
        UserId id = UserId.of(UUID.randomUUID());
        padron.add(new ProgramaActivado(id, LIMA, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 12, 18)));
        perfiles.put(id, new UserSummary(id, "Persona", null, rol, estado));
        if (dia != null) {
            dias.put(id, dia);
        }
        return id;
    }

    private static DiaDelSemaforo medido(int porcentaje, ColorSemaforo color) {
        return new DiaDelSemaforo(DIA_28, EstadoDiaSemaforo.MEDIDO, porcentaje, color, 10, porcentaje / 10, 0, 0);
    }

    @Test
    @DisplayName("a las 04:50 UTC pide el día 28 de Lima (nunca el 29 del servidor) y manda imagen sin aviso + texto solo para la aprendiz")
    void mandaLaDelDiaDeLima() {
        almacenamiento.guarda = true;
        UserId ana = persona(UserRole.TRAINEE, UserStatus.ACTIVE, medido(85, ColorSemaforo.VERDE));

        ResultadoDeTarjetas resultado = servicio(LAS_2350_DEL_28_EN_LIMA).enviarLasQueTocan();

        assertThat(fechasPedidas).containsExactly(DIA_28);
        assertThat(resultado).isEqualTo(new ResultadoDeTarjetas(1, 1, 0));
        EntregaDelPrograma entrega = entregas.getFirst();
        assertThat(entrega.sobreQuien()).isEqualTo(ana);
        TarjetaDelSemaforo esperada = new TarjetaDelSemaforo(ana, DIA_28, ColorDeTarjeta.VERDE, 85);
        assertThat(entrega.piezas()).extracting(PiezaDelPrograma::id)
                .containsExactly(esperada.idDeLaImagen(), esperada.idDelTexto());
        assertThat(entrega.piezas().get(0).contenido().mediaRuta()).isEqualTo("semaforo/tarjetas/verde-v1.jpg");
        assertThat(entrega.piezas().get(0).aviso()).isEqualTo(AvisoDeLaPieza.SIN_AVISO);
        assertThat(entrega.piezas().get(1).contenido().texto()).isEqualTo("Hoy llevas 85 % de tus hábitos.");
        assertThat(entrega.piezas().get(1).aviso()).isEqualTo(AvisoDeLaPieza.SOLO_A_QUIEN_SE_REFIERE);
    }

    @Test
    @DisplayName("fuera de las 23:50–23:59 locales no mira el semáforo ni manda nada")
    void fueraDeHoraNada() {
        persona(UserRole.TRAINEE, UserStatus.ACTIVE, medido(85, ColorSemaforo.VERDE));

        assertThat(servicio(Instant.parse("2026-09-29T10:00:00Z")).enviarLasQueTocan()).isEqualTo(ResultadoDeTarjetas.NADA);
        assertThat(servicio(Instant.parse("2026-09-29T05:00:00Z")).enviarLasQueTocan()).isEqualTo(ResultadoDeTarjetas.NADA);
        assertThat(fechasPedidas).isEmpty();
        assertThat(entregas).isEmpty();
    }

    @Test
    @DisplayName("sin tarjeta: staff con programa propio, cuenta suspendida o inactiva, día sin datos, fuera del programa, pausa")
    void quienNoRecibe() {
        persona(UserRole.MENTOR, UserStatus.ACTIVE, medido(90, ColorSemaforo.VERDE));
        persona(UserRole.ADMIN, UserStatus.ACTIVE, medido(90, ColorSemaforo.VERDE));
        persona(UserRole.TRAINEE, UserStatus.SUSPENDED, medido(40, ColorSemaforo.ROJO));
        persona(UserRole.TRAINEE, UserStatus.INACTIVE, medido(40, ColorSemaforo.ROJO));
        persona(UserRole.TRAINEE, UserStatus.ACTIVE, DiaDelSemaforo.sinPorcentaje(DIA_28, EstadoDiaSemaforo.SIN_DATOS));
        persona(UserRole.TRAINEE, UserStatus.ACTIVE, DiaDelSemaforo.sinPorcentaje(DIA_28, EstadoDiaSemaforo.FUERA_DEL_PROGRAMA));
        persona(UserRole.TRAINEE, UserStatus.ACTIVE, DiaDelSemaforo.sinPorcentaje(DIA_28, EstadoDiaSemaforo.PAUSADO));
        persona(UserRole.TRAINEE, UserStatus.ACTIVE, DiaDelSemaforo.sinPorcentaje(DIA_28, EstadoDiaSemaforo.CUENTA_SUSPENDIDA));
        persona(UserRole.TRAINEE, UserStatus.ACTIVE, null); // sin programa medible

        ResultadoDeTarjetas resultado = servicio(LAS_2350_DEL_28_EN_LIMA).enviarLasQueTocan();

        assertThat(entregas).isEmpty();
        assertThat(resultado.enSuHora()).as("solo los aprendices activos").isEqualTo(5);
        assertThat(resultado.enviadas()).isZero();
    }

    @Test
    @DisplayName("con almacenamiento noop sale solo el texto")
    void noopSoloTexto() {
        almacenamiento.guarda = false;
        persona(UserRole.TRAINEE, UserStatus.ACTIVE, medido(65, ColorSemaforo.AMARILLO));

        servicio(LAS_2350_DEL_28_EN_LIMA).enviarLasQueTocan();

        assertThat(entregas.getFirst().piezas()).singleElement()
                .satisfies(p -> assertThat(p.contenido().texto()).isEqualTo("Hoy llevas 65 % de tus hábitos."));
        assertThat(almacenamiento.subidas).isEmpty();
    }

    @Test
    @DisplayName("cada color se sube UNA vez y todos apuntan a la misma ruta; si ya estaba, no se sube")
    void subeUnaSolaVez() {
        almacenamiento.guarda = true;
        almacenamiento.objetos.put("semaforo/tarjetas/verde-v1.jpg", new byte[]{1, 2, 3});
        persona(UserRole.TRAINEE, UserStatus.ACTIVE, medido(30, ColorSemaforo.ROJO));
        persona(UserRole.TRAINEE, UserStatus.ACTIVE, medido(50, ColorSemaforo.ROJO));
        persona(UserRole.TRAINEE, UserStatus.ACTIVE, medido(90, ColorSemaforo.VERDE));

        servicio(LAS_2350_DEL_28_EN_LIMA).enviarLasQueTocan();

        assertThat(almacenamiento.subidas).containsExactly("semaforo/tarjetas/rojo-v1.jpg");
        assertThat(entregas).hasSize(3).allSatisfy(e -> assertThat(e.piezas()).hasSize(2));
    }

    @Test
    @DisplayName("si S3 falla sale igual el texto; si el texto ya salió, no se mira nada más")
    void s3FallaOYaEstaba() {
        almacenamiento.guarda = true;
        almacenamiento.fallaAlLeer = true;
        UserId ana = persona(UserRole.TRAINEE, UserStatus.ACTIVE, medido(85, ColorSemaforo.VERDE));
        UserId beto = persona(UserRole.TRAINEE, UserStatus.ACTIVE, medido(85, ColorSemaforo.VERDE));
        MensajeId textoDeBeto = new TarjetaDelSemaforo(beto, DIA_28, ColorDeTarjeta.VERDE, 85).idDelTexto();
        when(mensajes.porId(textoDeBeto)).thenReturn(Optional.of(mock(Mensaje.class)));

        ResultadoDeTarjetas resultado = servicio(LAS_2350_DEL_28_EN_LIMA).enviarLasQueTocan();

        assertThat(entregas).singleElement().satisfies(e -> {
            assertThat(e.sobreQuien()).isEqualTo(ana);
            assertThat(e.piezas()).hasSize(1);
        });
        assertThat(resultado).isEqualTo(new ResultadoDeTarjetas(2, 1, 0));
    }

    @Test
    @DisplayName("uno que falla no frena a los demás")
    void unoQueFallaNoFrena() {
        UserId ana = persona(UserRole.TRAINEE, UserStatus.ACTIVE, medido(85, ColorSemaforo.VERDE));
        UserId beto = persona(UserRole.TRAINEE, UserStatus.ACTIVE, medido(85, ColorSemaforo.VERDE));
        fallan.add(ana);

        ResultadoDeTarjetas resultado = servicio(LAS_2350_DEL_28_EN_LIMA).enviarLasQueTocan();

        assertThat(resultado).isEqualTo(new ResultadoDeTarjetas(2, 1, 1));
        assertThat(entregas).singleElement().satisfies(e -> assertThat(e.sobreQuien()).isEqualTo(beto));
    }

    @Test
    @DisplayName("sin chat de soporte no hay tarjeta")
    void sinSoporte() {
        persona(UserRole.TRAINEE, UserStatus.ACTIVE, medido(85, ColorSemaforo.VERDE));
        // doReturn: con when(...) la respuesta anterior se ejecutaría con la clave vacía del matcher.
        org.mockito.Mockito.doReturn(Optional.empty()).when(conversaciones).porClaveDirecta(anyString());

        assertThat(servicio(LAS_2350_DEL_28_EN_LIMA).enviarLasQueTocan()).isEqualTo(new ResultadoDeTarjetas(1, 0, 0));
        assertThat(entregas).isEmpty();
    }

    /** Almacenamiento con objetos de verdad (o de marcador, con {@code guarda = false}). */
    static final class AlmacenamientoEnMemoria implements AlmacenamientoPort {
        boolean guarda;
        boolean fallaAlLeer;
        final Map<String, byte[]> objetos = new HashMap<>();
        final List<String> subidas = new ArrayList<>();

        @Override
        public URI firmarSubida(String ruta, String tipoContenido, Duration validez) {
            throw new UnsupportedOperationException();
        }

        @Override
        public URI firmarLectura(String ruta, Duration validez) {
            return URI.create("https://s3/" + ruta);
        }

        @Override
        public URI urlPublica(String ruta) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void subir(String ruta, byte[] contenido, String tipoContenido) {
            subidas.add(ruta);
            objetos.put(ruta, contenido);
        }

        @Override
        public void borrar(String ruta) {
            objetos.remove(ruta);
        }

        @Override
        public Optional<byte[]> leer(String ruta, long pesoMaximo) {
            if (fallaAlLeer) {
                throw new IllegalStateException("S3 no responde");
            }
            return Optional.ofNullable(objetos.get(ruta));
        }

        @Override
        public boolean guardaObjetos() {
            return guarda;
        }
    }
}
