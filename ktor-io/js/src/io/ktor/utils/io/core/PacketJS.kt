package io.ktor.utils.io.core

import io.ktor.utils.io.core.internal.*

public actual val PACKET_MAX_COPY_SIZE: Int = 200

public actual typealias EOFException = io.ktor.utils.io.errors.EOFException
