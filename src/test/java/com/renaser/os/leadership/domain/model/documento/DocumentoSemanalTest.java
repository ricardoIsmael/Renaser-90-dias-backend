package com.renaser.os.leadership.domain.model.documento;

import com.renaser.os.leadership.domain.model.documento.DocumentoSemanal.Constancia;
import com.renaser.os.leadership.domain.model.documento.DocumentoSemanal.Contenido;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El documento semanal sin Spring ni base: su ruta determinista y la huella de los datos (spec 056, FR-039). */
class DocumentoSemanalTest {

    private static final UserId ANA = UserId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final UserId LIDER = UserId.of(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    /** 03:00 UTC del 18 de noviembre: en Lima todavía es el 17 (regla 02). */
    private static final Instant AHORA = Instant.parse("2026-11-18T03:00:00Z");
    private static final Clock RELOJ = new Clock() {
        @Override
        public Instant now() {
            return AHORA;
        }

        @Override
        public LocalDate today() {
            return LocalDate.of(2026, 11, 18);
        }
    };

    @Test
    @DisplayName("spec 056 FR-039: la huella es el SHA-256 en hexadecimal minúscula de los datos")
    void laHuellaEsUnSha256() {
        String huella = DocumentoSemanal.huellaDe(DatosDePruebaDelDocumento.aprendiz());

        assertThat(huella).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("spec 056 FR-039: los mismos datos dan la misma huella, también armados de nuevo")
    void losMismosDatosDanLaMismaHuella() {
        assertThat(DocumentoSemanal.huellaDe(DatosDePruebaDelDocumento.aprendiz()))
                .isEqualTo(DocumentoSemanal.huellaDe(DatosDePruebaDelDocumento.aprendiz()));
        assertThat(DocumentoSemanal.huellaDe(DatosDePruebaDelDocumento.mentor()))
                .isEqualTo(DocumentoSemanal.huellaDe(DatosDePruebaDelDocumento.mentor()));
        assertThat(DocumentoSemanal.huellaDe(DatosDePruebaDelDocumento.mes()))
                .isEqualTo(DocumentoSemanal.huellaDe(DatosDePruebaDelDocumento.mes()));
    }

    @Test
    @DisplayName("spec 056 FR-039: un dato distinto cambia la huella, hasta el más chico: una palabra, el día o la tendencia")
    void unDatoDistintoCambiaLaHuella() {
        String base = DocumentoSemanal.huellaDe(DatosDePruebaDelDocumento.aprendiz());

        assertThat(DocumentoSemanal.huellaDe(DatosDePruebaDelDocumento.aprendizConPalabras("Otra cosa"))).isNotEqualTo(base);
        assertThat(DocumentoSemanal.huellaDe(DatosDePruebaDelDocumento.aprendizConDia(48))).isNotEqualTo(base);
        assertThat(DocumentoSemanal.huellaDe(DatosDePruebaDelDocumento.aprendizConDia(null))).isNotEqualTo(base);
        assertThat(DocumentoSemanal.huellaDe(DatosDePruebaDelDocumento.aprendiz("Otra Persona"))).isNotEqualTo(base);
    }

    @Test
    @DisplayName("spec 056 FR-039: la huella de un mentor cambia con la semana propia que se le suma, y no se confunde con la de un aprendiz")
    void laHuellaDelMentor() {
        String sinPrograma = DocumentoSemanal.huellaDe(DatosDePruebaDelDocumento.mentor());
        String conPrograma = DocumentoSemanal.huellaDe(DatosDePruebaDelDocumento.mentor(DatosDePruebaDelDocumento.aprendiz("Luisa Rojas")));

        assertThat(conPrograma).isNotEqualTo(sinPrograma);
        assertThat(sinPrograma).isNotEqualTo(DocumentoSemanal.huellaDe(DatosDePruebaDelDocumento.aprendiz()));
    }

    @Test
    @DisplayName("spec 056 FR-039: la ruta es reportes/{persona}/{inicio}-{tipo}-{huella8}.html y siempre la misma para los mismos datos")
    void laRutaEsDeterminista() {
        String huella = DocumentoSemanal.huellaDe(DatosDePruebaDelDocumento.aprendiz());
        Contenido contenido = new Contenido(ANA, TipoDeDocumento.APRENDIZ, DatosDePruebaDelDocumento.SABADO,
                DatosDePruebaDelDocumento.VIERNES, huella, LIDER);

        DocumentoSemanal uno = DocumentoSemanal.generar(UUID.randomUUID(), contenido, RELOJ);
        DocumentoSemanal otro = DocumentoSemanal.generar(UUID.randomUUID(), contenido, RELOJ);

        assertThat(uno.ruta()).isEqualTo("reportes/" + ANA + "/2026-11-14-APRENDIZ-" + huella.substring(0, 8) + ".html");
        assertThat(otro.ruta()).isEqualTo(uno.ruta());
    }

    @Test
    @DisplayName("spec 056 FR-039: generarlo guarda quién lo generó y cuándo, con el reloj del sistema y no con la fecha del servidor")
    void generarGuardaQuienYCuando() {
        Contenido contenido = new Contenido(ANA, TipoDeDocumento.MENTOR, DatosDePruebaDelDocumento.SABADO,
                DatosDePruebaDelDocumento.VIERNES, DocumentoSemanal.huellaDe(DatosDePruebaDelDocumento.mentor()), LIDER);

        DocumentoSemanal documento = DocumentoSemanal.generar(UUID.randomUUID(), contenido, RELOJ);

        assertThat(documento.generadoEn()).isEqualTo(AHORA);
        assertThat(documento.generadoPor()).isEqualTo(LIDER);
        assertThat(documento.personaId()).isEqualTo(ANA);
        assertThat(documento.tipo()).isEqualTo(TipoDeDocumento.MENTOR);
        assertThat(documento.periodoInicio()).isEqualTo(DatosDePruebaDelDocumento.SABADO);
        assertThat(documento.periodoFin()).isEqualTo(DatosDePruebaDelDocumento.VIERNES);
    }

    @Test
    @DisplayName("spec 056 FR-039: al leerlo de la base conserva la ruta y la hora que se guardaron")
    void alLeerloConservaLoGuardado() {
        String huella = DocumentoSemanal.huellaDe(DatosDePruebaDelDocumento.mes());
        Contenido contenido = new Contenido(LIDER, TipoDeDocumento.MES, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), huella, LIDER);
        UUID id = UUID.randomUUID();

        DocumentoSemanal leido = DocumentoSemanal.rehydrate(id, contenido, new Constancia("reportes/guardada.html", AHORA));

        assertThat(leido.id()).isEqualTo(id);
        assertThat(leido.ruta()).isEqualTo("reportes/guardada.html");
        assertThat(leido.generadoEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("spec 056 FR-039: una huella que no es un SHA-256 o un período al revés no forman un documento")
    void rechazaLoQueNoEsValido() {
        LocalDate sabado = DatosDePruebaDelDocumento.SABADO;
        LocalDate viernes = DatosDePruebaDelDocumento.VIERNES;
        String huellaBuena = DocumentoSemanal.huellaDe(DatosDePruebaDelDocumento.aprendiz());

        assertThatThrownBy(() -> new Contenido(ANA, TipoDeDocumento.APRENDIZ, sabado, viernes, "corta", LIDER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Contenido(ANA, TipoDeDocumento.APRENDIZ, sabado, viernes, huellaBuena.toUpperCase(), LIDER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Contenido(ANA, TipoDeDocumento.APRENDIZ, viernes, sabado, huellaBuena, LIDER))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("termina antes de empezar");
    }
}
