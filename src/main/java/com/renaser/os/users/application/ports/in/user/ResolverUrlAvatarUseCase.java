package com.renaser.os.users.application.ports.in.user;

/**
 * Firma la URL de lectura del avatar AL LEER. Es el UNICO lugar del sistema que convierte
 * {@code usuarios.avatar_ruta} en algo servible, y por eso el unico lugar donde se decide
 * cuanto vale esa firma.
 *
 * <p>Existe como puerto de entrada — y no como colaborador suelto — porque los adaptadores
 * web de `users` tambien lo necesitan: un controller puede inyectar un puerto {@code in},
 * nunca un puerto {@code out} como {@code AlmacenamientoPort} (CLAUDE.MD §5.4.6, y la regla
 * {@code controllersDoNotTouchPersistence} de {@code ArchitectureTest} lo hace cumplir).
 *
 * <p>Los otros modulos (`chat`, `community`, `support`, `rag`) no lo llaman ni lo conocen:
 * reciben la URL ya firmada dentro de {@code users.api.UserSummary}.
 */
public interface ResolverUrlAvatarUseCase {

    /**
     * @param rutaAvatar valor de {@code usuarios.avatar_ruta}; {@code null}/vacio = sin avatar.
     * @return una URL de lectura FRESCA, o {@code null} si no hay avatar. Nunca se persiste.
     */
    String urlDeLectura(String rutaAvatar);
}
