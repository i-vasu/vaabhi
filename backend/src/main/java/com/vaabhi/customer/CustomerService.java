package com.vaabhi.customer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The signed-in customer's own profile. */
@Service
class CustomerService {

    private final CustomerStore customers;

    CustomerService(CustomerStore customers) {
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public CustomerProfile profile(long customerId) {
        // A deleted account with a still-valid access token.
        return customers.find(customerId).orElseThrow(() -> AuthException.unauthorized("Please sign in again"));
    }

    @Transactional
    public CustomerProfile updateName(long customerId, String name) {
        customers.updateName(customerId, name.strip());
        return profile(customerId);
    }
}
