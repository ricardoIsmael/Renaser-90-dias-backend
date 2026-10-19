package com.renaser.os.points.application.services;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.CumplimientoDeHabito;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.application.ports.in.semaforo.ConsultarMensajeDelSemaforoUseCase.ConsultaDelMensaje;
import com.renaser.os.points.application.ports.in.semaforo.ConsultarMensajeDelSemaforoUseCase.MensajeDelDia;
import com.renaser.os.points.application.ports.in.semaforo.ConsultarMiSemaforoUseCase;
import com.renaser.os.points.application.ports.in.semaforo.ConsultarMiSemaforoUseCase.ResumenParaHoy;
import com.renaser.os.points.application.services.DoblesDelMensajeDelSemaforo.CatalogoEnMemoria;
import com.renaser.os.points.application.services.DoblesDelMensajeDelSemaforo.ElegidosEnMemoria;
import com.renaser.os.points.application.services.DoblesDelMensajeDelSemaforo.RelojAjustable;
import com.renaser.os.points.domain.model.semaforo.CumplimientoDelDia;
import com.renaser.os.points.domain.model.semaforo.ReglaDelSemaforo;
import com.renaser.os.points.domain.model.semaforo.mensaje.CanalDelMensaje;
import com.renaser.os.points.domain.model.semaforo.mensaje.ClaveDeLaEleccion;
import com.renaser.os.points.domain.model.semaforo.mensaje.GrupoDelMensaje;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El mensaje personalizado de la tarjeta diaria del chat (D-281): se elige con el canal CHAT, del grupo del color del
 * DÍA de la tarjeta, sin {@code {faltan}}, distinto del que la persona vio en Hoy ese día, y la regla de no repetir
 * en 14 días cuenta los dos canales. Catálogo y elegidos son dobles en memoria con la semántica de las tablas.
 *
 * <p>Reloj a las 04:52 UTC del 29: en Lima son las 23:52 del 28 (regla 02 §3). La fecha que llega es la de la tarjeta
 * (el 28), no la del servidor.
 */
class MensajeDelChatServiceTest {

    private static final Instant LAS_2352_DEL_28_EN_LIMA = Instant.parse("2026-09-29T04:52:00Z");
    private static final LocalDate DIA_28 = LocalDate.of(2026, 9, 28);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final UserId PERSONA = UserId.of(UUID.fromString("00000000-0000-0000-0000-000000000042"));

    private final RelojAjustable reloj = new RelojAjustable(LAS_2352_DEL_28_EN_LIMA);
    private final CatalogoEnMemoria catalogo = new CatalogoEnMemoria();
    private final ElegidosEnMemoria elegidos = new ElegidosEnMemoria();
    private final AtomicInteger indiceDelAzar = new AtomicInteger(0);
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final ParticipacionProgramaFinder participaciones = mock(ParticipacionProgramaFinder.class);
    private final ConsultarMiSemaforoUseCase semaforo = mock(ConsultarMiSemaforoUseCase.class);
    private List<CumplimientoDeHabito> habitos = List.of();
    private ColorSemaforo colorDeLaVentana = ColorSemaforo.VERDE;

    private MensajeDelChatService delChat;
    private MensajeDelSemaforoService deHoy;

    @BeforeEach
    void armar() {
        when(usuarios.findById(any())).thenReturn(Optional.of(
                new UserSummary(PERSONA, "María Quispe", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        when(participaciones.deParticipante(PERSONA)).thenReturn(Optional.of(participacion(20)));
        when(semaforo.resumenParaHoy(PERSONA)).thenAnswer(inv -> Optional.of(resumen(colorDeLaVentana)));
        RecolectorDeDatosDelMensaje recolector = new RecolectorDeDatosDelMensaje(usuarios,
                (persona, desde, hasta) -> habitos, new DatosEstablesDelDia(reloj));
        delChat = new MensajeDelChatService(catalogo, elegidos, n -> Math.min(indiceDelAzar.get(), n - 1), recolector,
                participaciones, semaforo, reloj);
        deHoy = new MensajeDelSemaforoService(catalogo, elegidos, n -> Math.min(indiceDelAzar.get(), n - 1),
                recolector, reloj);
    }

    // ── el mensaje y su grupo ───────────────────────────────────────────────

    @Test
    @DisplayName("escribe el mensaje del color del día con el nombre y guarda UNA fila de canal CHAT con la fecha de la tarjeta")
    void elMensajeEsDelColorDelDia() {
        catalogo.agregar(GrupoDelMensaje.VERDE, "v001", "{nombre}, tu constancia está dando frutos.", "dorado-orgulloso");

        Optional<String> mensaje = delChat.delDia(PERSONA, DIA_28, ColorSemaforo.VERDE);

        assertThat(mensaje).contains("María, tu constancia está dando frutos.");
        assertThat(elegidos.filasDelChat()).containsExactly("2026-09-28|VERDE|v001");
        assertThat(elegidos.filas()).as("no toca lo de Hoy").isEmpty();
    }

    @Test
    @DisplayName("el grupo es el color del DÍA de la tarjeta, no el de la ventana de 7 días que muestra Hoy")
    void elGrupoEsElDelDiaNoElDeLaVentana() {
        colorDeLaVentana = ColorSemaforo.AMARILLO;
        catalogo.agregar(GrupoDelMensaje.AMARILLO, "a001", "Todavía puedes subir.", "azul-reloj");
        catalogo.agregar(GrupoDelMensaje.ROJO, "r001", "Hoy fue duro; mañana se repara.", "azul-firme");

        assertThat(delChat.delDia(PERSONA, DIA_28, ColorSemaforo.ROJO)).contains("Hoy fue duro; mañana se repara.");
        assertThat(elegidos.filasDelChat()).containsExactly("2026-09-28|ROJO|r001");
    }

    @Test
    @DisplayName("un día sin color (SIN_DATOS) no tiene grupo: no hay mensaje y no se toca nada")
    void sinColorNoHayMensaje() {
        catalogo.agregar(GrupoDelMensaje.VERDE, "v001", "Sigue así.", "dorado-sereno");

        assertThat(delChat.delDia(PERSONA, DIA_28, ColorSemaforo.SIN_DATOS)).isEmpty();
        assertThat(elegidos.filasDelChat()).isEmpty();
        assertThat(elegidos.consultas()).isZero();
    }

    @Test
    @DisplayName("sin catálogo para ese grupo no hay mensaje y no se guarda nada")
    void sinCatalogoNoHayMensaje() {
        assertThat(delChat.delDia(PERSONA, DIA_28, ColorSemaforo.VERDE)).isEmpty();
        assertThat(elegidos.filasDelChat()).isEmpty();
    }

    @Test
    @DisplayName("sin participación o sin semáforo (la persona no se mide) no hay mensaje")
    void sinParticipacionONoSeMide() {
        catalogo.agregar(GrupoDelMensaje.VERDE, "v001", "Sigue así.", "dorado-sereno");
        when(participaciones.deParticipante(PERSONA)).thenReturn(Optional.empty());
        assertThat(delChat.delDia(PERSONA, DIA_28, ColorSemaforo.VERDE)).isEmpty();

        when(participaciones.deParticipante(PERSONA)).thenReturn(Optional.of(participacion(20)));
        when(semaforo.resumenParaHoy(PERSONA)).thenReturn(Optional.empty());
        assertThat(delChat.delDia(PERSONA, DIA_28, ColorSemaforo.VERDE)).isEmpty();
        assertThat(elegidos.filasDelChat()).isEmpty();
    }

    // ── sin {faltan} ────────────────────────────────────────────────────────

    @Test
    @DisplayName("un mensaje que pide {faltan} no es candidato en el chat, aunque en Hoy sí lo sería")
    void faltanNoEsCandidatoEnElChat() {
        colorDeLaVentana = ColorSemaforo.AMARILLO;
        catalogo.agregar(GrupoDelMensaje.AMARILLO, "a001", "Te faltan {faltan} para subir.", "azul-reloj");
        catalogo.agregar(GrupoDelMensaje.AMARILLO, "a002", "Todavía puedes subir.", "azul-reloj");
        indiceDelAzar.set(0);

        assertThat(delChat.delDia(PERSONA, DIA_28, ColorSemaforo.AMARILLO)).contains("Todavía puedes subir.");
        assertThat(elegidos.filasDelChat()).containsExactly("2026-09-28|AMARILLO|a002");
    }

    @Test
    @DisplayName("si en el grupo solo hay mensajes con {faltan}, el chat no manda mensaje (y no guarda nada)")
    void soloConFaltanNoHayMensaje() {
        catalogo.agregar(GrupoDelMensaje.AMARILLO, "a001", "Te faltan {faltan} para subir.", "azul-reloj");

        assertThat(delChat.delDia(PERSONA, DIA_28, ColorSemaforo.AMARILLO)).isEmpty();
        assertThat(elegidos.filasDelChat()).isEmpty();
    }

    @Test
    @DisplayName("un mensaje ya elegido que dejó de ser admitido (corrigieron el catálogo con {faltan}) no sale ni rompe")
    void unElegidoQueYaNoSeAdmiteNoRompe() {
        catalogo.agregar(GrupoDelMensaje.AMARILLO, "a001", "Te faltan {faltan} para subir.", "azul-reloj");
        elegidos.sembrar(new ClaveDeLaEleccion(PERSONA, DIA_28, GrupoDelMensaje.AMARILLO, CanalDelMensaje.CHAT), "a001");

        assertThat(delChat.delDia(PERSONA, DIA_28, ColorSemaforo.AMARILLO)).isEmpty();
    }

    // ── distinto del de Hoy y 14 días entre los dos canales ─────────────────

    @Test
    @DisplayName("el mensaje del chat es distinto del que la persona vio en Hoy ese día, aunque el azar apunte al mismo")
    void distintoDelDeHoy() {
        catalogo.agregar(GrupoDelMensaje.VERDE, "v001", "Uno.", "dorado-sereno");
        catalogo.agregar(GrupoDelMensaje.VERDE, "v002", "Dos.", "dorado-sereno");
        assertThat(deHoy.deHoy(consulta(20))).get().extracting(MensajeDelDia::id).isEqualTo("v001");

        Optional<String> delChatDeEseDia = delChat.delDia(PERSONA, DIA_28, ColorSemaforo.VERDE);

        assertThat(delChatDeEseDia).contains("Dos.");
        assertThat(elegidos.filas()).containsExactly("2026-09-28|VERDE|v001");
        assertThat(elegidos.filasDelChat()).containsExactly("2026-09-28|VERDE|v002");
    }

    @Test
    @DisplayName("si lo único posible es lo que Hoy mostró ese día, el chat no lo repite: sale sin mensaje")
    void noRepiteLoDeHoyAunqueSeaLoUnico() {
        catalogo.agregar(GrupoDelMensaje.VERDE, "v001", "Uno.", "dorado-sereno");
        deHoy.deHoy(consulta(20));

        assertThat(delChat.delDia(PERSONA, DIA_28, ColorSemaforo.VERDE)).isEmpty();
        assertThat(elegidos.filasDelChat()).isEmpty();
    }

    @Test
    @DisplayName("si el chat salió primero, Hoy tampoco lo repite ese mismo día")
    void hoyNoRepiteLoDelChat() {
        catalogo.agregar(GrupoDelMensaje.VERDE, "v001", "Uno.", "dorado-sereno");
        catalogo.agregar(GrupoDelMensaje.VERDE, "v002", "Dos.", "dorado-sereno");
        assertThat(delChat.delDia(PERSONA, DIA_28, ColorSemaforo.VERDE)).contains("Uno.");

        assertThat(deHoy.deHoy(consulta(20))).get().extracting(MensajeDelDia::id).isEqualTo("v002");
    }

    @Test
    @DisplayName("la regla de 14 días cuenta los dos canales: ni Hoy ni el chat repiten lo que el otro mostró antes")
    void catorceDiasEntreLosDosCanales() {
        for (int i = 1; i <= 4; i++) {
            catalogo.agregar(GrupoDelMensaje.VERDE, "v00" + i, "Mensaje " + i + ".", "dorado-sereno");
        }
        List<String> vistos = new ArrayList<>();
        for (int dia = 0; dia < 2; dia++) {
            LocalDate fecha = DIA_28.plusDays(dia);
            vistos.add(deHoy.deHoy(consulta(20)).orElseThrow().id());
            vistos.add(idDelChat(delChat.delDia(PERSONA, fecha, ColorSemaforo.VERDE).orElseThrow()));
            reloj.avanzarUnDia();
        }

        assertThat(vistos).as("cuatro mensajes en dos días, de los dos canales, sin repetir ninguno")
                .containsExactly("v001", "v002", "v003", "v004");
    }

    // ── idempotencia y datos ────────────────────────────────────────────────

    @Test
    @DisplayName("el reintento de las 23:55 da el mismo mensaje aunque el azar diga otra cosa, y no escribe otra fila")
    void elReintentoDaElMismoMensaje() {
        catalogo.agregar(GrupoDelMensaje.VERDE, "v001", "Uno.", "dorado-sereno");
        catalogo.agregar(GrupoDelMensaje.VERDE, "v002", "Dos.", "dorado-sereno");
        catalogo.agregar(GrupoDelMensaje.VERDE, "v003", "Tres.", "dorado-sereno");
        indiceDelAzar.set(1);
        Optional<String> primero = delChat.delDia(PERSONA, DIA_28, ColorSemaforo.VERDE);

        indiceDelAzar.set(2);
        Optional<String> segundo = delChat.delDia(PERSONA, DIA_28, ColorSemaforo.VERDE);

        assertThat(primero).contains("Dos.");
        assertThat(segundo).isEqualTo(primero);
        assertThat(elegidos.escrituras()).isEqualTo(1);
    }

    @Test
    @DisplayName("el día y la fase salen de la participación, y los hábitos destacados de la ventana del semáforo")
    void losDatosDeLaPersona() {
        catalogo.agregar(GrupoDelMensaje.AMARILLO, "a001", "Día {dia} en {fase}: {habitoFlojo}, como {habitoFuerte}.",
                "azul-reloj");
        habitos = List.of(new CumplimientoDeHabito("JUGO VERDE", 7, 7), new CumplimientoDeHabito("LECTURA", 7, 2));

        assertThat(delChat.delDia(PERSONA, DIA_28, ColorSemaforo.AMARILLO))
                .contains("Día 20 en El Ciclo Alquímico: lectura, como jugo verde.");
    }

    // ── ayudas ──────────────────────────────────────────────────────────────

    private static String idDelChat(String texto) {
        return "v00" + texto.replaceAll("\\D", "");
    }

    private ConsultaDelMensaje consulta(int dia) {
        return new ConsultaDelMensaje(participacion(dia), resumen(ColorSemaforo.VERDE),
                new CumplimientoDelDia(DIA_28, 0, 0, 0, 0));
    }

    private ResumenParaHoy resumen(ColorSemaforo color) {
        return new ResumenParaHoy(color, new BigDecimal("72.9"), 7, false, ventana(), false, true);
    }

    private static ParticipacionPrograma participacion(int dia) {
        return new ParticipacionPrograma(PERSONA, true, dia, LocalDate.of(2026, 9, 1), LIMA,
                FasePrograma.paraDiaPrograma(dia), UUID.randomUUID(), UserId.of(UUID.randomUUID()), UserRole.TRAINEE,
                false, true);
    }

    /** Los siete días cerrados antes del 28: la ventana vigente que vería Hoy. */
    private static List<DiaDelSemaforo> ventana() {
        List<DiaDelSemaforo> dias = new ArrayList<>();
        for (int atras = 7; atras >= 1; atras--) {
            dias.add(new DiaDelSemaforo(DIA_28.minusDays(atras), EstadoDiaSemaforo.MEDIDO, 90,
                    ReglaDelSemaforo.colorDelDia(90), 1, 1, 0, 0));
        }
        return dias;
    }
}
