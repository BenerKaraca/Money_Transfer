package com.bener.corebank.transfer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    @Query("select count(t) from Transfer t where t.source.id = :id or t.destination.id = :id")
    long countForAccount(@Param("id") UUID id);
}
