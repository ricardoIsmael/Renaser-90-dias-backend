package com.renaser.os.users.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.participante.AvanzarDiaProgramaUseCase;
import com.renaser.os.users.application.ports.in.participante.SetTraineeProgramDayUseCase;
import com.renaser.os.users.application.ports.in.participante.SetTraineeProgramDayUseCase.SetProgramDayCommand;
import com.renaser.os.users.application.ports.out.participante.ListarParticipantesConProgramaActivoPort;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;

/**
 * D-197 contra Postgres real: un ajuste de dia que cae entre que el barrido del reloj LEE su pagina
 * y GUARDA la fila no se pierde.
 *
 * <p>La carrera se arma de forma determinista, no con hilos: un espia sobre el puerto de paginas
 * devuelve la pagina real y, antes de soltarla, ejecuta el ajuste del admin completo
 * ({@link SetTraineeProgramDayUseCase}, su propia transaccion, con bitacora). El barrido sigue con
 * la version que leyo — la de antes del ajuste —, que es exactamente lo que pasa en produccion
 * cuando el admin guarda a las 00:05 de Lima mientras el barrido recorre el padron.
 *
 * <p>Contra el codigo viejo ({@code saveParticipacionProgramaPort.save} de la fila entera) esta
 * prueba falla: {@code dias_ajuste_programa} vuelve a 0 y el dia queda en 40.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AjusteDeDiaContraElBarridoIT {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired
    private AvanzarDiaProgramaUseCase barrido;
    @Autowired
    private SetTraineeProgramDayUseCase ajustarDia;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Clock clock;
    @MockitoSpyBean
    private ListarParticipantesConProgramaActivoPort paginas;

    private UserId admin;
    private UserId aprendiz;

    @BeforeEach
    void seed() {
        admin = nuevoUsuario("ADMIN");
        aprendiz = nuevoUsuario("APRENDIZ");
        LocalDate hoyEnLima = clock.now().atZone(LIMA).toLocalDate();
        // Dia 40 de calendario; la columna quedo en el 39 de ayer: el barrido tiene algo que escribir.
        jdbcTemplate.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio,
                        programa_activado_en, timezone, dia_programa_avanzado_el, dias_ajuste_programa)
                VALUES (?, 39, ?, now(), 'America/Lima', ?, 0)
                """, aprendiz.value(), hoyEnLima.minusDays(39), hoyEnLima.minusDays(1));
    }

    @AfterEach
    void limpiar() {
        // ajustes_dia_programa cae por CASCADE del participante; despues ya se puede borrar al admin.
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", aprendiz.value());
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", admin.value());
    }

    private UserId nuevoUsuario(String rol) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Fixture D-197', CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", rol);
        return UserId.of(id);
    }

    @Test
    @DisplayName("el admin retrocede al 34 despues de que el barrido leyo la pagina: el ajuste queda")
    void elAjusteHechoMientrasElBarridoCorreNoSePierde() {
        AtomicBoolean ajustado = new AtomicBoolean(false);
        doAnswer(invocacion -> {
            @SuppressWarnings("unchecked")
            List<ParticipacionPrograma> pagina = (List<ParticipacionPrograma>) invocacion.callRealMethod();
            boolean traeAlAprendiz = pagina.stream().anyMatch(p -> p.participanteId().equals(aprendiz));
            if (traeAlAprendiz && ajustado.compareAndSet(false, true)) {
                ajustarDia.fijarDia(new SetProgramDayCommand(admin, aprendiz, 34, "Viaje"));
            }
            return pagina;
        }).when(paginas).pagina(anyInt(), anyInt());

        barrido.avanzarParticipantesActivos();

        assertThat(ajustado).as("la pagina tenia que traer al aprendiz para armar la carrera").isTrue();
        Map<String, Object> fila = jdbcTemplate.queryForMap("""
                SELECT dia_programa, dias_ajuste_programa, dia_programa_avanzado_el
                  FROM renaser.participantes_programa WHERE usuario_id = ?
                """, aprendiz.value());
        assertThat(((Number) fila.get("dias_ajuste_programa")).intValue()).as("el ajuste del admin").isEqualTo(6);
        assertThat(((Number) fila.get("dia_programa")).intValue()).isEqualTo(34);
        assertThat(fila.get("dia_programa_avanzado_el").toString())
                .isEqualTo(clock.now().atZone(LIMA).toLocalDate().toString());
    }
}
