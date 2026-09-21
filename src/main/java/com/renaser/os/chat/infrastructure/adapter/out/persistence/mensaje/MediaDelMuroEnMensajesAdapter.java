package com.renaser.os.chat.infrastructure.adapter.out.persistence.mensaje;

import com.renaser.os.community.api.ReferenciasExternasDeMediaDelMuro;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

/**
 * La respuesta de {@code chat} a la pregunta del Muro: "¿alguno de estos objetos lo sigue
 * necesitando una conversacion?".
 *
 * <p><b>Por que existe.</b> Compartir una publicacion en el chat <b>no copia el archivo</b>:
 * {@code CompartirPublicacionService} manda la MISMA clave {@code muro/} como media del mensaje y
 * {@code MensajeService.urlDeLectura} la vuelve a firmar en cada lectura — el javadoc de
 * {@code PublicacionParaCompartir} lo dice sin rodeos: "no se copia un solo byte". Si el borrado
 * fisico de una publicacion sacara el objeto del bucket sin preguntar, esa foto quedaria en 404
 * para siempre dentro de una conversacion privada que ninguna moderacion toco, y sin forma de
 * restaurarla. Es exactamente la rotura contra la que {@code MensajeService} ya advierte por
 * escrito al explicar por que admite el prefijo {@code muro/}.
 *
 * <p><b>Por que del lado de chat y no una consulta del Muro.</b>
 * {@code .claude/rules/01-arquitectura-hexagonal} prohibe que un modulo mande SQL nativo contra
 * una tabla ajena, y la dependencia no se puede invertir: {@code chat} ya importa
 * {@code community.api}, asi que un {@code community -> chat.api} cerraria un ciclo de Modulith.
 * Cada modulo responde por su tabla; la direccion de la dependencia queda como estaba.
 */
@Component
class MediaDelMuroEnMensajesAdapter implements ReferenciasExternasDeMediaDelMuro {

    private final SpringDataMensajeRepository repository;

    MediaDelMuroEnMensajesAdapter(SpringDataMensajeRepository repository) {
        this.repository = repository;
    }

    @Override
    public Set<String> referenciadas(Collection<String> rutasMuro) {
        if (rutasMuro == null || rutasMuro.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(repository.mediaRutasEnUso(rutasMuro));
    }
}
