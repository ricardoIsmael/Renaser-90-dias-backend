package com.renaser.os.users.infrastructure.adapter.out.persistence.eliminacion;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.eliminacion.BorrarDatosPropiosDeCuentaPort;
import com.renaser.os.users.domain.model.user.Email;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Las tablas de {@code users} que guardan algo de la persona, en orden de hijos a padres (D-243). Los
 * demas modulos ya borraron lo suyo ({@code users.api.BorradoDeDatosDeCuenta}); las FK con CASCADE
 * contra {@code usuarios}/{@code participantes_programa} quedan como red de seguridad.
 *
 * <p>Por SQL y no por los repositorios JPA: son seis tablas y un borrado en bloque, sin entidades que
 * cargar. Todas son de este modulo. Las columnas de «quien lo hizo» que apuntan a esta cuenta desde
 * filas de OTROS ({@code ajustes_dia_programa.ajustado_por}, {@code auditoria_cambios_rol},
 * {@code solicitudes_cuenta.revisada_por}) las deja en NULL la propia base (FK SET NULL, V90): la
 * historia de los demas se conserva sin el autor.
 */
@Component
class BorrarDatosPropiosDeCuentaJdbcAdapter implements BorrarDatosPropiosDeCuentaPort {

    private final JdbcTemplate jdbc;

    BorrarDatosPropiosDeCuentaJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void borrarTodoDe(UserId cuenta, Email email) {
        var id = cuenta.value();
        jdbc.update("DELETE FROM renaser.ajustes_dia_programa WHERE participante_id = ?", id);
        jdbc.update("DELETE FROM renaser.participantes_programa WHERE usuario_id = ?", id);
        jdbc.update("DELETE FROM renaser.perfiles_mentor WHERE usuario_id = ?", id);
        jdbc.update("DELETE FROM renaser.identidades_externas WHERE usuario_id = ?", id);
        // Por dueño y por correo: la del alta de esta cuenta, y cualquier otra con su correo, que
        // dejaria el correo ocupado para una cuenta nueva (UNIQUE de solicitudes_cuenta.email).
        jdbc.update("DELETE FROM renaser.solicitudes_cuenta WHERE usuario_id = ? OR usuario_creado_id = ? "
                + "OR lower(email) = lower(?)", id, id, email.value());
        jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id);
    }
}
