package com.kenkern.engine.scene

import java.util.UUID

/**
 * Stable, unique identifier of a Nink.
 *
 * Display names are never unique and must never be used as identity.
 * The raw value is a UUID string, which also serializes safely to JSON.
 */
@JvmInline
value class NinkId(val value: String) {

    override fun toString(): String = value

    companion object {
        fun generate(): NinkId = NinkId(UUID.randomUUID().toString())

        /** Creates an id from serialized data; invalid input yields null. */
        fun from(raw: String?): NinkId? = raw?.takeIf { it.isNotBlank() }?.let { NinkId(it) }
    }
}
