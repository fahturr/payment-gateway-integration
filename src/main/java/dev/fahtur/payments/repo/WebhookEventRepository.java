package dev.fahtur.payments.repo;

import dev.fahtur.payments.domain.WebhookEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    boolean existsByEventKey(String eventKey);
}
