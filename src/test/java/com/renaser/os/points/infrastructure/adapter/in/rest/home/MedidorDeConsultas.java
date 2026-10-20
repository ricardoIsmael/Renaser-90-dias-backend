package com.renaser.os.points.infrastructure.adapter.in.rest.home;

import com.renaser.os.points.application.ports.in.semaforo.ConsultarMensajeDelSemaforoUseCase;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Cuenta las sentencias SQL que llegan a la base, para probar el costo en consultas del mensaje del semáforo
 * (SC-004, D-277). Envuelve el {@link DataSource} del contexto —cubre JPA y {@code JdbcClient} por igual— y anota
 * cada sentencia con el hilo que la lanzó y si cayó dentro de {@code ConsultarMensajeDelSemaforoUseCase.deHoy}, que
 * es lo que mide el costo propio del mensaje sin mezclar lo que {@code /home} ya hacía por su cuenta.
 *
 * <p>El registro es estático y compartido porque el {@link BeanPostProcessor} nace antes que cualquier bean que la
 * prueba pueda inyectar. Una prueba lo vacía con {@link #reiniciar()}, hace UN pedido y lee {@link #sentencias()};
 * como otros hilos del contexto (tareas programadas) también escriben acá, se filtra por hilo de pedido o por la
 * marca de «dentro del mensaje», que solo la pone el hilo del pedido.
 */
@TestConfiguration(proxyBeanMethods = false)
class MedidorDeConsultas {

    /** Una sentencia vista: su SQL, el hilo y si se lanzó dentro de {@code deHoy}. */
    record Sentencia(String sql, String hilo, boolean dentroDelMensaje) {

        boolean esLectura() {
            String inicio = sql.stripLeading().toLowerCase(Locale.ROOT);
            return inicio.startsWith("select") || inicio.startsWith("with");
        }

        boolean esEscritura() {
            String inicio = sql.stripLeading().toLowerCase(Locale.ROOT);
            return inicio.startsWith("insert") || inicio.startsWith("update") || inicio.startsWith("delete");
        }

        boolean esDeUnPedidoHttp() {
            return hilo.startsWith("tomcat-handler");
        }
    }

    private static final List<Sentencia> VISTAS = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<Boolean> DENTRO_DEL_MENSAJE = ThreadLocal.withInitial(() -> false);
    private static final Set<String> EJECUTORES = Set.of("execute", "executeQuery", "executeUpdate",
            "executeLargeUpdate", "executeBatch", "executeLargeBatch");

    static void reiniciar() {
        VISTAS.clear();
    }

    static List<Sentencia> sentencias() {
        return List.copyOf(VISTAS);
    }

    @Bean
    static BeanPostProcessor medidorDeConsultas() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String nombre) {
                if (bean instanceof DataSource base && !(bean instanceof DataSourceMedido)) {
                    return new DataSourceMedido(base);
                }
                if (bean instanceof ConsultarMensajeDelSemaforoUseCase mensaje) {
                    return marcarElMensaje(mensaje);
                }
                return bean;
            }
        };
    }

    private static ConsultarMensajeDelSemaforoUseCase marcarElMensaje(ConsultarMensajeDelSemaforoUseCase real) {
        return (ConsultarMensajeDelSemaforoUseCase) Proxy.newProxyInstance(
                ConsultarMensajeDelSemaforoUseCase.class.getClassLoader(),
                new Class<?>[]{ConsultarMensajeDelSemaforoUseCase.class}, (proxy, metodo, argumentos) -> {
                    DENTRO_DEL_MENSAJE.set(true);
                    try {
                        return invocar(real, metodo, argumentos);
                    } finally {
                        DENTRO_DEL_MENSAJE.set(false);
                    }
                });
    }

    private static final class DataSourceMedido extends DelegatingDataSource {

        DataSourceMedido(DataSource destino) {
            super(destino);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return medida(super.getConnection());
        }

        @Override
        public Connection getConnection(String usuario, String clave) throws SQLException {
            return medida(super.getConnection(usuario, clave));
        }
    }

    private static Connection medida(Connection real) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, metodo, argumentos) -> {
                    Object resultado = invocar(real, metodo, argumentos);
                    String nombre = metodo.getName();
                    if (nombre.equals("prepareStatement") || nombre.equals("prepareCall")) {
                        return sentenciaMedida((PreparedStatement) resultado, (String) argumentos[0]);
                    }
                    return nombre.equals("createStatement") ? sentenciaMedida((Statement) resultado, null) : resultado;
                });
    }

    private static Object sentenciaMedida(Statement real, String sqlPreparado) {
        Class<?> tipo = real instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
        return Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[]{tipo}, (proxy, metodo, argumentos) -> {
            if (EJECUTORES.contains(metodo.getName())) {
                anotar(sqlPreparado != null ? sqlPreparado : sqlDe(argumentos));
            }
            return invocar(real, metodo, argumentos);
        });
    }

    /** El SQL de un {@code execute(String)} de una sentencia simple; un lote de una simple no trae texto. */
    private static String sqlDe(Object[] argumentos) {
        return argumentos != null && argumentos.length > 0 && argumentos[0] instanceof String sql ? sql : "(lote)";
    }

    private static void anotar(String sql) {
        VISTAS.add(new Sentencia(sql, Thread.currentThread().getName(), DENTRO_DEL_MENSAJE.get()));
    }

    private static Object invocar(Object destino, Method metodo, Object[] argumentos) throws Throwable {
        try {
            return metodo.invoke(destino, argumentos);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
