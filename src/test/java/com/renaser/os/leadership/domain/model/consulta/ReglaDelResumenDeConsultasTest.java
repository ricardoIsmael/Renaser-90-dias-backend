package com.renaser.os.leadership.domain.model.consulta;

import com.renaser.os.leadership.domain.model.consulta.ReglaDelResumenDeConsultas.DiaDelMentor;
import com.renaser.os.leadership.domain.model.consulta.ReglaDelResumenDeConsultas.Resumen;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cuándo y con qué sale el resumen diario al mentor («Tu líder revisó a tu grupo», spec 005, FR-017/FR-018): a las 20:00
 * de SU zona, una vez al día, solo con lo que miró el líder de mentores. Todo con el reloj pasado como argumento y casos
 * en la madrugada UTC, que es donde los días de Lima y de Madrid se separan del día UTC (regla 02).
 */
class ReglaDelResumenDeConsultasTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final UserId MENTOR = id("a1");
    private static final UserId LIDER = id("c1");
    private static final UserId ADMIN = id("d1");
    private static final UserId ALQUIMISTA = id("d2");
    private static final UserId ANA = id("b1");
    private static final UserId LUIS = id("b2");
    private static final UserId PIA = id("b3");
    private static final UUID FENIX = UUID.fromString("00000000-0000-0000-0000-00000000f001");
    private static final UUID AURORA = UUID.fromString("00000000-0000-0000-0000-00000000f002");
    private static final LocalDate DOMINGO = LocalDate.of(2026, 10, 18);
    private static final DiaDelMentor EL_DOMINGO_DE_LUISA = new DiaDelMentor(MENTOR, LIMA, DOMINGO);

    private static UserId id(String sufijo) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufijo));
    }

    /** Domingo 18 a las {@code hora}:{@code minuto} de Lima, que es el lunes 19 en UTC desde las 19:00. */
    private static Instant enLima(int hora, int minuto) {
        return DOMINGO.atTime(hora, minuto).atZone(LIMA).toInstant();
    }

    private static ConsultaDeFicha consulta(UserId consultor, UserRole rol, UserId persona, UUID grupo, Instant cuando) {
        return new ConsultaDeFicha(UUID.randomUUID(), consultor, rol, persona, grupo, MENTOR, LocalDate.of(2026, 10, 10),
                cuando);
    }

    private static ConsultaDeFicha delLider(UserId persona, UUID grupo, int hora) {
        return consulta(LIDER, UserRole.MENTOR_LEAD, persona, grupo, enLima(hora, 5));
    }

    // ---- cuando toca

    @Test
    @DisplayName("toca entre las 20:00 y las 20:59 de la zona del mentor, en Lima")
    void tocaEnLima() {
        assertThat(ReglaDelResumenDeConsultas.toca(enLima(19, 59), LIMA)).isFalse();
        assertThat(ReglaDelResumenDeConsultas.toca(enLima(20, 0), LIMA)).isTrue();
        assertThat(ReglaDelResumenDeConsultas.toca(enLima(20, 59), LIMA)).isTrue();
        assertThat(ReglaDelResumenDeConsultas.toca(enLima(21, 0), LIMA)).isFalse();
    }

    @Test
    @DisplayName("toca a las 20:00 de Madrid, que son las 18:00 UTC y la 13:00 de Lima")
    void tocaEnMadrid() {
        Instant lasVeinteEnMadrid = Instant.parse("2026-10-18T18:10:00Z");

        assertThat(ReglaDelResumenDeConsultas.toca(lasVeinteEnMadrid, MADRID)).isTrue();
        assertThat(ReglaDelResumenDeConsultas.toca(lasVeinteEnMadrid, LIMA)).isFalse();
    }

    @Test
    @DisplayName("nunca toca en la madrugada UTC por el solo hecho de ser las 20 UTC: 20:00 UTC es la 1 de la manana en Madrid")
    void nuncaPorLaHoraUtc() {
        Instant lasVeinteUtc = Instant.parse("2026-10-18T20:10:00Z");
        Instant madrugadaUtc = Instant.parse("2026-10-19T01:30:00Z");

        assertThat(ReglaDelResumenDeConsultas.toca(lasVeinteUtc, LIMA)).isFalse();
        assertThat(ReglaDelResumenDeConsultas.toca(lasVeinteUtc, MADRID)).isFalse();
        // 01:30 UTC del lunes 19 son las 20:30 del domingo 18 en Lima: sí toca, y el dia del resumen es el domingo.
        assertThat(ReglaDelResumenDeConsultas.toca(madrugadaUtc, LIMA)).isTrue();
        assertThat(ReglaDelResumenDeConsultas.diaLocal(madrugadaUtc, LIMA)).isEqualTo(DOMINGO);
    }

    @Test
    @DisplayName("el dia local de una consulta es el de la zona del mentor, no el de UTC")
    void ventanaDelDia() {
        assertThat(ReglaDelResumenDeConsultas.inicioDelDia(DOMINGO, LIMA)).isEqualTo(Instant.parse("2026-10-18T05:00:00Z"));
        assertThat(ReglaDelResumenDeConsultas.inicioDelDia(DOMINGO.plusDays(1), LIMA))
                .isEqualTo(Instant.parse("2026-10-19T05:00:00Z"));
        assertThat(ReglaDelResumenDeConsultas.inicioDelDia(DOMINGO, MADRID)).isEqualTo(Instant.parse("2026-10-17T22:00:00Z"));
    }

    // ---- a quien y que cuenta

    @Test
    @DisplayName("junta a las personas que miro el lider ese dia, sin repetir a nadie, en el orden en que las miro")
    void sinRepetidos() {
        List<ConsultaDeFicha> delDia = List.of(delLider(LUIS, FENIX, 15), delLider(ANA, FENIX, 10),
                delLider(LUIS, FENIX, 18), delLider(ANA, FENIX, 19));

        Optional<Resumen> resumen = ReglaDelResumenDeConsultas.resumir(EL_DOMINGO_DE_LUISA, delDia);

        assertThat(resumen).get().satisfies(r -> assertThat(r.personas()).containsExactly(ANA, LUIS));
    }

    @Test
    @DisplayName("solo cuenta al lider de mentores: Administracion y Alquimista abren la ficha sin avisarle al mentor")
    void soloElLider() {
        List<ConsultaDeFicha> delDia = List.of(consulta(ADMIN, UserRole.ADMIN, ANA, FENIX, enLima(10, 0)),
                consulta(ALQUIMISTA, UserRole.ALCHEMIST, LUIS, FENIX, enLima(11, 0)));

        List<ConsultaDeFicha> conUnaDelLider = List.of(delDia.get(0), delLider(PIA, FENIX, 12), delDia.get(1));

        assertThat(ReglaDelResumenDeConsultas.resumir(EL_DOMINGO_DE_LUISA, delDia)).isEmpty();
        assertThat(ReglaDelResumenDeConsultas.resumir(EL_DOMINGO_DE_LUISA, conUnaDelLider))
                .get().satisfies(r -> assertThat(r.personas()).containsExactly(PIA));
    }

    @Test
    @DisplayName("no cuenta al propio mentor: ni cuando el consultor es el mentor ni cuando la persona es el mentor")
    void sinElPropioMentor() {
        List<ConsultaDeFicha> delDia = List.of(
                consulta(MENTOR, UserRole.MENTOR_LEAD, ANA, FENIX, enLima(10, 0)),
                consulta(LIDER, UserRole.MENTOR_LEAD, MENTOR, FENIX, enLima(11, 0)));

        assertThat(ReglaDelResumenDeConsultas.resumir(EL_DOMINGO_DE_LUISA, delDia)).isEmpty();
    }

    @Test
    @DisplayName("no cuenta lo que se registro para otro mentor ni lo de otro dia local, aunque caiga en el mismo dia UTC")
    void soloSuMentorYSuDia() {
        ConsultaDeOtroMentor otro = new ConsultaDeOtroMentor();
        ConsultaDeFicha ayerNocheEnLima = consulta(LIDER, UserRole.MENTOR_LEAD, ANA, FENIX,
                Instant.parse("2026-10-18T04:59:00Z"));
        ConsultaDeFicha mananaEnLimaYaEsOtroDia = consulta(LIDER, UserRole.MENTOR_LEAD, LUIS, FENIX,
                Instant.parse("2026-10-19T05:00:00Z"));

        assertThat(ReglaDelResumenDeConsultas.resumir(EL_DOMINGO_DE_LUISA,
                List.of(otro.consulta(), ayerNocheEnLima, mananaEnLimaYaEsOtroDia))).isEmpty();
    }

    @Test
    @DisplayName("la consulta de las 23:59 de Lima, que ya es lunes en UTC, entra al resumen del domingo")
    void madrugadaUtc() {
        ConsultaDeFicha casiMedianoche = consulta(LIDER, UserRole.MENTOR_LEAD, ANA, FENIX,
                Instant.parse("2026-10-19T04:59:00Z"));

        assertThat(ReglaDelResumenDeConsultas.resumir(EL_DOMINGO_DE_LUISA, List.of(casiMedianoche)))
                .get().satisfies(r -> assertThat(r.personas()).containsExactly(ANA));
    }

    @Test
    @DisplayName("la ruta es la del grupo con mas personas miradas; en empate, el de la primera consulta")
    void rutaDelGrupoConMasPersonas() {
        List<ConsultaDeFicha> delDia = List.of(delLider(ANA, FENIX, 9), delLider(PIA, AURORA, 10),
                delLider(LUIS, AURORA, 11), delLider(ANA, FENIX, 12));
        List<ConsultaDeFicha> empate = List.of(delLider(ANA, FENIX, 9), delLider(PIA, AURORA, 10));

        assertThat(ReglaDelResumenDeConsultas.resumir(EL_DOMINGO_DE_LUISA, delDia)).get()
                .satisfies(r -> assertThat(r.grupoDeLaRuta()).isEqualTo(AURORA));
        assertThat(ReglaDelResumenDeConsultas.resumir(EL_DOMINGO_DE_LUISA, empate)).get()
                .satisfies(r -> assertThat(r.grupoDeLaRuta()).isEqualTo(FENIX));
    }

    @Test
    @DisplayName("el dia del mentor trae su ventana de lectura, medianoche a medianoche local, y la clave de ese dia")
    void diaDelMentor() {
        DiaDelMentor deMadrugada = DiaDelMentor.de(MENTOR, LIMA, Instant.parse("2026-10-19T01:30:00Z"));

        assertThat(deMadrugada.dia()).isEqualTo(DOMINGO);
        assertThat(deMadrugada.desde()).isEqualTo(Instant.parse("2026-10-18T05:00:00Z"));
        assertThat(deMadrugada.hasta()).isEqualTo(Instant.parse("2026-10-19T05:00:00Z"));
        assertThat(deMadrugada.clave()).isEqualTo(ReglaDelResumenDeConsultas.clave(MENTOR, DOMINGO));
    }

    @Test
    @DisplayName("sin consultas del lider no hay resumen")
    void sinConsultas() {
        assertThat(ReglaDelResumenDeConsultas.resumir(EL_DOMINGO_DE_LUISA, List.of())).isEmpty();
    }

    /** Una consulta del líder, de ese día, pero registrada para OTRO mentor. */
    private static final class ConsultaDeOtroMentor {
        ConsultaDeFicha consulta() {
            return new ConsultaDeFicha(UUID.randomUUID(), LIDER, UserRole.MENTOR_LEAD, ANA, AURORA, id("a2"),
                    LocalDate.of(2026, 10, 10), enLima(10, 0));
        }
    }

    // ---- clave y texto

    @Test
    @DisplayName("la clave de deduplicacion es una por mentor y dia local: la misma corrida repetida no avisa dos veces")
    void clave() {
        UUID domingo = ReglaDelResumenDeConsultas.clave(MENTOR, DOMINGO);

        assertThat(ReglaDelResumenDeConsultas.clave(MENTOR, DOMINGO)).isEqualTo(domingo);
        assertThat(ReglaDelResumenDeConsultas.clave(MENTOR, DOMINGO.plusDays(1))).isNotEqualTo(domingo);
        assertThat(ReglaDelResumenDeConsultas.clave(id("a2"), DOMINGO)).isNotEqualTo(domingo);
        assertThat(domingo).isEqualTo(UUID.nameUUIDFromBytes(("consultas-lider:" + MENTOR + ":2026-10-18").getBytes(
                StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("el cuerpo nombra hasta tres personas y despues cuenta las que faltan")
    void cuerpo() {
        assertThat(ReglaDelResumenDeConsultas.cuerpo(List.of("Ana"))).isEqualTo("Hoy revisó a Ana");
        assertThat(ReglaDelResumenDeConsultas.cuerpo(List.of("Ana", "Luis"))).isEqualTo("Hoy revisó a Ana y Luis");
        assertThat(ReglaDelResumenDeConsultas.cuerpo(List.of("Ana", "Luis", "Pía"))).isEqualTo("Hoy revisó a Ana, Luis y Pía");
        assertThat(ReglaDelResumenDeConsultas.cuerpo(List.of("Ana", "Luis", "Pía", "Raúl")))
                .isEqualTo("Hoy revisó a Ana, Luis, Pía y 1 más");
        assertThat(ReglaDelResumenDeConsultas.cuerpo(List.of("Ana", "Luis", "Pía", "Raúl", "Eva")))
                .isEqualTo("Hoy revisó a Ana, Luis, Pía y 2 más");
        assertThatThrownBy(() -> ReglaDelResumenDeConsultas.cuerpo(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("del nombre completo solo va el primero: el aviso sale por push y no lleva apellidos")
    void primerNombre() {
        assertThat(ReglaDelResumenDeConsultas.primerNombre("Ana María Pérez")).isEqualTo("Ana");
        assertThat(ReglaDelResumenDeConsultas.primerNombre("  Luis  ")).isEqualTo("Luis");
        assertThat(ReglaDelResumenDeConsultas.primerNombre(null)).isEqualTo("Una persona");
        assertThat(ReglaDelResumenDeConsultas.primerNombre("   ")).isEqualTo("Una persona");
    }
}
