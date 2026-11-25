package com.appresso.backend.tiemporeal;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/** Envía el RECEIPT solicitado después de que el broker simple registra el SUBSCRIBE. */
@Component
public class ConfirmacionSuscripcionStomp implements ExecutorChannelInterceptor {

	private final ObjectProvider<MessageChannel> salida;

	public ConfirmacionSuscripcionStomp(@Qualifier("clientOutboundChannel") ObjectProvider<MessageChannel> salida) {
		this.salida = salida;
	}

	@Override
	public void afterMessageHandled(Message<?> mensaje, MessageChannel canal, MessageHandler manejador,
			@Nullable Exception error) {
		if (error != null || !(manejador instanceof SimpleBrokerMessageHandler)) {
			return;
		}
		var suscripcion = MessageHeaderAccessor.getAccessor(mensaje, StompHeaderAccessor.class);
		if (suscripcion == null || suscripcion.getCommand() != StompCommand.SUBSCRIBE
				|| suscripcion.getReceipt() == null || suscripcion.getSessionId() == null
				|| suscripcion.getSubscriptionId() == null) {
			return;
		}
		var confirmacion = StompHeaderAccessor.create(StompCommand.RECEIPT);
		confirmacion.setSessionId(suscripcion.getSessionId());
		confirmacion.setReceiptId(suscripcion.getReceipt());
		salida.getObject().send(MessageBuilder.createMessage(new byte[0], confirmacion.getMessageHeaders()));
	}
}
