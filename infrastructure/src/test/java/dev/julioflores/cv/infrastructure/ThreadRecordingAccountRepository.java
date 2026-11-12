package dev.julioflores.cv.infrastructure;

import dev.julioflores.cv.domain.AdminAccount;
import dev.julioflores.cv.infrastructure.adapter.out.postgres.PostgresAdminAccountRepository;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * Solo en pruebas: delega en el repositorio real, apunta en qué hilo se lee la cuenta y puede simular la base de datos
 * caída. Sirve para comprobar que el filtro de sesión (JDBC bloqueante) no corre en el hilo de eventos.
 */
@Alternative
@Priority(1)
@Singleton
public class ThreadRecordingAccountRepository extends PostgresAdminAccountRepository {

    public static final List<String> READ_THREADS = new CopyOnWriteArrayList<>();
    public static volatile boolean failReads;

    public ThreadRecordingAccountRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Optional<AdminAccount> find() {
        READ_THREADS.add(Thread.currentThread().getName());
        if (failReads) {
            throw new IllegalStateException("jdbc:postgresql://secreto:5432/db password=hunter2 is down");
        }
        return super.find();
    }
}
