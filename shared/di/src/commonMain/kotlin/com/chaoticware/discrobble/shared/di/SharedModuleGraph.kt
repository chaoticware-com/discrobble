package com.chaoticware.discrobble.shared.di

import com.chaoticware.discrobble.shared.auth.AuthModule
import com.chaoticware.discrobble.shared.catalog.CatalogModule
import com.chaoticware.discrobble.shared.domain.DomainModule
import com.chaoticware.discrobble.shared.network.NetworkModule
import com.chaoticware.discrobble.shared.persistence.PersistenceModule
import com.chaoticware.discrobble.shared.scrobble.ScrobbleModule
import com.chaoticware.discrobble.shared.session.SessionModule

object SharedModuleGraph {
    val moduleIds: List<String> = listOf(
        DomainModule.id,
        AuthModule.id,
        CatalogModule.id,
        SessionModule.id,
        ScrobbleModule.id,
        PersistenceModule.id,
        NetworkModule.id,
    )
}
