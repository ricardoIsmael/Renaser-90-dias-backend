package com.renaser.os.plataforma.domain.model.estado;

import com.renaser.os.plataforma.domain.model.catalogo.DefinicionDeFuncionalidad;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Lo que Soporte TI pide al cambiar un interruptor (contrato §6, {@code PUT /soporte-ti/funcionalidades/{clave}}): el
 * estado completo que quiere —para todos, por rol, aviso— con su motivo y la versión que estaba mirando.
 *
 * @param versionEsperada la versión de la fila que leyó (0 si no había): si cambió entretanto, 409 (FR-019)
 */
public record CambioDeFuncionalidad(String clave, boolean prendidaGlobal, Set<UserRole> apagadaPara, String aviso,
                                    String motivo, long versionEsperada) {

    static final int LARGO_MAXIMO_DEL_MOTIVO = 500;

    public CambioDeFuncionalidad {
        Objects.requireNonNull(clave, "clave es obligatoria");
        apagadaPara = apagadaPara == null ? Set.of() : Set.copyOf(apagadaPara);
        aviso = EstadoDeFuncionalidad.normalizarAviso(aviso);
        motivo = requerirMotivo(motivo);
        if (versionEsperada < 0) {
            throw new IllegalArgumentException("La version no puede ser negativa");
        }
    }

    /**
     * Crítico (FR-008): toda funcionalidad marcada crítica, o dejar cualquiera apagada para todos. Pide la contraseña
     * de nuevo y avisa a Administración y al Alquimista.
     */
    public boolean esCritico(DefinicionDeFuncionalidad definicion) {
        return definicion.critica() || !prendidaGlobal;
    }

    /** La fila nueva, con la versión que le toca y quién lo hizo. */
    public EstadoDeFuncionalidad aplicar(long nuevaVersion, UserId actor, Instant ahora) {
        return new EstadoDeFuncionalidad(clave, prendidaGlobal, apagadaPara, aviso, nuevaVersion, actor, ahora);
    }

    /** El estado nuevo en palabras, para el push: «Apagada para todos», «Apagada para Aprendiz, Mentor»… */
    public String resumen() {
        if (!prendidaGlobal) {
            return "Apagada para todos";
        }
        if (apagadaPara.isEmpty()) {
            return "Prendida para todos";
        }
        return "Apagada para " + String.join(", ", apagadaPara.stream().sorted()
                .map(UserRole::nombreVisible).toList());
    }

    private static String requerirMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Escribe el motivo del cambio");
        }
        String limpio = motivo.strip();
        if (limpio.codePointCount(0, limpio.length()) > LARGO_MAXIMO_DEL_MOTIVO) {
            throw new IllegalArgumentException("El motivo no puede pasar de " + LARGO_MAXIMO_DEL_MOTIVO + " caracteres");
        }
        return limpio;
    }
}
