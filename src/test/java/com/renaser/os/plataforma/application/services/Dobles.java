package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.out.CargarEstadosPort;
import com.renaser.os.plataforma.application.ports.out.GuardarEstadoPort;
import com.renaser.os.plataforma.application.ports.out.LimitarDesbloqueosPort;
import com.renaser.os.plataforma.application.ports.out.RegistrarCambioAuditadoPort;
import com.renaser.os.plataforma.application.ports.out.TokensDeConsolaPort;
import com.renaser.os.plataforma.domain.model.auditoria.CambioAuditado;
import com.renaser.os.plataforma.domain.model.estado.EstadoDeFuncionalidad;
import com.renaser.os.shared.domain.UserId;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Dobles en memoria de los puertos de salida de {@code plataforma}, para las pruebas de sus servicios. */
final class Dobles {

    private Dobles() {
    }

    /** La base: filas por clave, con bloqueo optimista y una secuencia de versiones. */
    static final class Base implements CargarEstadosPort, GuardarEstadoPort {

        final Map<String, EstadoDeFuncionalidad> filas = new HashMap<>();
        private long secuencia;

        @Override
        public List<EstadoDeFuncionalidad> todos() {
            return List.copyOf(filas.values());
        }

        @Override
        public long siguienteVersion() {
            return ++secuencia;
        }

        @Override
        public boolean guardar(EstadoDeFuncionalidad nuevo, long versionEsperada) {
            EstadoDeFuncionalidad actual = filas.get(nuevo.clave());
            long vigente = actual == null ? 0L : actual.version();
            if (vigente != versionEsperada) {
                return false;
            }
            filas.put(nuevo.clave(), nuevo);
            return true;
        }
    }

    static final class Auditoria implements RegistrarCambioAuditadoPort {

        final List<CambioAuditado> filas = new ArrayList<>();

        @Override
        public void registrar(CambioAuditado cambio) {
            filas.add(cambio);
        }
    }

    /** Tokens: guarda «actor|sesion» por token; la reautenticación se gasta. */
    static final class Tokens implements TokensDeConsolaPort {

        final Map<String, String> consola = new HashMap<>();
        final Map<String, String> reautenticacion = new HashMap<>();

        @Override
        public String emitirDeConsola(UserId actor, String sesion, Duration vigencia) {
            String token = UUID.randomUUID().toString();
            consola.put(token, actor + "|" + sesion);
            return token;
        }

        @Override
        public String emitirDeReautenticacion(UserId actor, String sesion, Duration vigencia) {
            String token = UUID.randomUUID().toString();
            reautenticacion.put(token, actor + "|" + sesion);
            return token;
        }

        @Override
        public boolean consolaVigente(String token, UserId actor, String sesion, Duration vigencia) {
            return (actor + "|" + sesion).equals(consola.get(token));
        }

        @Override
        public boolean gastarReautenticacion(String token, UserId actor, String sesion) {
            return (actor + "|" + sesion).equals(reautenticacion.remove(token));
        }
    }

    static final class Limitador implements LimitarDesbloqueosPort {

        int fallos;

        @Override
        public boolean bloqueado(UserId actor, int maximo) {
            return fallos >= maximo;
        }

        @Override
        public void registrarFallo(UserId actor, Duration ventana, int maximo) {
            fallos++;
        }
    }
}
