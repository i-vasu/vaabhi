package com.vaabhi.customer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The signed-in customer's own profile. */
@Service
public class CustomerService {

    private final CustomerStore customers;

    CustomerService(CustomerStore customers) {
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public CustomerProfile profile(long customerId) {
        // A deleted account with a still-valid access token.
        return customers.find(customerId).orElseThrow(() -> AuthException.unauthorized("Please sign in again"));
    }

    @Transactional(readOnly = true)
    public CustomerDetails details(long customerId) {
        return customers.details(customerId).orElseThrow(() -> AuthException.unauthorized("Please log in again"));
    }

    @Transactional
    public CustomerDetails updateDetails(long customerId, CustomerDetails details) {
        customers.updateDetails(customerId, details);
        return details(customerId);
    }

    @Transactional
    public CustomerProfile updateName(long customerId, String name) {
        customers.updateName(customerId, name.strip());
        return profile(customerId);
    }
}
