package com.renaser.os.shared.application;

import com.renaser.os.shared.application.DecisionDeLimite.Accion;
import com.renaser.os.shared.application.ports.out.AnotarCuentaQueHabriaBloqueadoPort;
import com.renaser.os.shared.application.ports.out.ModoDeLosLimitesPort;
import com.renaser.os.shared.application.ports.out.RegistrarMetricaDeLimitesPort;
import com.renaser.os.shared.application.ports.out.TopesDeLimites;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.ContadorDeVentanas;
import com.renaser.os.shared.domain.ContadorDeVentanas.Resultado;
import com.renaser.os.shared.domain.ContadorDeVentanas.Ventana;
import com.renaser.os.shared.domain.GrupoDeLimite;
import com.renaser.os.shared.domain.ModoDeLimite;
import com.renaser.os.shared.domain.ResultadoDelLimite;
import com.renaser.os.shared.domain.SujetoDeLimite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Decide qué hacer con un pedido contado por el filtro de límite por cuenta (spec 050, FR-012 a FR-019): cuenta, mira el modo del
 * grupo y devuelve {@link DecisionDeLimite}. No conoce HTTP: el filtro le pasa el grupo y a quién se cuenta.
 *
 * <ul>
 *   <li>{@code DESACTIVADO}: no cuenta nada.</li>
 *   <li>{@code REGISTRAR}: cuenta igual; si se pasó, anota «habría bloqueado» y <b>deja pasar</b>. Nunca rechaza (SC-002).</li>
 *   <li>{@code APLICAR}: cuenta y, si se pasó, rechaza con los segundos que faltan para que venza la ventana.</li>
 * </ul>
 *
 * <p><b>El modo se cambia sin reiniciar</b> (Q6): el de Redis ({@link ModoDeLosLimitesPort}) manda sobre el de la configuración y se
 * relee cada {@code cache-del-modo}. Si Redis no responde, se sigue con lo último que se supo.
 *
 * <p><b>Nunca falla un pedido por esto</b> (FR-017): si el contador lanza una excepción el pedido pasa, se cuenta como
 * {@code error_de_redis} y se anota (una vez cada 30 s, para no llenar el registro). El límite protege; no es fuente de verdad de
 * negocio, y con Redis caído la sesión tampoco se lee, así que la API ya no atiende.
 */
@Component
public class PoliticaDeLimites {

    private static final Logger log = LoggerFactory.getLogger(PoliticaDeLimites.class);
    private static final long MILIS_ENTRE_AVISOS = 30_000;

    private final TopesDeLimites topes;
    private final ContadorDeVentanas contador;
    private final ModoDeLosLimitesPort modos;
    private final RegistrarMetricaDeLimitesPort metricas;
    private final AnotarCuentaQueHabriaBloqueadoPort anotador;
    private final Clock clock;
    private final AtomicReference<ModosLeidos> modosLeidos = new AtomicReference<>(ModosLeidos.NINGUNO);
    private volatile long ultimoAvisoEnMilis;

    public PoliticaDeLimites(TopesDeLimites topes, ContadorDeVentanas contador, ModoDeLosLimitesPort modos,
                             RegistrarMetricaDeLimitesPort metricas, AnotarCuentaQueHabriaBloqueadoPort anotador, Clock clock) {
        this.topes = topes;
        this.contador = contador;
        this.modos = modos;
        this.metricas = metricas;
        this.anotador = anotador;
        this.clock = clock;
    }

    /** El modo con el que se cuenta este grupo ahora: el de Redis si hay, si no el de la configuración. */
    public ModoDeLimite modoDe(GrupoDeLimite grupo) {
        if (!topes.habilitado()) {
            return ModoDeLimite.DESACTIVADO;
        }
        ModoDeLimite fijado = modosFijados().get(grupo);
        return fijado != null ? fijado : topes.modoPorDefecto(grupo);
    }

    public DecisionDeLimite decidir(GrupoDeLimite grupo, SujetoDeLimite sujeto) {
        ModoDeLimite modo = modoDe(grupo);
        List<Ventana> ventanas = topes.ventanasDe(grupo);
        if (modo == ModoDeLimite.DESACTIVADO || ventanas.isEmpty()) {
            return DecisionDeLimite.pasar();
        }
        try {
            return contar(grupo, sujeto, modo, ventanas);
        } catch (RuntimeException falla) {
            metricas.pedido(grupo, ResultadoDelLimite.ERROR_DE_REDIS);
            avisar("No se pudo contar el pedido del grupo " + grupo.clave() + " (" + falla.getClass().getSimpleName() + "): pasa sin contarse");
            return DecisionDeLimite.pasar();
        }
    }

    private DecisionDeLimite contar(GrupoDeLimite grupo, SujetoDeLimite sujeto, ModoDeLimite modo, List<Ventana> ventanas) {
        Resultado resultado = contador.intentar(grupo, sujeto, ventanas);
        resultado.usos().forEach(uso -> metricas.uso(grupo, uso.ventana(), uso.proporcion()));
        if (resultado.paso()) {
            metricas.pedido(grupo, ResultadoDelLimite.PERMITIDO);
            return DecisionDeLimite.pasar();
        }
        if (modo == ModoDeLimite.APLICAR) {
            metricas.pedido(grupo, ResultadoDelLimite.BLOQUEADO);
            return new DecisionDeLimite(Accion.RECHAZAR, resultado.ventanaPasada(), resultado.segundosParaReintentar());
        }
        metricas.pedido(grupo, ResultadoDelLimite.HABRIA_BLOQUEADO);
        anotarQuienHabriaBloqueado(grupo, sujeto);
        return new DecisionDeLimite(Accion.PASAR_Y_ANOTAR, resultado.ventanaPasada(), 0);
    }

    /** El registro durable es de mejor esfuerzo: que no se pueda escribir no cambia lo que pasa con el pedido. */
    private void anotarQuienHabriaBloqueado(GrupoDeLimite grupo, SujetoDeLimite sujeto) {
        try {
            anotador.anotar(grupo, sujeto);
        } catch (RuntimeException falla) {
            avisar("No se pudo anotar quién habría sido bloqueado en " + grupo.clave() + " (" + falla.getClass().getSimpleName() + ")");
        }
    }

    private Map<GrupoDeLimite, ModoDeLimite> modosFijados() {
        ModosLeidos leidos = modosLeidos.get();
        Instant ahora = clock.now();
        if (leidos.vigenteA(ahora)) {
            return leidos.fijados();
        }
        ModosLeidos nuevos = releer(leidos, ahora);
        modosLeidos.set(nuevos);
        return nuevos.fijados();
    }

    private ModosLeidos releer(ModosLeidos anteriores, Instant ahora) {
        Instant venceEn = ahora.plus(topes.cacheDelModo());
        try {
            return new ModosLeidos(modos.fijados(), ahora, venceEn);
        } catch (RuntimeException falla) {
            avisar("No se pudo leer el modo de los límites de Redis (" + falla.getClass().getSimpleName() + "): se sigue con el último conocido");
            return new ModosLeidos(anteriores.fijados(), ahora, venceEn);
        }
    }

    private void avisar(String mensaje) {
        long ahora = clock.now().toEpochMilli();
        if (ahora - ultimoAvisoEnMilis >= MILIS_ENTRE_AVISOS) {
            ultimoAvisoEnMilis = ahora;
            log.warn(mensaje);
        }
    }

    /**
     * Lo último que se leyó de Redis y entre qué instantes vale. Un reloj que retrocede (un ajuste del sistema) también obliga a
     * releer: si no, un instante futuro dejaría el modo congelado hasta que el reloj lo alcanzara.
     */
    private record ModosLeidos(Map<GrupoDeLimite, ModoDeLimite> fijados, Instant leidoEn, Instant venceEn) {

        static final ModosLeidos NINGUNO = new ModosLeidos(Map.of(), Instant.MIN, Instant.MIN);

        boolean vigenteA(Instant ahora) {
            return !ahora.isBefore(leidoEn) && ahora.isBefore(venceEn);
        }
    }
}
