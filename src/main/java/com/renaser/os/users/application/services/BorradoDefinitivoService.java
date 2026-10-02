package com.renaser.os.users.application.services;

import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BorradoDeDatosDeCuenta;
import com.renaser.os.users.application.ports.in.autenticacion.CerrarTodasLasSesionesUseCase;
import com.renaser.os.users.application.ports.out.eliminacion.BorrarDatosPropiosDeCuentaPort;
import com.renaser.os.users.application.ports.out.eliminacion.RegistrarEliminacionPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.user.ClavesDeCuenta;
import com.renaser.os.users.domain.model.user.RegistroDeEliminacion;
import com.renaser.os.users.domain.model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Borra una cuenta para siempre (D-243): la usan el barrido de las cuentas cerradas cuya gracia
 * vencio y la eliminacion inmediata desde Administracion.
 *
 * <p><b>Cada modulo borra lo suyo.</b> Esta clase no conoce ninguna tabla ajena: junta lo que cada
 * modulo declara por {@link BorradoDeDatosDeCuenta} y despues borra las tablas de {@code users}. Hasta
 * D-243 la purga leia con SQL las rutas de seis modulos ajenos y dejaba el resto a las FK con
 * CASCADE ({@code RutasDeAlmacenamientoDeCuentaJdbcAdapter}, borrado).
 *
 * <p><b>El orden importa, y es el mismo que tenia la purga vieja:</b>
 * <ol>
 *   <li>Primero el censo de archivos, ANTES de borrar filas: las filas son la unica forma de saber
 *       que objetos habia.</li>
 *   <li>Despues los objetos del bucket y recien al final las filas. Si el proceso muere en el medio
 *       la cuenta sigue en pie y el barrido de la hora siguiente repite todo (borrar es idempotente);
 *       al reves, los objetos quedarian huerfanos sin nadie que supiera nombrarlos.</li>
 *   <li>Todas las filas en UNA transaccion: una cuenta a medio borrar no es un estado aceptable.</li>
 * </ol>
 *
 * <p><b>Que archivos se borran.</b> Solo los que son de esta cuenta por la FORMA de la clave
 * ({@link ClavesDeCuenta}) y que ninguna fila que sobrevive sigue mirando (cada modulo responde por
 * las suyas). Ante la duda, no se borra; lo que queda se anota en el log.
 */
@Service
class BorradoDefinitivoService {

    private static final Logger log = LoggerFactory.getLogger(BorradoDefinitivoService.class);

    private final LoadUserPort loadUserPort;
    private final List<BorradoDeDatosDeCuenta> modulos;
    private final BorrarDatosPropiosDeCuentaPort borrarDatosPropios;
    private final RegistrarEliminacionPort registrarEliminacion;
    private final AlmacenamientoPort almacenamiento;
    private final CerrarTodasLasSesionesUseCase cerrarSesiones;
    private final TransactionTemplate transaccion;
    private final Clock clock;

    BorradoDefinitivoService(LoadUserPort loadUserPort, List<BorradoDeDatosDeCuenta> modulos,
                             BorrarDatosPropiosDeCuentaPort borrarDatosPropios,
                             RegistrarEliminacionPort registrarEliminacion, AlmacenamientoPort almacenamiento,
                             CerrarTodasLasSesionesUseCase cerrarSesiones,
                             PlatformTransactionManager transactionManager, Clock clock) {
        this.loadUserPort = loadUserPort;
        this.modulos = List.copyOf(modulos);
        this.borrarDatosPropios = borrarDatosPropios;
        this.registrarEliminacion = registrarEliminacion;
        this.almacenamiento = almacenamiento;
        this.cerrarSesiones = cerrarSesiones;
        this.transaccion = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * @param accion  por que se borra (para la auditoria)
     * @param actorId quien la borra; {@code null} si es el barrido
     * @return {@code false} si la cuenta ya no existia (idempotente)
     */
    boolean borrar(UserId cuentaId, RegistroDeEliminacion.Accion accion, UserId actorId) {
        Optional<User> encontrada = loadUserPort.byId(cuentaId);
        if (encontrada.isEmpty()) {
            return false;
        }
        User cuenta = encontrada.get();
        cerrarSesiones.cerrarTodas(cuentaId);
        borrarDelBucket(cuenta, archivosExclusivosDe(cuenta));
        transaccion.executeWithoutResult(estado -> {
            for (BorradoDeDatosDeCuenta modulo : modulos) {
                modulo.borrarDatosDe(cuentaId);
            }
            borrarDatosPropios.borrarTodoDe(cuentaId, cuenta.email());
            registrarEliminacion.registrar(RegistroDeEliminacion.de(cuenta, accion, actorId, clock.now()));
        });
        return true;
    }

    private Set<String> archivosExclusivosDe(User cuenta) {
        Set<String> candidatas = candidatasDe(cuenta);
        if (candidatas.isEmpty()) {
            return Set.of();
        }
        Set<String> enUso = enUsoPorFilasQueQuedan(cuenta.id(), Set.copyOf(candidatas));
        if (!enUso.isEmpty()) {
            log.warn("[users.BorradoDefinitivo] la cuenta {} deja {} objeto(s) en el bucket: otra fila que "
                    + "sobrevive los sigue usando", cuenta.id(), enUso.size());
        }
        candidatas.removeAll(enUso);
        return candidatas;
    }

    /** Lo que cada modulo guarda de la cuenta, filtrado por la forma de la clave. */
    private Set<String> candidatasDe(User cuenta) {
        ClavesDeCuenta propias = ClavesDeCuenta.de(cuenta.id());
        Set<String> candidatas = new HashSet<>();
        if (cuenta.avatarUrl() != null) {
            candidatas.add(propias.avatar());
        }
        for (BorradoDeDatosDeCuenta modulo : modulos) {
            candidatas.addAll(modulo.archivosDe(cuenta.id()));
        }
        candidatas.removeIf(clave -> !propias.contiene(clave));
        return candidatas;
    }

    private Set<String> enUsoPorFilasQueQuedan(UserId cuentaId, Set<String> candidatas) {
        Set<String> enUso = new HashSet<>();
        for (BorradoDeDatosDeCuenta modulo : modulos) {
            enUso.addAll(modulo.archivosEnUsoTrasBorrar(cuentaId, candidatas));
        }
        return enUso;
    }

    /** Un fallo de S3 no frena el borrado de la cuenta, pero queda escrito: no se traga en silencio. */
    private void borrarDelBucket(User cuenta, Set<String> claves) {
        for (String clave : claves) {
            try {
                almacenamiento.borrar(clave);
            } catch (RuntimeException e) {
                log.error("[users.BorradoDefinitivo] la cuenta {} se borra pero quedo el objeto {}",
                        cuenta.id(), clave, e);
            }
        }
    }
}
