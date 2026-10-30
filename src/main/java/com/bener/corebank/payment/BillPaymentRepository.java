package com.bener.corebank.payment;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillPaymentRepository extends JpaRepository<BillPayment, UUID> {
    Optional<BillPayment> findByIdempotencyKey(String idempotencyKey);
    List<BillPayment> findTop50ByAccount_IdOrderByCreatedAtDesc(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BillPayment p where p.id = :id")
    Optional<BillPayment> findByIdForUpdate(@Param("id") UUID id);

    @Query("select p.id from BillPayment p where p.status in ('PENDING','UNKNOWN') order by p.updatedAt")
    List<UUID> findPendingIds(Pageable page);
}
