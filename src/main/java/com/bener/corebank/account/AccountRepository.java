package com.bener.corebank.account;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    @Override
    @EntityGraph(attributePaths = "customer")
    Optional<Account> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = "customer")
    Page<Account> findAllByOrderByCreatedAtDesc(Pageable pageable);
    @EntityGraph(attributePaths = "customer")
    Page<Account> findAllByCustomer_IdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);
    @EntityGraph(attributePaths = "customer")
    List<Account> findAllByCurrencyAndCustomer_IdNotOrderByCreatedAtDesc(String currency, UUID customerId);

    @EntityGraph(attributePaths = "customer")
    @Query("select a from Account a")
    List<Account> findAllWithCustomer();
}
