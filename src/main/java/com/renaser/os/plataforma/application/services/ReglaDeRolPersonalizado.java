package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.domain.model.catalogo.CatalogoDeFuncionalidades;
import com.renaser.os.plataforma.domain.model.estado.ActorEvaluado;
import com.renaser.os.plataforma.domain.model.estado.ReglaDeDisponibilidad;
import com.renaser.os.plataforma.domain.model.roles.DisponibilidadPorRolPersonalizado;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * La regla de disponibilidad de los roles personalizados (contrato de la fase 2, §1 y §4.4; D-272), {@code @Order(20)}:
 * después del despliegue progresivo ({@code @Order(10)}). Con un rol personalizado vigente, lo que su rol no marca y su
 * base usa queda «no disponible» (404 {@code FUNCIONALIDAD_NO_DISPONIBLE}, la app lo esconde). Sin rol personalizado, o
 * con uno desactivado, no hace nada.
 *
 * <p>Responde desde {@link CopiaDeRolesPersonalizados}: se evalúa en cada pedido y ~55 veces por
 * {@code GET /funcionalidades/mias}. Usa solo {@code actor.id()}: el rol base lo saca de la copia, sin leer la cuenta.
 */
@Component
@Order(20)
class ReglaDeRolPersonalizado implements ReglaDeDisponibilidad {

    private final CopiaDeRolesPersonalizados copia;
    private final CatalogoDeFuncionalidades catalogo = CatalogoDeFuncionalidades.inicial();

    ReglaDeRolPersonalizado(CopiaDeRolesPersonalizados copia) {
        this.copia = copia;
    }

    @Override
    public Optional<String> noDisponibleEn(List<String> cadena, ActorEvaluado actor) {
        return copia.foto().de(actor.id())
                .flatMap(rol -> DisponibilidadPorRolPersonalizado.noDisponibleEn(cadena, rol, catalogo));
    }
}
