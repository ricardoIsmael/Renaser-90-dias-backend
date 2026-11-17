package com.renaser.os.mentoring.domain.model.nota;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.RechazoConCodigoException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las reglas de la nota privada del mentor (spec 055), sin Spring ni base de datos.
 */
class NotaDelMentorTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final UserId MENTORA = UserId.of(UUID.randomUUID());
    private static final UserId OTRA = UserId.of(UUID.randomUUID());
    /** 22:00 del martes 17 en Lima, que en UTC ya es el miércoles 18 a las 03:00. */
    private static final Instant NOCHE_EN_LIMA = Instant.parse("2026-11-18T03:00:00Z");

    private static NotaDelMentor escribir(String texto, Instant cuando) {
        return NotaDelMentor.escribir(UUID.randomUUID(), ANA, MENTORA, texto, LIMA, FixedClock.at(cuando));
    }

    @Test
    @DisplayName("spec 055 FR-026: el texto se guarda recortado y tiene entre 1 y 2000 caracteres sin contar los espacios de los extremos")
    void elTextoSeRecortaYTieneLimites() {
        assertThat(escribir("  Hablamos de su ritmo \n", NOCHE_EN_LIMA).texto()).isEqualTo("Hablamos de su ritmo");
        assertThat(escribir("x".repeat(2000), NOCHE_EN_LIMA).texto()).hasSize(2000);
        assertThat(escribir("  " + "x".repeat(2000) + "  ", NOCHE_EN_LIMA).texto()).as("los espacios de los extremos no cuentan").hasSize(2000);

        for (String malo : new String[] {null, "", "   \n ", "x".repeat(2001)}) {
            assertThatThrownBy(() -> escribir(malo, NOCHE_EN_LIMA))
                    .isInstanceOfSatisfying(RechazoConCodigoException.class, e -> {
                        assertThat(e.codigo()).isEqualTo("NOTA_INVALIDA");
                        assertThat(e.estado()).isEqualTo(400);
                    });
        }
    }

    @Test
    @DisplayName("spec 055 FR-023: la nota guarda su aprendiz, su autor y el día local del AUTOR (no el de UTC)")
    void guardaElDiaDelAutor() {
        NotaDelMentor nota = escribir("Primera", NOCHE_EN_LIMA);

        assertThat(nota.aprendizId()).isEqualTo(ANA);
        assertThat(nota.autorId()).isEqualTo(MENTORA);
        assertThat(nota.creadoEn()).isEqualTo(NOCHE_EN_LIMA);
        assertThat(nota.diaDelAutor()).as("en Lima todavía es el martes 17").hasToString("2026-11-17");
        assertThat(nota.corregidoEn()).isNull();
    }

    @Test
    @DisplayName("spec 055 FR-025: el autor la corrige el mismo día, también pasada la medianoche de UTC, y cada corrección entrega el texto anterior")
    void elAutorCorrigeElMismoDia() {
        NotaDelMentor nota = escribir("Primera", NOCHE_EN_LIMA);
        Instant ultimoMinuto = Instant.parse("2026-11-18T04:59:00Z");

        VersionDeLaNota anterior = nota.corregir(MENTORA, " Segunda ", LIMA, FixedClock.at(ultimoMinuto));
        VersionDeLaNota anteriorDeLaSegunda = nota.corregir(MENTORA, "Tercera", LIMA, FixedClock.at(ultimoMinuto));

        assertThat(anterior.textoAnterior()).isEqualTo("Primera");
        assertThat(anterior.notaId()).isEqualTo(nota.id());
        assertThat(anterior.reemplazadoEn()).isEqualTo(ultimoMinuto);
        assertThat(anteriorDeLaSegunda.textoAnterior()).isEqualTo("Segunda");
        assertThat(nota.texto()).isEqualTo("Tercera");
        assertThat(nota.corregidoEn()).isEqualTo(ultimoMinuto);
        assertThat(nota.puedeCorregirla(MENTORA, LIMA, FixedClock.at(ultimoMinuto))).isTrue();
    }

    @Test
    @DisplayName("spec 055 FR-025: desde la medianoche del autor ya no se corrige, aunque en UTC sea el mismo día")
    void yaNoSeCorrigeAlOtroDia() {
        NotaDelMentor nota = escribir("Primera", NOCHE_EN_LIMA);
        Instant pasadaLaMedianocheEnLima = Instant.parse("2026-11-18T05:00:00Z");

        assertThat(nota.puedeCorregirla(MENTORA, LIMA, FixedClock.at(pasadaLaMedianocheEnLima))).isFalse();
        assertThatThrownBy(() -> nota.corregir(MENTORA, "Tarde", LIMA, FixedClock.at(pasadaLaMedianocheEnLima)))
                .isInstanceOfSatisfying(RechazoConCodigoException.class, e -> {
                    assertThat(e.codigo()).isEqualTo("YA_NO_SE_PUEDE_CORREGIR");
                    assertThat(e.estado()).isEqualTo(409);
                });
        assertThat(nota.texto()).as("no cambió").isEqualTo("Primera");
        assertThat(nota.corregidoEn()).isNull();
    }

    @Test
    @DisplayName("spec 055 FR-025: solo el autor corrige; otra persona recibe NO_ES_EL_AUTOR aunque sea el mismo día")
    void soloElAutorCorrige() {
        NotaDelMentor nota = escribir("Primera", NOCHE_EN_LIMA);

        assertThat(nota.puedeCorregirla(OTRA, LIMA, FixedClock.at(NOCHE_EN_LIMA))).isFalse();
        assertThatThrownBy(() -> nota.corregir(OTRA, "Ajena", LIMA, FixedClock.at(NOCHE_EN_LIMA)))
                .isInstanceOfSatisfying(NotAuthorizedException.class, e -> assertThat(e.codigo()).isEqualTo("NO_ES_EL_AUTOR"));
        assertThat(nota.texto()).isEqualTo("Primera");
    }

    @Test
    @DisplayName("spec 055 FR-026: una corrección vacía o de más de 2000 caracteres no cambia la nota")
    void laCorreccionInvalidaNoCambiaNada() {
        NotaDelMentor nota = escribir("Primera", NOCHE_EN_LIMA);

        assertThatThrownBy(() -> nota.corregir(MENTORA, "  ", LIMA, FixedClock.at(NOCHE_EN_LIMA)))
                .isInstanceOf(RechazoConCodigoException.class);
        assertThatThrownBy(() -> nota.corregir(MENTORA, "x".repeat(2001), LIMA, FixedClock.at(NOCHE_EN_LIMA)))
                .isInstanceOf(RechazoConCodigoException.class);
        assertThat(nota.texto()).isEqualTo("Primera");
        assertThat(nota.corregidoEn()).isNull();
    }

    @Test
    @DisplayName("spec 055 FR-025: no hay forma de borrar una nota")
    void noSeBorra() {
        assertThat(Arrays.stream(NotaDelMentor.class.getMethods()).map(Method::getName))
                .noneMatch(nombre -> nombre.toLowerCase().contains("borrar") || nombre.toLowerCase().contains("eliminar")
                        || nombre.toLowerCase().contains("delete") || nombre.toLowerCase().contains("remove"));
    }
}
