package com.renaser.os.leadership.application.services;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Los servicios REALES de la ruta de huecos del líder (spec 015) —el caso de uso y sus dos guardas, que son
 * package-private—, para la prueba del controller: el 403 de un MENTOR lo da {@code AccesoDeLiderazgo} (el interceptor
 * lo deja pasar, A-1), así que mockear el caso de uso no probaría nada. Los otros módulos entran como dobles de la
 * propia prueba.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({AccesoDeLiderazgo.class, PersonasDelGrupo.class, HuecosDePersonaService.class})
public class HuecosDePersonaDePrueba {
}
