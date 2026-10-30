package com.bener.corebank;

import static org.assertj.core.api.Assertions.assertThat;

import com.bener.corebank.account.Account;
import com.bener.corebank.account.AccountRepository;
import com.bener.corebank.customer.Customer;
import com.bener.corebank.customer.CustomerRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class HibernateFetchingTest {
    @Autowired AccountRepository accounts;
    @Autowired CustomerRepository customers;
    @Autowired EntityManager entityManager;

    @Test
    @Transactional
    void entityGraphAvoidsNPlusOneCustomerQueries() {
        for (int index = 0; index < 3; index++) {
            Customer customer = customers.save(new Customer("Fetch example " + index));
            accounts.save(new Account(customer));
        }
        entityManager.flush();
        entityManager.clear();

        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        accounts.findAll().forEach(Account::getCustomerName);
        long lazyStatements = statistics.getPrepareStatementCount();

        entityManager.clear();
        statistics.clear();
        accounts.findAllWithCustomer().forEach(Account::getCustomerName);
        long graphStatements = statistics.getPrepareStatementCount();

        assertThat(lazyStatements).isGreaterThan(graphStatements);
        assertThat(graphStatements).isEqualTo(1);
    }
}
