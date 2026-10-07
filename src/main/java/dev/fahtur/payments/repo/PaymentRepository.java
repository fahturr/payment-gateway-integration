package dev.fahtur.payments.repo;

import dev.fahtur.payments.domain.Payment;
import dev.fahtur.payments.domain.PaymentStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByStatusInAndCreatedAtBefore(Collection<PaymentStatus> statuses,
                                                   Instant before,
                                                   Pageable pageable);
}
