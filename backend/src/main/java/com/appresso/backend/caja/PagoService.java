package com.appresso.backend.caja;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appresso.backend.auditoria.RegistroAuditoria;
import com.appresso.backend.auditoria.RegistroAuditoriaRepository;
import com.appresso.backend.comandas.Comanda;
import com.appresso.backend.comandas.ComandaRepository;
import com.appresso.backend.comandas.ComandaService;
import com.appresso.backend.comandas.EstadoComanda;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;

import jakarta.persistence.EntityNotFoundException;

/**
 * Registro de pagos (RF-28, RF-29, RF-30, RF-41, RF-43).
 *
 * <p>Reglas:</p>
 * <ul>
 * <li>Efectivo: solo cajero y administrador. Transferencia: mesero, cajero y administrador.
 * Aunque las rutas ya filtran por rol ({@code PoliticaPermisos}), se vuelve a validar aquí.</li>
 * <li>Solo se paga una comanda {@code LISTA}. Una {@code CERRADA} ya está pagada, o fue cancelada
 * (cerrada sin pago, RF-63): en ambos casos no admite otro pago.</li>
 * <li>El valor es el total de la comanda, calculado por el servidor.</li>
 * <li>Al registrar el pago la comanda pasa a {@code CERRADA} y la mesa queda disponible.</li>
 * <li>Un solo pago por comanda: lo garantizan la validación y la restricción única de la base
 * (cubre dos solicitudes simultáneas).</li>
 * </ul>
 */
@Service
public class PagoService {

	/** Resultado de registrar: {@code repetido} es true si era un reintento de un pago ya registrado. */
	public record Resultado(PagoRespuesta pago, boolean repetido) {
	}

	private static final String YA_PAGADA = "Esta comanda ya fue pagada.";

	private final PagoRepository pagos;
	private final ComandaRepository comandas;
	private final UsuarioRepository usuarios;
	private final JornadaRepository jornadas;
	private final ComandaService comandaService;
	private final RegistroAuditoriaRepository auditoria;

	public PagoService(PagoRepository pagos, ComandaRepository comandas, UsuarioRepository usuarios,
			JornadaRepository jornadas, ComandaService comandaService, RegistroAuditoriaRepository auditoria) {
		this.pagos = pagos;
		this.comandas = comandas;
		this.usuarios = usuarios;
		this.jornadas = jornadas;
		this.comandaService = comandaService;
		this.auditoria = auditoria;
	}

	@Transactional
	public Resultado registrar(Long comandaId, MedioPago medio, UUID identificadorOperacion, Long actorId) {
		Usuario actor = usuarios.findById(actorId)
				.orElseThrow(() -> new EntityNotFoundException("Usuario " + actorId + " no existe"));
		exigirPermiso(actor.getRol(), medio);
		Comanda comanda = comandas.findById(comandaId)
				.orElseThrow(() -> new EntityNotFoundException("Comanda " + comandaId + " no existe"));

		Optional<Pago> previo = pagos.findByComandaId(comandaId);
		if (previo.isPresent()) {
			Pago p = previo.get();
			boolean mismoIntento = identificadorOperacion != null
					&& identificadorOperacion.equals(p.getIdentificadorOperacion()) && p.getMedio() == medio;
			if (mismoIntento) {
				return new Resultado(PagoRespuesta.de(p), true);
			}
			throw new ConflictoPagoException(YA_PAGADA);
		}
		if (comanda.getEstado() == EstadoComanda.CERRADA) {
			throw new ConflictoPagoException("La comanda fue cancelada y no admite pagos.");
		}
		if (comanda.getEstado() != EstadoComanda.LISTA) {
			throw new ConflictoPagoException("La comanda aún no está lista para pagar.");
		}
		BigDecimal valor = comanda.getTotal();
		if (valor == null || valor.signum() <= 0) {
			throw new ConflictoPagoException("La comanda no tiene un valor por cobrar.");
		}

		Pago pago = new Pago();
		pago.setComanda(comanda);
		pago.setJornada(jornadaActual(actor));
		pago.setValor(valor);
		pago.setMedio(medio);
		pago.setFecha(Instant.now());
		pago.setConfirmadoPor(actor);
		if (identificadorOperacion != null) {
			pago.setIdentificadorOperacion(identificadorOperacion);
		}
		try {
			pagos.saveAndFlush(pago);
		}
		catch (DataIntegrityViolationException e) {
			// Otra solicitud pagó la misma comanda (o reutilizó el identificador) un instante antes.
			throw new ConflictoPagoException(YA_PAGADA);
		}

		comandaService.cerrarPorPago(comanda, actor);
		registrarAuditoria(actor, pago);
		return new Resultado(PagoRespuesta.de(pago), false);
	}

	private static void exigirPermiso(Rol rol, MedioPago medio) {
		boolean permitido = switch (medio) {
			case EFECTIVO -> rol == Rol.ADMINISTRADOR || rol == Rol.CAJERO;
			case TRANSFERENCIA -> rol == Rol.ADMINISTRADOR || rol == Rol.CAJERO || rol == Rol.MESERO;
		};
		if (!permitido) {
			throw new AccessDeniedException("No tienes permiso para realizar esta acción.");
		}
	}

	/**
	 * La jornada abierta. Mientras no exista el flujo de apertura (RF-49), si no hay ninguna se abre
	 * una con saldo inicial de efectivo en cero, para que el primer pago no quede bloqueado.
	 */
	private Jornada jornadaActual(Usuario actor) {
		return jornadas.findFirstByEstadoOrderByFechaAperturaDesc(EstadoJornada.ABIERTA).orElseGet(() -> {
			Jornada nueva = new Jornada();
			nueva.setEstado(EstadoJornada.ABIERTA);
			nueva.setFechaApertura(Instant.now());
			nueva.setSaldoInicialEfectivo(BigDecimal.ZERO);
			nueva.setAbiertaPor(actor);
			Jornada guardada = jornadas.save(nueva);
			RegistroAuditoria r = new RegistroAuditoria();
			r.setUsuario(actor);
			r.setTipoOperacion("JORNADA_ABIERTA_AUTOMATICA");
			r.setEntidad("Jornada");
			r.setEntidadId(guardada.getId());
			r.setDetalle("No había jornada abierta; se abrió una con saldo inicial de efectivo 0 al registrar un pago.");
			auditoria.save(r);
			return guardada;
		});
	}

	private void registrarAuditoria(Usuario actor, Pago pago) {
		RegistroAuditoria r = new RegistroAuditoria();
		r.setUsuario(actor);
		r.setTipoOperacion("PAGO_REGISTRADO");
		r.setEntidad("Pago");
		r.setEntidadId(pago.getId());
		r.setDetalle("Comanda " + pago.getComanda().getId() + ", " + pago.getMedio() + ", valor " + pago.getValor());
		auditoria.save(r);
	}

}
