package com.renaser.os.users.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase.SubmitAccountRequestCommand;
import com.renaser.os.users.application.ports.out.autenticacion.TokenVerificacionEmailPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regresion de C-16 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html), la mitad
 * del hallazgo sobre el token de verificacion de email: antes, {@code submit()} consumia
 * {@code verificationToken} (GETDEL en Redis, de un solo uso) ANTES de intentar el INSERT -- si
 * el correo ya estaba registrado y el INSERT fallaba, el token ya estaba perdido y la persona
 * tenia que reverificar su correo de cero para volver a intentarlo con OTRO correo.
 *
 * <p>Requiere Redis real (Testcontainers): la prueba de que el token "no se perdio" es que
 * TODAVIA se puede consumir despues del intento fallido -- eso solo lo puede confirmar el
 * adaptador real ({@code TokenVerificacionEmailRedisAdapter}, GETDEL atomico), no un mock que
 * simplemente registra si {@code consumir} se llamo o no (eso ya lo cubre
 * {@code AccountRequestServiceTest}, a nivel unitario).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountRequestServiceTokenNoSePierdeIT {

    @Autowired
    private SubmitAccountRequestUseCase submitAccountRequestUseCase;
    @Autowired
    private TokenVerificacionEmailPort tokenVerificacionEmailPort;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String emailYaRegistrado;

    /** Una cuenta ya existente por otro camino (ej. alta anterior ya aprobada), para forzar el
     * choque de C-16 sin depender de una carrera real. */
    @BeforeEach
    void seedUsuarioExistente() {
        emailYaRegistrado = "ya-registrado-" + UUID.randomUUID() + "@renaser.test";
        UserId id = UserId.of(UUID.randomUUID());
        jdbcTemplate.update("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (?, ?, 'Ya Registrado', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """,
                id.value(), emailYaRegistrado);
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE email = ?", emailYaRegistrado);
    }

    @Test
    @DisplayName("C-16: submit() rechaza un correo ya registrado SIN consumir el token de "
            + "verificacion -- el mismo token sigue siendo valido despues del intento fallido")
    void submitRechazaCorreoYaRegistradoSinPerderElToken() {
        String token = tokenVerificacionEmailPort.generar(emailYaRegistrado, Duration.ofMinutes(10));

        assertThatThrownBy(() -> submitAccountRequestUseCase.submit(SubmitAccountRequestCommand.porFormulario(
                emailYaRegistrado, "Otra Persona", null, null, token, "una-contrasena-de-12-o-mas", null)))
                .isInstanceOf(IllegalStateException.class);

        // Si el token se hubiera consumido durante el intento fallido, este segundo consumo
        // devolveria vacio (GETDEL ya lo habria borrado) -- justo lo que pasaba antes de C-16.
        var emailCertificado = tokenVerificacionEmailPort.consumir(token);
        assertThat(emailCertificado).as("el token sigue vivo: no se gasto en un intento que iba a fallar igual")
                .contains(emailYaRegistrado);
    }
}
