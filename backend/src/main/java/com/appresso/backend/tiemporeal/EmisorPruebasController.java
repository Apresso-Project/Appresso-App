package com.appresso.backend.tiemporeal;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Solo para probar el tiempo real a mano (SCRUM-31 y SCRUM-32): emite eventos de mentira para
 * ver cómo los recibe la app Android y medir la latencia.
 *
 * <p>Existe únicamente con el perfil {@code dev} (el mismo que crea el administrador de prueba);
 * en producción este controlador no se carga.</p>
 *
 * <pre>
 * curl -X POST "http://localhost:8080/api/dev/tiempo-real/emitir?cantidad=20&amp;pausaMs=500"
 * </pre>
 */
@Profile("dev")
@RestController
@RequestMapping("/api/dev/tiempo-real")
public class EmisorPruebasController {

	static final int MAXIMO_EVENTOS = 5_000;
	static final long MAXIMA_PAUSA_MS = 5_000;

	private final PublicadorTiempoReal publicador;

	public EmisorPruebasController(PublicadorTiempoReal publicador) {
		this.publicador = publicador;
	}

	/**
	 * Emite {@code cantidad} eventos, uno cada {@code pausaMs} milisegundos, alternando un evento
	 * de comandas y uno de catálogo (así un cliente suscrito a ambos tópicos ve la secuencia continua).
	 * Responde de inmediato; los eventos salen en segundo plano.
	 */
	@PostMapping("/emitir")
	public Map<String, Object> emitir(@RequestParam(defaultValue = "10") int cantidad,
			@RequestParam(defaultValue = "0") long pausaMs) {
		int total = Math.max(1, Math.min(cantidad, MAXIMO_EVENTOS));
		long pausa = Math.max(0, Math.min(pausaMs, MAXIMA_PAUSA_MS));
		Thread hilo = new Thread(() -> emitirAhora(total, pausa), "emisor-pruebas-tiempo-real");
		hilo.setDaemon(true);
		hilo.start();
		return Map.of("programados", total, "pausaMs", pausa);
	}

	void emitirAhora(int cantidad, long pausaMs) {
		for (int i = 1; i <= cantidad; i++) {
			if (i % 2 == 1) {
				publicador.publicarComanda(TipoEvento.COMANDA_CREADA, new ComandaEventoDatos(
						(long) i, 1L + i % 10, "PENDIENTE", null, new BigDecimal("9000.00"), null));
			}
			else {
				publicador.publicarProducto(TipoEvento.EXISTENCIAS_ACTUALIZADAS, new ProductoEventoDatos(
						1L, 1L, "Café de prueba", new BigDecimal("4500.00"), 100 - i % 100, 5, true));
			}
			if (pausaMs > 0 && i < cantidad) {
				try {
					Thread.sleep(pausaMs);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}

}
