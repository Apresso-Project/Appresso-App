package com.appresso.backend.tiemporeal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Solo para probar el tiempo real a mano (SCRUM-31 y SCRUM-32): emite eventos de mentira para
 * ver cómo los recibe la app Android y medir la latencia.
 *
 * <p>Existe únicamente con el perfil {@code dev} (el mismo que crea el administrador de prueba);
 * en producción este controlador no se carga. Con sesión de administrador (2.3.1).</p>
 *
 * <pre>
 * curl -X POST "http://localhost:8080/api/dev/tiempo-real/emitir?cantidad=20&amp;pausaMs=500" \
 *      -H "Authorization: Bearer &lt;token del administrador&gt;"
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
	@PreAuthorize("hasRole('ADMINISTRADOR')")
	public Map<String, Object> emitir(@RequestParam(defaultValue = "10") int cantidad,
			@RequestParam(defaultValue = "0") long pausaMs) {
		int total = Math.max(1, Math.min(cantidad, MAXIMO_EVENTOS));
		long pausa = Math.max(0, Math.min(pausaMs, MAXIMA_PAUSA_MS));
		Thread hilo = new Thread(() -> emitirAhora(total, pausa), "emisor-pruebas-tiempo-real");
		hilo.setDaemon(true);
		hilo.start();
		return Map.of("programados", total, "pausaMs", pausa);
	}

	/**
	 * Emite {@code cantidad} eventos de los que mueven la pantalla de cocina (cambios de estado y
	 * productos agregados, alternados), uno cada {@code pausaMs} milisegundos. Sirve para medir en un
	 * teléfono cuánto tarda la pantalla de cocina en releer y mostrar la lista tras un evento
	 * (SCRUM-93, docs/medicion-cocina.md). Las comandas de mentira no existen en la base: la lista
	 * REST no cambia, pero la app igual la vuelve a pedir, que es lo que se mide.
	 */
	@PostMapping("/emitir-cocina")
	@PreAuthorize("hasRole('ADMINISTRADOR')")
	public Map<String, Object> emitirCocina(@RequestParam(defaultValue = "10") int cantidad,
			@RequestParam(defaultValue = "0") long pausaMs) {
		int total = Math.max(1, Math.min(cantidad, MAXIMO_EVENTOS));
		long pausa = Math.max(0, Math.min(pausaMs, MAXIMA_PAUSA_MS));
		Thread hilo = new Thread(() -> emitirCocinaAhora(total, pausa), "emisor-pruebas-cocina");
		hilo.setDaemon(true);
		hilo.start();
		return Map.of("programados", total, "pausaMs", pausa);
	}

	void emitirCocinaAhora(int cantidad, long pausaMs) {
		for (int i = 1; i <= cantidad; i++) {
			long mesaId = 1L + i % 10;
			if (i % 2 == 1) {
				publicador.publicarComanda(TipoEvento.COMANDA_ESTADO_CAMBIADO, new ComandaEventoDatos(
						(long) i, mesaId, "EN_PREPARACION", "ENVIADA", new BigDecimal("9000.00"), null));
			}
			else {
				publicador.publicarComanda(TipoEvento.COMANDA_MODIFICADA, new ComandaEventoDatos(
						(long) i, mesaId, "ENVIADA", null, new BigDecimal("13500.00"),
						List.of(new ComandaEventoDatos.ItemAgregado(1L, 1, "sin hielo"))));
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
