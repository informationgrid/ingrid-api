package de.ingrid.ingridapi

import de.ingrid.ingridapi.plugins.appModule
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.KoinTest
import org.koin.test.verify.verify
import kotlin.test.Test

class KoinModuleCheck : KoinTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun koinTest() {
        appModule.verify()
    }
}
