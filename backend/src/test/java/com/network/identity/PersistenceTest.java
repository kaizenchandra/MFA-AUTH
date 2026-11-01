package com.network.identity;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class PersistenceTest {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17.6-alpine");
    static JdbcTemplate db;
    @BeforeAll static void migrate(){var ds=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());Flyway.configure().dataSource(ds).load().migrate();db=new JdbcTemplate(ds);}
    @Test void simultaneousProvisioningCreatesOneProfile() throws Exception {
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {var tasks=new ArrayList<Future<?>>();for(int i=0;i<12;i++)tasks.add(pool.submit(()->db.update("INSERT INTO app_user(id,issuer,subject) VALUES (?,'https://issuer','same') ON CONFLICT DO NOTHING",UUID.randomUUID())));for(var task:tasks)task.get();}
        assertEquals(1,db.queryForObject("SELECT count(*) FROM app_user WHERE subject='same'",Integer.class));
    }
    @Test void subjectIsScopedToIssuer(){UUID a=UUID.randomUUID(),b=UUID.randomUUID();db.update("INSERT INTO app_user(id,issuer,subject) VALUES (?,'https://one','subject'),(?,'https://two','subject')",a,b);assertEquals(2,db.queryForObject("SELECT count(*) FROM app_user WHERE subject='subject'",Integer.class));}
}
