package com.renaser.os.points.application.services;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.CumplimientoDeHabito;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.application.ports.in.semaforo.ConsultarMensajeDelSemaforoUseCase.ConsultaDelMensaje;
import com.renaser.os.points.application.ports.in.semaforo.ConsultarMensajeDelSemaforoUseCase.MensajeDelDia;
import com.renaser.os.points.application.ports.in.semaforo.ConsultarMiSemaforoUseCase.ResumenParaHoy;
import com.renaser.os.points.application.services.DoblesDelMensajeDelSemaforo.CatalogoEnMemoria;
import com.renaser.os.points.application.services.DoblesDelMensajeDelSemaforo.ElegidosEnMemoria;
import com.renaser.os.points.application.services.DoblesDelMensajeDelSemaforo.RelojAjustable;
import com.renaser.os.points.domain.model.semaforo.CumplimientoDelDia;
import com.renaser.os.points.domain.model.semaforo.ReglaDelSemaforo;
import com.renaser.os.points.domain.model.semaforo.mensaje.GrupoDelMensaje;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El mensaje del día del semáforo (D-277, spec 006): se elige una vez por persona, día local y grupo; se mantiene
 * el resto del día; no repite en 14 días; se escribe con los datos de cada lectura. Catálogo y elegidos son
 * dobles en memoria con la misma semántica que las tablas (PK por persona, fecha y grupo).
 */
class MensajeDelSemaforoServiceTest {

    /** 10:00 UTC en Lima son las 05:00 del mismo día: el 19 de octubre. */
    private static final Instant MEDIODIA_UTC = Instant.parse("2026-10-19T10:00:00Z");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final UserId PERSONA = UserId.of(UUID.fromString("00000000-0000-0000-0000-000000000042"));

    private final RelojAjustable reloj = new RelojAjustable(MEDIODIA_UTC);
    private final CatalogoEnMemoria catalogo = new CatalogoEnMemoria();
    private final ElegidosEnMemoria elegidos = new ElegidosEnMemoria();
    private final AtomicInteger indiceDelAzar = new AtomicInteger(0);
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final AtomicInteger lecturasDeDestacados = new AtomicInteger();
    private List<CumplimientoDeHabito> habitos = List.of();
    private CumplimientoDelDia deHoy = new CumplimientoDelDia(LocalDate.of(2026, 10, 19), 0, 0, 0, 0);

    private MensajeDelSemaforoService servicio;

    @BeforeEach
    void armar() {
        when(usuarios.findById(any())).thenReturn(Optional.of(
                new UserSummary(PERSONA, "María Quispe", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        RecolectorDeDatosDelMensaje recolector = new RecolectorDeDatosDelMensaje(usuarios,
                (persona, desde, hasta) -> {
                    lecturasDeDestacados.incrementAndGet();
                    return habitos;
                }, new DatosEstablesDelDia(reloj));
        servicio = new MensajeDelSemaforoService(catalogo, elegidos, n -> Math.min(indiceDelAzar.get(), n - 1),
                recolector, reloj);
    }

    // ── la elección y su estabilidad ────────────────────────────────────────

    @Test
    @DisplayName("la primera lectura del día elige, escribe con el nombre y guarda una sola fila")
    void laPrimeraLecturaEligeYGuarda() {
        catalogo.agregar(GrupoDelMensaje.VERDE, "v001", "{nombre}, tu constancia da frutos.", "dorado-orgulloso");

        Optional<MensajeDelDia> mensaje = servicio.deHoy(consulta(ColorSemaforo.VERDE, 20));

        assertThat(mensaje).get().satisfies(m -> {
            assertThat(m.id()).isEqualTo("v001");
            assertThat(m.texto()).isEqualTo("María, tu constancia da frutos.");
            assertThat(m.sticker()).isEqualTo("dorado-orgulloso");
        });
        assertThat(elegidos.filas()).containsExactly("2026-10-19|VERDE|v001");
    }

    @Test
    @DisplayName("volver a abrir Hoy el mismo día da el mismo mensaje, aunque el azar diga otra cosa, sin escribir otra fila")
    void elMismoDiaElMismoMensaje() {
        catalogo.agregar(GrupoDelMensaje.VERDE, "v001", "Uno.", "dorado-sereno");
        catalogo.agregar(GrupoDelMensaje.VERDE, "v002", "Dos.", "dorado-sereno");
        catalogo.agregar(GrupoDelMensaje.VERDE, "v003", "Tres.", "dorado-sereno");
        indiceDelAzar.set(1);
        String primero = servicio.deHoy(consulta(ColorSemaforo.VERDE, 20)).orElseThrow().id();

        indiceDelAzar.set(2);
        String segundo = servicio.deHoy(consulta(ColorSemaforo.VERDE, 20)).orElseThrow().id();

        assertThat(primero).isEqualTo("v002");
        assertThat(segundo).isEqualTo(primero);
        assertThat(elegidos.escrituras()).isEqualTo(1);
    }

    @Test
    @DisplayName("al día siguiente elige otro y no repite los de los últimos 14 días mientras queden otros del grupo")
    void alDiaSiguienteNoRepite() {
        for (int i = 1; i <= 4; i++) {
            catalogo.agregar(GrupoDelMensaje.VERDE, "v00" + i, "Mensaje " + i + ".", "dorado-sereno");
        }
        List<String> vistos = new ArrayList<>();
        for (int dia = 0; dia < 4; dia++) {
            vistos.add(servicio.deHoy(consulta(ColorSemaforo.VERDE, 20)).orElseThrow().id());
            reloj.avanzarUnDia();
        }

        assertThat(vistos).as("con el azar fijo en 0 recorre el catálogo entero sin repetir").doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("si ya vio todos los de su grupo, vuelve a elegir entre todos menos el de ayer")
    void catalogoAgotado() {
        catalogo.agregar(GrupoDelMensaje.VERDE, "v001", "Uno.", "dorado-sereno");
        catalogo.agregar(GrupoDelMensaje.VERDE, "v002", "Dos.", "dorado-sereno");
        List<String> vistos = new ArrayList<>();

        for (int dia = 0; dia < 5; dia++) {
            vistos.add(servicio.deHoy(consulta(ColorSemaforo.VERDE, 20)).orElseThrow().id());
            reloj.avanzarUnDia();
        }

        assertThat(vistos).containsExactly("v001", "v002", "v001", "v002", "v001");
    }

    @Test
    @DisplayName("si el color cambia durante el día entra otro mensaje, del color nuevo; si vuelve, vuelve el primero")
    void cambioDeColorEligeUnoNuevo() {
        catalogo.agregar(GrupoDelMensaje.VERDE, "v001", "Sigue así.", "dorado-sereno");
        catalogo.agregar(GrupoDelMensaje.AMARILLO, "a001", "Todavía puedes subir.", "azul-reloj");

        MensajeDelDia verde = servicio.deHoy(consulta(ColorSemaforo.VERDE, 20)).orElseThrow();
        MensajeDelDia amarillo = servicio.deHoy(consulta(ColorSemaforo.AMARILLO, 20)).orElseThrow();
        MensajeDelDia denuevoVerde = servicio.deHoy(consulta(ColorSemaforo.VERDE, 20)).orElseThrow();

        assertThat(verde.id()).isEqualTo("v001");
        assertThat(amarillo.id()).isEqualTo("a001");
        assertThat(denuevoVerde.id()).isEqualTo("v001");
        assertThat(elegidos.filas()).containsExactlyInAnyOrder("2026-10-19|VERDE|v001", "2026-10-19|AMARILLO|a001");
    }

    @Test
    @DisplayName("el día es el de la zona de la persona: a las 03:00 UTC en Lima todavía es el 18")
    void elDiaEsElLocalDeLaPersona() {
        reloj.ajustar(Instant.parse("2026-10-19T03:00:00Z"));
        catalogo.agregar(GrupoDelMensaje.VERDE, "v001", "Sigue así.", "dorado-sereno");

        servicio.deHoy(consulta(ColorSemaforo.VERDE, 20));

        assertThat(elegidos.filas()).containsExactly("2026-10-18|VERDE|v001");
    }

    @Test
    @DisplayName("dos lecturas que se cruzan terminan con el mismo mensaje: queda el de la que llegó primero")
    void dosLecturasQueSeCruzan() {
        catalogo.agregar(GrupoDelMensaje.VERDE, "v001", "Uno.", "dorado-sereno");
        catalogo.agregar(GrupoDelMensaje.VERDE, "v002", "Dos.", "dorado-sereno");
        elegidos.alRegistrarYaHabiaGanado("v002");

        MensajeDelDia mensaje = servicio.deHoy(consulta(ColorSemaforo.VERDE, 20)).orElseThrow();

        assertThat(mensaje.id()).isEqualTo("v002");
    }

    // ── los datos ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("sin hábito flojo nunca recibe un mensaje que lo pida")
    void sinHabitoFlojoNoRecibeUnMensajeQueLoPida() {
        catalogo.agregar(GrupoDelMensaje.AMARILLO, "a001", "Dedica tiempo a {habitoFlojo}.", "azul-reloj");
        catalogo.agregar(GrupoDelMensaje.AMARILLO, "a002", "Todavía puedes subir.", "azul-reloj");
        habitos = List.of();

        Optional<MensajeDelDia> mensaje = servicio.deHoy(consulta(ColorSemaforo.AMARILLO, 20));

        assertThat(mensaje).get().extracting(MensajeDelDia::id).isEqualTo("a002");
    }

    @Test
    @DisplayName("con hábitos de verdad, el flojo se escribe con el nombre que ve la persona")
    void conHabitoFlojoLoNombra() {
        catalogo.agregar(GrupoDelMensaje.AMARILLO, "a001", "Dedica tiempo a {habitoFlojo}.", "azul-reloj");
        habitos = List.of(new CumplimientoDeHabito("JUGO VERDE", 7, 7), new CumplimientoDeHabito("LECTURA", 7, 2));

        assertThat(servicio.deHoy(consulta(ColorSemaforo.AMARILLO, 20))).get()
                .extracting(MensajeDelDia::texto).isEqualTo("Dedica tiempo a lectura.");
    }

    @Test
    @DisplayName("{faltan} se escribe con el valor de cada lectura: baja al cumplir un hábito, y con uno dice «falta 1»")
    void faltanSeActualiza() {
        catalogo.agregar(GrupoDelMensaje.AMARILLO, "a001", "Te faltan {faltan} para subir al siguiente color.",
                "azul-reloj");
        deHoy = elDiaDeHoy(3, 1);
        List<DiaDelSemaforo> ventana = ventanaAmarilla();

        String antes = servicio.deHoy(consulta(ColorSemaforo.AMARILLO, 20, ventana)).orElseThrow().texto();
        deHoy = elDiaDeHoy(3, 2);
        MensajeDelDia despues = servicio.deHoy(consulta(ColorSemaforo.AMARILLO, 20, ventana)).orElseThrow();

        assertThat(antes).isEqualTo("Te faltan 2 para subir al siguiente color.");
        assertThat(despues.id()).isEqualTo("a001");
        assertThat(despues.texto()).isEqualTo("Te falta 1 para subir al siguiente color.");
    }

    @Test
    @DisplayName("si lo que pedía el mensaje desaparece en el día (ya cumplió lo que faltaba), sale otro del grupo, el mismo en cada lectura")
    void siElDatoDesapareceSaleOtro() {
        catalogo.agregar(GrupoDelMensaje.AMARILLO, "a001", "Te faltan {faltan} para subir.", "azul-reloj");
        catalogo.agregar(GrupoDelMensaje.AMARILLO, "a002", "Todavía puedes subir.", "azul-reloj");
        catalogo.agregar(GrupoDelMensaje.AMARILLO, "a003", "Haz algo hoy.", "azul-reloj");
        deHoy = elDiaDeHoy(3, 1);
        List<DiaDelSemaforo> ventana = ventanaAmarilla();
        assertThat(servicio.deHoy(consulta(ColorSemaforo.AMARILLO, 20, ventana)).orElseThrow().id()).isEqualTo("a001");

        deHoy = elDiaDeHoy(3, 3);
        String primera = servicio.deHoy(consulta(ColorSemaforo.AMARILLO, 20, ventana)).orElseThrow().id();
        String segunda = servicio.deHoy(consulta(ColorSemaforo.AMARILLO, 20, ventana)).orElseThrow().id();

        assertThat(primera).isIn("a002", "a003").isEqualTo(segunda);
        assertThat(elegidos.filas()).as("lo guardado sigue siendo lo que se eligió").containsExactly("2026-10-19|AMARILLO|a001");
    }

    @Test
    @DisplayName("el día y la fase salen de la participación; antes del Día 1 no hay ninguno de los dos")
    void diaYFase() {
        catalogo.agregar(GrupoDelMensaje.VERDE, "v001", "Día {dia} en {fase}.", "dorado-sereno");

        assertThat(servicio.deHoy(consulta(ColorSemaforo.VERDE, 20))).get().extracting(MensajeDelDia::texto)
                .isEqualTo("Día 20 en El Ciclo Alquímico.");
    }

    // ── el costo en consultas (SC-004) ─────────────────────────────────────

    @Test
    @DisplayName("releer el mismo día pregunta por lo elegido una vez por lectura y no trae el historial")
    void lasLecturasRepetidasNoTraenElHistorial() {
        catalogo.agregar(GrupoDelMensaje.VERDE, "v001", "{nombre}, sigue así.", "dorado-sereno");

        for (int lectura = 0; lectura < 4; lectura++) {
            servicio.deHoy(consulta(ColorSemaforo.VERDE, 20));
        }

        assertThat(elegidos.consultas()).as("una consulta por lectura").isEqualTo(4);
        assertThat(elegidos.historialesLeidos()).as("el historial solo la primera vez, cuando se elige").isEqualTo(1);
        assertThat(elegidos.escrituras()).isEqualTo(1);
    }

    @Test
    @DisplayName("el nombre y los hábitos destacados se leen una sola vez en el día, no en cada lectura")
    void elNombreYLosDestacadosSeLeenUnaVez() {
        catalogo.agregar(GrupoDelMensaje.AMARILLO, "a001", "{nombre}, dedica tiempo a {habitoFlojo}.", "azul-reloj");
        habitos = List.of(new CumplimientoDeHabito("JUGO VERDE", 7, 7), new CumplimientoDeHabito("LECTURA", 7, 2));

        for (int lectura = 0; lectura < 5; lectura++) {
            servicio.deHoy(consulta(ColorSemaforo.AMARILLO, 20));
        }

        verify(usuarios, times(1)).findById(any());
        assertThat(lecturasDeDestacados).hasValue(1);
    }

    @Test
    @DisplayName("al día local siguiente se vuelven a leer; con el reloj en la madrugada UTC el día cambia a las 05:00 UTC")
    void alDiaLocalSiguienteSeVuelvenALeer() {
        catalogo.agregar(GrupoDelMensaje.VERDE, "v001", "{nombre}, sigue así.", "dorado-sereno");
        catalogo.agregar(GrupoDelMensaje.VERDE, "v002", "{nombre}, vas bien.", "dorado-sereno");
        reloj.ajustar(Instant.parse("2026-10-19T03:00:00Z"));
        servicio.deHoy(consulta(ColorSemaforo.VERDE, 20));

        reloj.ajustar(Instant.parse("2026-10-19T04:59:59Z"));
        servicio.deHoy(consulta(ColorSemaforo.VERDE, 20));
        verify(usuarios, times(1)).findById(any());

        reloj.ajustar(Instant.parse("2026-10-19T05:00:00Z"));
        servicio.deHoy(consulta(ColorSemaforo.VERDE, 20));
        verify(usuarios, times(2)).findById(any());
    }

    @Test
    @DisplayName("{faltan} sale de lo de hoy que trae la consulta: cambia en cada lectura sin leer nada más")
    void faltanSaleDeLaConsulta() {
        catalogo.agregar(GrupoDelMensaje.AMARILLO, "a001", "Te faltan {faltan} para subir.", "azul-reloj");
        List<DiaDelSemaforo> ventana = ventanaAmarilla();

        deHoy = elDiaDeHoy(3, 0);
        String sinNada = servicio.deHoy(consulta(ColorSemaforo.AMARILLO, 20, ventana)).orElseThrow().texto();
        deHoy = elDiaDeHoy(3, 2);
        String conDos = servicio.deHoy(consulta(ColorSemaforo.AMARILLO, 20, ventana)).orElseThrow().texto();

        assertThat(sinNada).isEqualTo("Te faltan 3 para subir.");
        assertThat(conDos).isEqualTo("Te falta 1 para subir.");
        assertThat(elegidos.consultas()).isEqualTo(2);
        verify(usuarios, times(1)).findById(any());
        assertThat(lecturasDeDestacados).as("al elegir se leyó todo una vez; la segunda lectura no leyó nada").hasValue(1);
    }

    @Test
    @DisplayName("si hoy no se mide (en pausa, o reactivada hoy) no hay «cuánto falta»: ese mensaje no es candidato")
    void hoyQueNoSeMideNoDaFaltan() {
        catalogo.agregar(GrupoDelMensaje.AMARILLO, "a001", "Te faltan {faltan} para subir.", "azul-reloj");
        deHoy = elDiaDeHoy(3, 1);

        assertThat(servicio.deHoy(consulta(ColorSemaforo.AMARILLO, 20, ventanaAmarilla(), false)))
                .as("sin otro mensaje que no pida el dato").isEmpty();
        assertThat(servicio.deHoy(consulta(ColorSemaforo.AMARILLO, 20, ventanaAmarilla(), true)))
                .get().extracting(MensajeDelDia::id).isEqualTo("a001");
    }

    @Test
    @DisplayName("con hábitos cumplidos y nada más programado hoy tampoco hay «cuánto falta»")
    void sinNadaQueFaltarNoHayFaltan() {
        catalogo.agregar(GrupoDelMensaje.AMARILLO, "a001", "Te faltan {faltan} para subir.", "azul-reloj");
        deHoy = elDiaDeHoy(3, 3);

        assertThat(servicio.deHoy(consulta(ColorSemaforo.AMARILLO, 20, ventanaAmarilla()))).isEmpty();
    }

    // ── los grupos sin color ────────────────────────────────────────────────

    @Test
    @DisplayName("después del día 90 el mensaje es del grupo GRADUADO")
    void graduado() {
        catalogo.agregar(GrupoDelMensaje.VERDE, "v001", "Sigue así.", "dorado-sereno");
        catalogo.agregar(GrupoDelMensaje.GRADUADO, "s013", "{nombre}, completaste Renaser.", "dorado-trofeo");
        ResumenParaHoy resumen = new ResumenParaHoy(ColorSemaforo.VERDE, new BigDecimal("90.0"), 7, false,
                ventanaVerde(), true, false);

        Optional<MensajeDelDia> mensaje = servicio.deHoy(new ConsultaDelMensaje(participacion(90), resumen, deHoy));

        assertThat(mensaje).get().extracting(MensajeDelDia::id).isEqualTo("s013");
    }

    @Test
    @DisplayName("sin ningún mensaje posible para su situación no hay mensaje: la tarjeta sale como antes")
    void sinCatalogoNoHayMensaje() {
        assertThat(servicio.deHoy(consulta(ColorSemaforo.VERDE, 20))).isEmpty();
        assertThat(elegidos.filas()).isEmpty();
    }

    // ── ayudas ──────────────────────────────────────────────────────────────

    private ConsultaDelMensaje consulta(ColorSemaforo color, int dia) {
        return consulta(color, dia, ventanaVerde());
    }

    private ConsultaDelMensaje consulta(ColorSemaforo color, int dia, List<DiaDelSemaforo> ventana) {
        return consulta(color, dia, ventana, true);
    }

    private ConsultaDelMensaje consulta(ColorSemaforo color, int dia, List<DiaDelSemaforo> ventana, boolean seMideHoy) {
        ResumenParaHoy resumen = new ResumenParaHoy(color, new BigDecimal("72.9"), 7, false, ventana, false, seMideHoy);
        return new ConsultaDelMensaje(participacion(dia), resumen, deHoy);
    }

    private static ParticipacionPrograma participacion(int dia) {
        return new ParticipacionPrograma(PERSONA, true, dia, LocalDate.of(2026, 9, 1), LIMA,
                FasePrograma.paraDiaPrograma(dia), UUID.randomUUID(), UserId.of(UUID.randomUUID()), UserRole.TRAINEE,
                false, true);
    }

    private static List<DiaDelSemaforo> ventanaVerde() {
        List<DiaDelSemaforo> dias = new ArrayList<>();
        for (int atras = 7; atras >= 1; atras--) {
            dias.add(new DiaDelSemaforo(LocalDate.of(2026, 10, 19).minusDays(atras), EstadoDiaSemaforo.MEDIDO, 90,
                    ColorSemaforo.VERDE, 1, 1, 0, 0));
        }
        return dias;
    }

    /** Primer día 50 y los seis últimos 100, 80, 70, 70, 70, 70 (suman 460): hoy al 100 % la ventana de mañana da 80,0. */
    private static List<DiaDelSemaforo> ventanaAmarilla() {
        int[] porcentajes = {50, 100, 80, 70, 70, 70, 70};
        List<DiaDelSemaforo> dias = new ArrayList<>();
        for (int i = 0; i < porcentajes.length; i++) {
            dias.add(new DiaDelSemaforo(LocalDate.of(2026, 10, 19).minusDays(7 - i), EstadoDiaSemaforo.MEDIDO,
                    porcentajes[i], ReglaDelSemaforo.colorDelDia(porcentajes[i]), 1, 1, 0, 0));
        }
        return dias;
    }

    private static CumplimientoDelDia elDiaDeHoy(int programados, int cumplidos) {
        return new CumplimientoDelDia(LocalDate.of(2026, 10, 19), programados, cumplidos, 0, 0);
    }
}
