package com.appresso.backend.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Zona horaria del negocio (CaffeBar, Colombia: UTC-5, sin horario de verano).
 * <p>
 * Los instantes se guardan en UTC ({@code Instant}), pero lo que el negocio entiende como
 * "hoy" (jornada de caja, vigencia de una promoción) se calcula en esta zona. Todo código que
 * necesite la fecha o la hora actual debe pedir este {@link Clock} y no usar
 * {@code LocalDate.now()}, para que no dependa de la zona del servidor y se pueda probar.
 */
@Configuration
public class ZonaHorariaConfig {

	public static final String ZONA_POR_DEFECTO = "America/Bogota";

	@Bean
	public ZoneId zonaHorariaNegocio(@Value("${appresso.zona-horaria:" + ZONA_POR_DEFECTO + "}") String zona) {
		return ZoneId.of(zona);
	}

	@Bean
	public Clock reloj(ZoneId zonaHorariaNegocio) {
		return Clock.system(zonaHorariaNegocio);
	}

}
