package com.thropic.talki.scoring.infrastructure.messaging.consumer;

import com.thropic.talki.scoring.domain.event.SessionLiveFinalizedEvent;
import com.thropic.talki.scoring.domain.model.ScoreResult;
import com.thropic.talki.scoring.infrastructure.messaging.config.RabbitMQConfig;
import com.thropic.talki.scoring.infrastructure.persistence.ScoreResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Registra la práctica cerrada como PENDING para que el reporte pueda responder
 * "en proceso" antes de que llegue fillers.analyzed. Si ese análisis llegó primero,
 * no cambia nada.
 */
@Component
public class SessionFinalizedConsumer {

    private static final Logger log = LoggerFactory.getLogger(SessionFinalizedConsumer.class);

    private final ScoreResultRepository scoreResultRepository;

    public SessionFinalizedConsumer(ScoreResultRepository scoreResultRepository) {
        this.scoreResultRepository = scoreResultRepository;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_SESSIONS)
    public void handle(SessionLiveFinalizedEvent event) {
        String sessionId = event.getSessionId();
        String userId = event.getUserId();
        if (sessionId == null || sessionId.isBlank() || userId == null || userId.isBlank()) {
            throw new AmqpRejectAndDontRequeueException("session.live.finalized sin sessionId/userId");
        }
        if (scoreResultRepository.findBySessionId(sessionId).isPresent()) {
            return;
        }
        try {
            scoreResultRepository.save(ScoreResult.pending(sessionId, userId));
            log.info("[scoring-service] Análisis pendiente registrado — sessionId={}", sessionId);
        } catch (DataIntegrityViolationException alreadyRegistered) {
            log.debug("[scoring-service] sessionId={} ya registrado", sessionId);
        }
    }
}
