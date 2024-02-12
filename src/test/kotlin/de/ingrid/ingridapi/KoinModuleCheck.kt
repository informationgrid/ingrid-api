package de.ingrid.ingridapi

import kotlin.test.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest

class CheckModulesTest : KoinTest {

    //    private val api1Service by inject<Api1Service>()

    @Test
    fun koinTest() {
        stopKoin()
        startKoin {
            //            modules(appModule)
            //            assertEquals(3, api1Service.getList().size)
        }
    }
}
