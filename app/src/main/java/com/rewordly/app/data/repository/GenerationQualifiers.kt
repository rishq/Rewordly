package com.rewordly.app.data.repository

import javax.inject.Qualifier

/**
 * The project's own backend, which relays to an AI provider.
 *
 * Both generation paths implement the same interface, so the qualifiers are what keeps them apart at the
 * injection point and lets the delegating repository be exercised with fakes.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackendGeneration

/** The provider the user supplied their own API key for. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OwnProviderGeneration
