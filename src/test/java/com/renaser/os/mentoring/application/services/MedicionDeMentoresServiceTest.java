package com.renaser.os.mentoring.application.services;

import com.renaser.os.mentoring.api.MedicionDeMentoresFinder.MedicionVigente;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder.SemaforoResumido;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static com.renaser.os.mentoring.application.services.BancoDelSemaforo.DESDE_VIGENTE;
import static com.renaser.os.mentoring.application.services.BancoDelSemaforo.HASTA_VIGENTE;
import static com.renaser.os.mentoring.application.services.VentanasDePrueba.ventanaPareja;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * La medición del semáforo agrupada por mentor que lee la gestión del Líder de Mentores (D-241): la
 * misma de {@code SemaforoPorGruposService}, sin guard propio.
 */
class MedicionDeMentoresServiceTest {

    private static final UUID FENIX = UUID.fromString("00000000-0000-0000-0000-00000000f001");
    private static final UUID AURORA = UUID.fromString("00000000-0000-0000-0000-00000000f002");
    private static final UUID ALBA = UUID.fromString("00000000-0000-0000-0000-00000000f003");
    private static final UserId LUISA = id("a1");
    private static final UserId RAUL = id("a2");
    private static final UserId ANA = id("b1");
    private static final UserId LUIS = id("b2");
    private static final UserId PIA = id("b4");
    private static final UserId SUSPENDIDA = id("b5");

    private final BancoDelSemaforo banco = new BancoDelSemaforo();
    private MedicionDeMentoresService servicio;

    @BeforeEach
    void preparar() {
        servicio = new MedicionDeMentoresService(new MedicionDeGrupos(banco.acompanamiento, banco.semaforo,
                banco.usuarios), banco.acompanamiento, banco.reloj);
        banco.usuario(LUISA, "Luisa Rojas", UserRole.MENTOR, UserStatus.ACTIVE);
        banco.usuario(RAUL, "Raúl Soto", UserRole.MENTOR, UserStatus.ACTIVE);

        banco.grupo(FENIX, "Grupo Fénix");
        banco.mentor(FENIX, LUISA);
        aprendiz(FENIX, ANA, 90);
        aprendiz(FENIX, LUIS, 50);

        // Luisa lidera dos grupos (D-141): Ana está en los dos (D-139) y se cuenta una vez.
        banco.grupo(ALBA, "Grupo Alba");
        banco.mentor(ALBA, LUISA);
        banco.aprendiz(ALBA, ANA);

        banco.grupo(AURORA, "Grupo Aurora");
        banco.mentor(AURORA, RAUL);
        aprendiz(AURORA, PIA, 70);
        banco.aprendiz(AURORA, SUSPENDIDA);
        banco.usuario(SUSPENDIDA, "Sin cuenta", UserRole.TRAINEE, UserStatus.SUSPENDED);
    }

    private static UserId id(String sufijo) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufijo));
    }

    private void aprendiz(UUID grupo, UserId aprendiz, int porcentaje) {
        banco.aprendiz(grupo, aprendiz);
        banco.persona(aprendiz, "Aprendiz " + aprendiz);
        banco.vigente(aprendiz, ventanaPareja(DESDE_VIGENTE, false, porcentaje));
    }

    @Test
    @DisplayName("por mentor: la union de sus grupos sin repetir, con el mismo resumen que el del lider")
    void porMentorSobreLaUnionDeSusGrupos() {
        MedicionVigente medicion = servicio.vigente();

        SemaforoResumido deLuisa = medicion.porMentor().get(LUISA);
        assertThat(deLuisa.total()).isEqualTo(2);
        assertThat(deLuisa.verde()).isEqualTo(1);
        assertThat(deLuisa.rojo()).isEqualTo(1);
        assertThat(deLuisa.promedio()).isEqualByComparingTo(new BigDecimal("70.0"));
        assertThat(deLuisa.color()).isEqualTo("AMARILLO");
        assertThat(deLuisa.etiqueta()).isEqualTo("Requiere atención");
        assertThat(medicion.grupos()).hasSize(3);
    }

    @Test
    @DisplayName("una cuenta suspendida no se cuenta, igual que en la tabla del semaforo")
    void soloCuentasActivas() {
        MedicionVigente medicion = servicio.vigente();

        assertThat(medicion.porMentor().get(RAUL).total()).isEqualTo(1);
        assertThat(medicion.grupos().stream().filter(g -> g.grupoId().equals(AURORA)).findFirst().orElseThrow()
                .aprendices()).containsExactly(PIA);
    }

    @Test
    @DisplayName("la ventana vigente viaja con la medicion")
    void ventanaVigente() {
        MedicionVigente medicion = servicio.vigente();

        assertThat(medicion.desde()).isEqualTo(DESDE_VIGENTE);
        assertThat(medicion.hasta()).isEqualTo(HASTA_VIGENTE);
    }
}
