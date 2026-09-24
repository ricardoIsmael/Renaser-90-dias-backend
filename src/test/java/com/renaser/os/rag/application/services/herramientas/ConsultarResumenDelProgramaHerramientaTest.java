package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.rag.application.ports.out.programa.ConsultarPanoramaDelProgramaPort;
import com.renaser.os.rag.application.ports.out.programa.ConsultarPanoramaDelProgramaPort.Panorama;
import com.renaser.os.rag.application.ports.out.programa.ConsultarPanoramaDelProgramaPort.ProximoEvento;
import com.renaser.os.rag.application.ports.out.programa.ConsultarPanoramaDelProgramaPort.RachaYPuntos;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La herramienta "donde estoy" del acompanante. Lo que importa probar es que las fechas que ve la
 * persona son las de SU zona: el reloj de los casos principales esta a las 03:30 UTC, que en Lima
 * todavia es el dia anterior (regla 03, el fixture que escondio E-91).
 */
class ConsultarResumenDelProgramaHerramientaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    /** 2026-09-23 03:30 UTC = martes 2026-09-22 22:30 en Lima. */
    private static final Instant MADRUGADA_UTC = Instant.parse("2026-09-23T03:30:00Z");
    /** 2026-09-23 14:00 UTC = miercoles 2026-09-23 09:00 en Lima. */
    private static final Instant INICIO_DEL_EVENTO = Instant.parse("2026-09-23T14:00:00Z");

    private static final ConsultarSituacionDelAprendizPort EN_DIA_12 =
            participanteId -> Optional.of(new SituacionDelAprendiz(12, 2));

    private static ConsultarResumenDelProgramaHerramienta herramienta(ConsultarSituacionDelAprendizPort situacion,
                                                                     Panorama panorama, Instant ahora) {
        ConsultarPanoramaDelProgramaPort panoramaPort = (participanteId, instante) -> Optional.ofNullable(panorama);
        return new ConsultarResumenDelProgramaHerramienta(situacion, panoramaPort, FixedClock.at(ahora));
    }

    private static String contenidoDe(ResultadoHerramienta resultado) {
        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
        return ((ResultadoHerramienta.Exito) resultado).contenido();
    }

    private static ResultadoHerramienta ejecutar(ConsultarResumenDelProgramaHerramienta herramienta) {
        return herramienta.ejecutar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos(ConsultarResumenDelProgramaHerramienta.NOMBRE));
    }

    @Test
    @DisplayName("se ofrece con su nombre estable y sin parametros")
    void definicion() {
        var definicion = herramienta(EN_DIA_12, null, MADRUGADA_UTC).definicion();

        assertThat(definicion.nombre()).isEqualTo("consultar_resumen_del_programa");
        assertThat(definicion.parametros()).isEmpty();
    }

    @Test
    @DisplayName("dice el dia de 90 y la fase")
    void diaYFase() {
        var panorama = new Panorama(LIMA, Optional.empty(), Optional.empty(), Optional.empty());

        String contenido = contenidoDe(ejecutar(herramienta(EN_DIA_12, panorama, MADRUGADA_UTC)));

        assertThat(contenido).contains("Dia del programa: 12 de 90 (fase 2 de 4).");
    }

    @Test
    @DisplayName("a las 03:30 UTC la fecha y la hora son las de Lima: martes 22 a las 22:30, no miercoles 23")
    void laFechaEsLaDeSuZona() {
        var panorama = new Panorama(LIMA, Optional.empty(), Optional.empty(), Optional.empty());

        String contenido = contenidoDe(ejecutar(herramienta(EN_DIA_12, panorama, MADRUGADA_UTC)));

        assertThat(contenido).contains("martes 2026-09-22 22:30").contains("America/Lima")
                .doesNotContain("2026-09-23");
    }

    @Test
    @DisplayName("un evento del dia siguiente en Lima es 'manana' aunque en UTC sea el mismo dia")
    void elEventoSeCuentaEnFechasLocales() {
        var panorama = new Panorama(LIMA, Optional.empty(),
                Optional.of(new ProximoEvento("Sesion en vivo", INICIO_DEL_EVENTO)), Optional.empty());

        String contenido = contenidoDe(ejecutar(herramienta(EN_DIA_12, panorama, MADRUGADA_UTC)));

        assertThat(contenido).contains("Proximo evento: Sesion en vivo, manana (")
                .contains("2026-09-23 09:00, hora local");
    }

    @Test
    @DisplayName("con el reloj a media manana en Lima, el mismo evento es 'hoy'")
    void elEventoDeHoy() {
        var panorama = new Panorama(LIMA, Optional.empty(),
                Optional.of(new ProximoEvento("Sesion en vivo", INICIO_DEL_EVENTO)), Optional.empty());

        String contenido = contenidoDe(ejecutar(
                herramienta(EN_DIA_12, panorama, Instant.parse("2026-09-23T12:00:00Z"))));

        assertThat(contenido).contains("Proximo evento: Sesion en vivo, hoy (");
    }

    @Test
    @DisplayName("la coherencia se muestra con su decimal, y sin dato se dice que no hay, nunca un cero")
    void coherencia() {
        var conDato = new Panorama(LIMA, Optional.of(new BigDecimal("85.5")), Optional.empty(), Optional.empty());
        var sinDato = new Panorama(LIMA, Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(contenidoDe(ejecutar(herramienta(EN_DIA_12, conDato, MADRUGADA_UTC))))
                .contains("Coherencia de los ultimos 7 dias: 85.5%");
        assertThat(contenidoDe(ejecutar(herramienta(EN_DIA_12, sinDato, MADRUGADA_UTC))))
                .contains("no hay dato").doesNotContain("0%");
    }

    @Test
    @DisplayName("sin eventos, lo dice")
    void sinEvento() {
        var panorama = new Panorama(LIMA, Optional.empty(), Optional.empty(), Optional.empty());

        String contenido = contenidoDe(ejecutar(herramienta(EN_DIA_12, panorama, MADRUGADA_UTC)));

        assertThat(contenido).contains("no tiene eventos proximos");
    }

    @Test
    @DisplayName("a las 03:30 UTC muestra la racha actual con su record y los puntos de liga, sin decir que no estan")
    void rachaYPuntos() {
        var panorama = new Panorama(LIMA, Optional.empty(), Optional.empty(),
                Optional.of(new RachaYPuntos(3, 7, 150)));

        String contenido = contenidoDe(ejecutar(herramienta(EN_DIA_12, panorama, MADRUGADA_UTC)));

        assertThat(contenido).contains("Racha actual: 3 dias (record: 7).")
                .contains("Puntos de liga: 150 puntos")
                .doesNotContain("no disponibles");
    }

    @Test
    @DisplayName("una racha de un dia se dice en singular, y una en cero se muestra como cero, no como falta de dato")
    void rachaDeUnDiaYEnCero() {
        var deUnDia = new Panorama(LIMA, Optional.empty(), Optional.empty(), Optional.of(new RachaYPuntos(1, 1, 100)));
        var enCero = new Panorama(LIMA, Optional.empty(), Optional.empty(), Optional.of(new RachaYPuntos(0, 5, 90)));

        assertThat(contenidoDe(ejecutar(herramienta(EN_DIA_12, deUnDia, MADRUGADA_UTC))))
                .contains("Racha actual: 1 dia (record: 1).");
        assertThat(contenidoDe(ejecutar(herramienta(EN_DIA_12, enCero, MADRUGADA_UTC))))
                .contains("Racha actual: 0 dias (record: 5).").contains("Puntos de liga: 90 puntos");
    }

    @Test
    @DisplayName("si la racha y los puntos no se pudieron leer, lo dice en vez de mostrar un cero")
    void sinRachaNiPuntos() {
        var panorama = new Panorama(LIMA, Optional.empty(), Optional.empty(), Optional.empty());

        String contenido = contenidoDe(ejecutar(herramienta(EN_DIA_12, panorama, MADRUGADA_UTC)));

        assertThat(contenido).contains("Racha y puntos de liga: no pude leerlos")
                .doesNotContain("Racha actual:").doesNotContain("Puntos de liga: ");
    }

    @Test
    @DisplayName("un programa que todavia no arranco no es 'dia 0 de 90'")
    void programaSinEmpezar() {
        var panorama = new Panorama(LIMA, Optional.empty(), Optional.empty(), Optional.empty());
        ConsultarSituacionDelAprendizPort enDiaCero = participanteId -> Optional.of(new SituacionDelAprendiz(0, 1));

        String contenido = contenidoDe(ejecutar(herramienta(enDiaCero, panorama, MADRUGADA_UTC)));

        assertThat(contenido).contains("todavia no empezo").doesNotContain("0 de 90");
    }

    @Test
    @DisplayName("quien no esta inscrito recibe una respuesta, no un dia inventado")
    void noInscrito() {
        ConsultarSituacionDelAprendizPort sinInscripcion = participanteId -> Optional.empty();

        String contenido = contenidoDe(ejecutar(herramienta(sinInscripcion, null, MADRUGADA_UTC)));

        assertThat(contenido).contains("No esta inscrito").doesNotContain("de 90 (");
    }

    @Test
    @DisplayName("si el panorama no se puede leer, falla con un motivo apto para mostrar")
    void sinPanorama() {
        var resultado = ejecutar(herramienta(EN_DIA_12, null, MADRUGADA_UTC));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
    }
}
