package com.example.ledger.config

import jakarta.persistence.EntityManagerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.DefaultTransactionStatus
import java.sql.SQLException

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Transactional(isolation = Isolation.SERIALIZABLE)
annotation class SerializableTx

@Configuration
class TransactionConfig {
    companion object {
        private const val SERIALIZATION_FAILURE_SQLSTATE = "40001"
    }

    @Bean
    @Primary
    fun transactionManager(entityManagerFactory: EntityManagerFactory): JpaTransactionManager {
        return object : JpaTransactionManager(entityManagerFactory) {
            override fun doCommit(status: DefaultTransactionStatus) {
                try {
                    super.doCommit(status)
                } catch (ex: RuntimeException) {
                    if (isSerializationFailure(ex)) {
                        throw CannotSerializeTransactionException("Serialization failure on transaction commit", ex)
                    }
                    throw ex
                }
            }
        }
    }

    private fun isSerializationFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is SQLException && current.sqlState == SERIALIZATION_FAILURE_SQLSTATE) {
                return true
            }
            current = current.cause
        }
        return false
    }
}

class CannotSerializeTransactionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
