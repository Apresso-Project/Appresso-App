package com.appresso.backend.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Comprueba que la aplicación tiene un DataSource configurado y que puede
 * abrir una conexión válida. En las pruebas usa H2 en memoria (ver
 * src/test/resources/application.properties).
 */
@SpringBootTest
class DatabaseConnectionTest {

	@Autowired
	private DataSource dataSource;

	@Test
	void abreUnaConexionValida() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.isValid(2)).isTrue();
		}
	}

}
