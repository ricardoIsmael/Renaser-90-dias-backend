package com.renaser.os.onboarding.domain.model.caja;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La caja de un aprendiz (D-219): qué estado se deriva, qué se puede hacer desde cada uno y el requisito de
 * la Fase 1. Sin Spring ni base: {@code CajaRenaser.de(...)} a secas.
 *
 * <p>Ana es de Lima (UTC−5) y su Día 1 fue el jueves 1 de octubre de 2026. Su Día 8 empieza el 8 a las 05:00
 * UTC; a las 03:00 UTC del 8 todavía es el 7 en Lima (regla 02: el fixture a las 10:00 UTC escondería eso).
 */
class CajaRenaserTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate DIA_1 = LocalDate.of(2026, 10, 1);
    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-00000000a0a0"));
    private static final UserId KELIN = UserId.of(UUID.fromString("00000000-0000-0000-0000-00000000ad01"));
    /** Día 10 en Lima (10 de octubre, 12:00 local). */
    private static final Instant DIA_10 = Instant.parse("2026-10-10T17:00:00Z");

    @Nested
    @DisplayName("el requisito de la Fase 1 y el Día 8")
    class Requisito {

        @Test
        @DisplayName("a las 03:00 UTC del 8, en Lima sigue siendo el Día 7: la caja todavía no aplica")
        void elDiaOchoEsElDeLima() {
            Instant madrugadaUtc = Instant.parse("2026-10-08T03:00:00Z");

            CajaRenaser caja = caja(situacion(dias(100)), List.of(), madrugadaUtc);

            assertThat(caja.diaDelPrograma()).isEqualTo(7);
            assertThat(caja.estado()).isEqualTo(EstadoCaja.NO_APLICA);
            assertThat(caja.cumplimientoFaseUno()).as("la Fase 1 no cerró: no hay número").isEmpty();
        }

        @Test
        @DisplayName("a las 05:00 UTC del 8 empieza su Día 8: con 80 % o más, pasa sola a en revisión")
        void conOchentaPasaSola() {
            CajaRenaser caja = caja(situacion(dias(80)), List.of(), Instant.parse("2026-10-08T05:00:00Z"));

            assertThat(caja.diaDelPrograma()).isEqualTo(8);
            assertThat(caja.cumplimientoFaseUno()).hasValueSatisfying(p -> assertThat(p).isEqualByComparingTo("80.0"));
            assertThat(caja.estado()).isEqualTo(EstadoCaja.POR_REVISAR);
        }

        @Test
        @DisplayName("con 79,9 % queda en evaluación, y el Admin la aprueba caso por caso")
        void debajoDelUmbralQuedaEnEvaluacion() {
            // Seis días al 80 % y uno al 79 %: (6 × 80 + 79) / 7 = 79,86 → 79,9, por debajo del umbral.
            CajaRenaser caja = caja(situacion(List.of(
                    dia(0, 10, 8), dia(1, 10, 8), dia(2, 10, 8), dia(3, 10, 8), dia(4, 10, 8), dia(5, 10, 8),
                    dia(6, 100, 79))), List.of(), DIA_10);

            assertThat(caja.cumplimientoFaseUno()).hasValueSatisfying(p -> assertThat(p).isEqualByComparingTo("79.9"));
            assertThat(caja.estado()).isEqualTo(EstadoCaja.EN_EVALUACION);

            PasoDeCaja aprobada = AccionDeCaja.APROBAR.paso(caja, KELIN, Map.of());
            assertThat(caja(situacion(dias(0)), List.of(aprobada), DIA_10).estado()).isEqualTo(EstadoCaja.POR_REVISAR);
        }

        @Test
        @DisplayName("los días fuera del 1 al 7 no cuentan, y sin nada programado no hay número (ni 0 ni 100)")
        void soloLosDiasUnoASiete() {
            List<DiaDeHabitos> conDiaOcho = new ArrayList<>(dias(100));
            conDiaOcho.add(dia(7, 10, 0));

            assertThat(caja(situacion(conDiaOcho), List.of(), DIA_10).estado()).isEqualTo(EstadoCaja.POR_REVISAR);
            CajaRenaser sinHabitos = caja(situacion(List.of()), List.of(), DIA_10);
            assertThat(sinHabitos.cumplimientoFaseUno()).isEmpty();
            assertThat(sinHabitos.estado()).isEqualTo(EstadoCaja.EN_EVALUACION);
        }
    }

    @Nested
    @DisplayName("los estados derivados")
    class Derivados {

        @Test
        @DisplayName("cuenta suspendida → en pausa; país distinto de Perú → fuera de la app; staff → no aplica")
        void pausaYExtranjero() {
            assertThat(caja(new SituacionDelAprendiz(true, true, true, LIMA, DIA_1, dias(100)), List.of(), DIA_10)
                    .estado()).isEqualTo(EstadoCaja.EN_PAUSA);
            assertThat(caja(new SituacionDelAprendiz(true, false, false, LIMA, DIA_1, dias(100)), List.of(), DIA_10)
                    .estado()).isEqualTo(EstadoCaja.FUERA_DE_LA_APP);
            assertThat(caja(SituacionDelAprendiz.noAplica(false, true), List.of(), DIA_10).estado())
                    .isEqualTo(EstadoCaja.NO_APLICA);
        }

        @Test
        @DisplayName("entregada es entregada aunque después se suspenda la cuenta")
        void entregadaGanaALaPausa() {
            PasoDeCaja entregada = new PasoDeCaja(ANA, 1, TipoPasoCaja.ENTREGADA, DIA_10, KELIN,
                    Map.of(PasoDeCaja.PREVIA, "true"));

            assertThat(caja(new SituacionDelAprendiz(true, true, true, LIMA, DIA_1, dias(0)), List.of(entregada),
                    DIA_10).estado()).isEqualTo(EstadoCaja.ENTREGADA);
        }

        @Test
        @DisplayName("una caja armando con la cuenta suspendida queda en pausa, y al reactivarla vuelve a armando")
        void armandoEnPausa() {
            PasoDeCaja armando = new PasoDeCaja(ANA, 1, TipoPasoCaja.ARMANDO, DIA_10, KELIN, null);

            assertThat(caja(new SituacionDelAprendiz(true, true, true, LIMA, DIA_1, dias(100)), List.of(armando),
                    DIA_10).estado()).isEqualTo(EstadoCaja.EN_PAUSA);
            assertThat(caja(situacion(dias(100)), List.of(armando), DIA_10).estado()).isEqualTo(EstadoCaja.ARMANDO);
        }
    }

    @Nested
    @DisplayName("las transiciones")
    class Transiciones {

        @Test
        @DisplayName("en revisión → armando → enviada → entregada, y cada paso lo firma quien lo hizo")
        void elCaminoFeliz() {
            List<PasoDeCaja> pasos = new ArrayList<>();
            CajaRenaser caja = caja(situacion(dias(100)), pasos, DIA_10);

            pasos.add(AccionDeCaja.ARMAR.paso(caja, KELIN, Map.of()));
            caja = caja(situacion(dias(100)), pasos, DIA_10.plusSeconds(60));
            assertThat(caja.estado()).isEqualTo(EstadoCaja.ARMANDO);
            assertThat(caja.faltaParaEnviar(false)).containsExactly(FaltaParaEnviar.CONTENIDO, FaltaParaEnviar.FOTO,
                    FaltaParaEnviar.COMPROBANTE);

            pasos.add(AccionDeCaja.FIJAR_FOTO.paso(caja, KELIN, Map.of(PasoDeCaja.RUTA, "onboarding/x/caja/1")));
            pasos.add(AccionDeCaja.FIJAR_COMPROBANTE.paso(caja, KELIN, Map.of(PasoDeCaja.RUTA, "onboarding/x/caja/2")));
            caja = caja(situacion(dias(100)), pasos, DIA_10.plusSeconds(120));
            assertThat(caja.faltaParaEnviar(true)).isEmpty();

            pasos.add(AccionDeCaja.ENVIAR.paso(caja, KELIN, DatosDelEnvio.de("Olva", null, "A1", null).comoDetalle()));
            caja = caja(situacion(dias(100)), pasos, DIA_10.plusSeconds(180));
            assertThat(caja.estado()).isEqualTo(EstadoCaja.ENVIADA);

            PasoDeCaja recibida = AccionDeCaja.CONFIRMAR_RECIBIDA.paso(caja, ANA, Map.of());
            assertThat(recibida.marcadaPor()).isEqualTo(ANA);
            assertThat(recibida.flujo()).isEqualTo("caja:1:ENTREGADA");
            pasos.add(recibida);
            assertThat(caja(situacion(dias(100)), pasos, DIA_10.plusSeconds(240)).estado())
                    .isEqualTo(EstadoCaja.ENTREGADA);
        }

        @Test
        @DisplayName("no se arma lo que está en evaluación, ni se envía lo que no se está armando: 409 con el motivo")
        void fueraDeOrden() {
            CajaRenaser enEvaluacion = caja(situacion(dias(0)), List.of(), DIA_10);

            assertThatThrownBy(() -> AccionDeCaja.ARMAR.paso(enEvaluacion, KELIN, Map.of()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("en evaluación");
            assertThatThrownBy(() -> AccionDeCaja.ENVIAR.paso(enEvaluacion, KELIN, Map.of()))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> AccionDeCaja.CONFIRMAR_RECIBIDA.paso(enEvaluacion, ANA, Map.of()))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(AccionDeCaja.CAMBIAR_DESTINO.sePuedeDesde(EstadoCaja.ENVIADA)).isFalse();
            assertThat(AccionDeCaja.CAMBIAR_DESTINO.sePuedeDesde(EstadoCaja.ARMANDO)).isTrue();
        }

        @Test
        @DisplayName("un problema después de enviada y el reenvío: vuelve a armando con el envío 2, sin las fotos del 1")
        void reenvio() {
            List<PasoDeCaja> pasos = new ArrayList<>(List.of(
                    new PasoDeCaja(ANA, 1, TipoPasoCaja.ARMANDO, DIA_10, KELIN, null),
                    new PasoDeCaja(ANA, 1, TipoPasoCaja.FOTO, DIA_10, KELIN, Map.of(PasoDeCaja.RUTA, "r")),
                    new PasoDeCaja(ANA, 1, TipoPasoCaja.COMPROBANTE, DIA_10, KELIN, Map.of(PasoDeCaja.RUTA, "c")),
                    new PasoDeCaja(ANA, 1, TipoPasoCaja.ENVIADA, DIA_10, KELIN, Map.of(PasoDeCaja.CODIGO, "A1"))));
            CajaRenaser enviada = caja(situacion(dias(100)), pasos, DIA_10.plusSeconds(60));
            assertThatThrownBy(() -> AccionDeCaja.REENVIAR.paso(enviada, KELIN, Map.of()))
                    .isInstanceOf(IllegalStateException.class);

            pasos.add(AccionDeCaja.REPORTAR_PROBLEMA.paso(enviada, KELIN, Map.of(PasoDeCaja.MOTIVO, "PERDIDA")));
            CajaRenaser conProblema = caja(situacion(dias(100)), pasos, DIA_10.plusSeconds(120));
            assertThat(conProblema.estado()).isEqualTo(EstadoCaja.CON_PROBLEMA);

            PasoDeCaja reenvio = AccionDeCaja.REENVIAR.paso(conProblema, KELIN, Map.of());
            assertThat(reenvio.flujo()).isEqualTo("caja:2:ARMANDO");
            pasos.add(reenvio);
            CajaRenaser segunda = caja(situacion(dias(100)), pasos, DIA_10.plusSeconds(180));
            assertThat(segunda.estado()).isEqualTo(EstadoCaja.ARMANDO);
            assertThat(segunda.envioActual()).isEqualTo(2);
            assertThat(segunda.faltaParaEnviar(true)).containsExactly(FaltaParaEnviar.FOTO, FaltaParaEnviar.COMPROBANTE);
            assertThat(segunda.pasos()).as("el historial del envío 1 sigue a la vista").hasSize(6);
        }

        @Test
        @DisplayName("«Ya se envió antes» sirve para el padrón de antes de la app, pero no para una caja en camino")
        void entregaPrevia() {
            assertThat(AccionDeCaja.ENTREGAR_PREVIA.paso(caja(situacion(dias(0)), List.of(), DIA_10), KELIN,
                    Map.of(PasoDeCaja.PREVIA, "true")).dato(PasoDeCaja.PREVIA)).hasValue("true");
            assertThat(AccionDeCaja.ENTREGAR_PREVIA.sePuedeDesde(EstadoCaja.ENVIADA)).isFalse();
            assertThat(AccionDeCaja.ENTREGAR_PREVIA.sePuedeDesde(EstadoCaja.ENTREGADA)).isFalse();
        }
    }

    @Nested
    @DisplayName("los avisos del barrido")
    class Avisos {

        @Test
        @DisplayName("en revisión se avisa una vez; enviada, a los 3 días al aprendiz y a los 5 al Admin")
        void plazos() {
            CajaRenaser enRevision = caja(situacion(dias(100)), List.of(), DIA_10);
            assertThat(AvisosDeCaja.debidos(enRevision)).containsExactly(TipoPasoCaja.AVISO_EN_REVISION);
            PasoDeCaja marca = AvisosDeCaja.marca(enRevision, TipoPasoCaja.AVISO_EN_REVISION);
            assertThat(AvisosDeCaja.debidos(caja(situacion(dias(100)), List.of(marca), DIA_10))).isEmpty();

            PasoDeCaja enviada = new PasoDeCaja(ANA, 1, TipoPasoCaja.ENVIADA, DIA_10, KELIN, null);
            assertThat(AvisosDeCaja.debidos(caja(situacion(dias(100)), List.of(enviada),
                    DIA_10.plus(AvisosDeCaja.RECORDATORIO_AL_APRENDIZ).minusSeconds(1)))).isEmpty();
            assertThat(AvisosDeCaja.debidos(caja(situacion(dias(100)), List.of(enviada),
                    DIA_10.plus(AvisosDeCaja.RECORDATORIO_AL_APRENDIZ)))).containsExactly(TipoPasoCaja.AVISO_RECORDATORIO);
            assertThat(AvisosDeCaja.debidos(caja(situacion(dias(100)), List.of(enviada),
                    DIA_10.plus(AvisosDeCaja.AVISO_AL_ADMIN))))
                    .containsExactly(TipoPasoCaja.AVISO_RECORDATORIO, TipoPasoCaja.AVISO_SIN_CONFIRMAR);
        }
    }

    private static CajaRenaser caja(SituacionDelAprendiz situacion, List<PasoDeCaja> pasos, Instant ahora) {
        return CajaRenaser.de(ANA, situacion, pasos, ahora);
    }

    private static SituacionDelAprendiz situacion(List<DiaDeHabitos> habitos) {
        return new SituacionDelAprendiz(true, false, true, LIMA, DIA_1, habitos);
    }

    /** Siete días con 10 hábitos y {@code porcentaje} % cumplidos cada uno. */
    private static List<DiaDeHabitos> dias(int porcentaje) {
        List<DiaDeHabitos> dias = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            dias.add(dia(i, 10, porcentaje / 10));
        }
        return dias;
    }

    private static DiaDeHabitos dia(int desdeElUno, int programados, int cumplidos) {
        return new DiaDeHabitos(DIA_1.plusDays(desdeElUno), programados, cumplidos);
    }
}
