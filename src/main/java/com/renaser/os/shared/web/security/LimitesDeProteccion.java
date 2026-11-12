package com.renaser.os.shared.web.security;

import com.renaser.os.shared.application.ports.out.TopesDeLimites;
import com.renaser.os.shared.domain.ContadorDeVentanas.Ventana;
import com.renaser.os.shared.domain.GrupoDeLimite;
import com.renaser.os.shared.domain.ModoDeLimite;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code renaser.proteccion.limites.*} (spec 050, plan §5): los topes y el modo de cada grupo del límite por cuenta. Los valores de
 * partida viven en {@code application.yaml} (los de FR-013, todos en {@code REGISTRAR}); un grupo sin tope en una ventana no la cuenta.
 *
 * <p>El modo de aquí es el <b>de partida</b>: el que el dueño fija en Redis ({@code proteccion:modo:<grupo>}) manda sobre él y se
 * cambia sin reiniciar (ver {@code ModoDeLosLimitesPort}).
 *
 * @param habilitado  {@code false} apaga todo el filtro (vuelta atrás de emergencia)
 * @param cacheDelModo cada cuánto se relee el modo de Redis
 * @param grupos      por {@link GrupoDeLimite#clave()}
 */
@ConfigurationProperties(prefix = "renaser.proteccion.limites")
public record LimitesDeProteccion(@DefaultValue("true") boolean habilitado, @DefaultValue("30s") Duration cacheDelModo,
                                  Map<String, Grupo> grupos) implements TopesDeLimites {

    public LimitesDeProteccion {
        grupos = grupos == null ? Map.of() : Map.copyOf(grupos);
    }

    /** Los topes de un grupo; 0 es «sin tope en esa ventana». */
    public record Grupo(@DefaultValue("REGISTRAR") ModoDeLimite modo, @DefaultValue("0") int porMinuto,
                        @DefaultValue("0") int porHora, @DefaultValue("0") int porDia) {
    }

    @Override
    public ModoDeLimite modoPorDefecto(GrupoDeLimite grupo) {
        Grupo configurado = grupos.get(grupo.clave());
        return configurado == null ? ModoDeLimite.REGISTRAR : configurado.modo();
    }

    @Override
    public List<Ventana> ventanasDe(GrupoDeLimite grupo) {
        Grupo configurado = grupos.get(grupo.clave());
        List<Ventana> ventanas = new ArrayList<>(3);
        if (configurado == null) {
            return ventanas;
        }
        agregar(ventanas, "1m", Duration.ofMinutes(1), configurado.porMinuto());
        agregar(ventanas, "1h", Duration.ofHours(1), configurado.porHora());
        agregar(ventanas, "1d", Duration.ofHours(24), configurado.porDia());
        return ventanas;
    }

    private static void agregar(List<Ventana> ventanas, String nombre, Duration duracion, int tope) {
        if (tope > 0) {
            ventanas.add(new Ventana(nombre, duracion, tope));
        }
    }
}
