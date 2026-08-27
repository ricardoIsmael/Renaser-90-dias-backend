package com.renaser.os.users.application.ports.in.accountrequest;

/**
 * ¿Este correo ya tiene cuenta o solicitud? Porte de {@code checkEmailAvailability} y
 * {@code accountExists} del repo viejo (AR-04 y AR-06), que compartian el MISMO lookup
 * ({@code tieneCuentaOSolicitud}) y solo se diferenciaban en como se lee la respuesta. Aca
 * tambien es un solo caso de uso; quien nombra la respuesta es el endpoint (CLAUDE.MD §5.4.8,
 * ISP: un puerto por intencion, no dos consultas identicas duplicadas).
 *
 * <p>Incluye solicitudes pendientes, no solo cuentas aprobadas — regla portada tal cual: alguien
 * que se registro y espera aprobacion ya ocupa ese correo.
 *
 * <p><b>Sobre enumeracion de correos.</b> Este caso de uso le dice a quien pregunte si una
 * direccion esta registrada. La decision de producto del 2026-08-01 (documentada en el repo
 * viejo) fue aceptar ese coste a cambio de que nadie llene seis campos para descubrir al final
 * que su correo ya existe; el argumento es que {@code POST /account-requests} ya filtraba el
 * mismo dato, asi que esto no abre informacion nueva, solo la vuelve barata de sondear.
 *
 * <p>Lo que SI cambia respecto del repo viejo: alli se concluyo que el limite por IP tenia que
 * vivir en el borde (WAF) porque en serverless no habia donde contar. Corriendo siempre-arriba
 * y con Redis ya en el stack, el limite vive aca dentro reutilizando
 * {@code LimitarSolicitudesResetPort} — que es lo que CLAUDE.MD §5.3.6 anticipaba.
 */
public interface ConsultarEmailRegistradoUseCase {

    /**
     * @param requestIp IP de quien pregunta, para el limite de tasa. La consulta es publica y sin
     *                  sesion, asi que la IP es el unico sujeto al que se le puede contar.
     * @return {@code true} si existe una cuenta o una solicitud con ese correo.
     */
    boolean estaRegistrado(String email, String requestIp);
}
