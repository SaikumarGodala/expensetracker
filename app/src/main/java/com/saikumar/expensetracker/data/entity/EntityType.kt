package com.saikumar.expensetracker.data.entity

/**
 * Inferred type of the counterparty/entity involved in the transaction.
 * Critical for distinguishing P2P Transfers (Person) from Spending (Business).
 */
enum class EntityType {
    /**
     * A commercial entity (restaurant, shop, transport, utility, etc.).
     * Transactions to BUSINESS are typically EXPENSES.
     */
    BUSINESS,

    /**
     * A private individual.
     * Transactions to PERSON are typically TRANSFERS (P2P).
     */
    PERSON,

    /**
     * Could not determine if entity is business or person.
     * Requires fallback confidence logic.
     */
    UNKNOWN
}
