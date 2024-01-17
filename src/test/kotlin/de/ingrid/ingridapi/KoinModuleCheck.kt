package de.ingrid.ingridapi

import de.ingrid.ingridapi.api1.Api1Service
import org.koin.core.context.startKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import kotlin.test.Test
import kotlin.test.assertEquals

class CheckModulesTest : KoinTest {

    private val api1Service by inject<Api1Service>()

    @Test
    fun koinTest() {
        startKoin {
            modules(appModule)
            assertEquals(3, api1Service.getList().size)
        }
    }
}