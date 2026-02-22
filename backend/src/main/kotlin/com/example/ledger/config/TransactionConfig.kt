package com.example.ledger.config

import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Transactional(isolation = Isolation.SERIALIZABLE)
annotation class SerializableTx
