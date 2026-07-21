package de.ingrid.ingridapi.core.services

import de.ingrid.ingridapi.config.AppConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ElasticsearchServiceTest {

    @Test
    fun `getActiveIndices should filter indices by prefix`() = runBlocking {
        val config = mockk<AppConfig>()
        every { config.elasticHost } returns "localhost"
        every { config.elasticPort } returns 9200
        every { config.elasticHttps } returns false
        every { config.elasticUsername } returns ""
        every { config.elasticPassword } returns ""
        every { config.indexPrefix } returns "pre_"

        val service = spyk(ElasticsearchService(config))

        val catalog1 = JsonObject(mapOf("linkedIndex" to JsonPrimitive("pre_index1")))
        val catalog2 = JsonObject(mapOf("linkedIndex" to JsonPrimitive("other_index")))

        coEvery { service.getActiveCatalogs() } returns listOf(catalog1, catalog2)

        val activeIndices = service.getActiveIndices()

        assertEquals(1, activeIndices.size)
        assertEquals("pre_index1", activeIndices[0])
        assertEquals("ingrid_meta", service.metaIndexName)
    }

    @Test
    fun `metaIndexName should not be prefixed if prefix is set`() = runBlocking {
        val config = mockk<AppConfig>()
        every { config.elasticHost } returns "localhost"
        every { config.elasticPort } returns 9200
        every { config.elasticHttps } returns false
        every { config.elasticUsername } returns ""
        every { config.elasticPassword } returns ""
        every { config.indexPrefix } returns "test_"

        val service = ElasticsearchService(config)
        assertEquals("ingrid_meta", service.metaIndexName)
    }

    @Test
    fun `getActiveIndices should return all indices if no prefix is set`() = runBlocking {
        val config = mockk<AppConfig>()
        every { config.elasticHost } returns "localhost"
        every { config.elasticPort } returns 9200
        every { config.elasticHttps } returns false
        every { config.elasticUsername } returns ""
        every { config.elasticPassword } returns ""
        every { config.indexPrefix } returns ""

        val service = spyk(ElasticsearchService(config))

        val catalog1 = JsonObject(mapOf("linkedIndex" to JsonPrimitive("pre_index1")))
        val catalog2 = JsonObject(mapOf("linkedIndex" to JsonPrimitive("other_index")))

        coEvery { service.getActiveCatalogs() } returns listOf(catalog1, catalog2)

        val activeIndices = service.getActiveIndices()

        assertEquals(2, activeIndices.size)
        assertEquals("pre_index1", activeIndices[0])
        assertEquals("other_index", activeIndices[1])
    }

    @Test
    fun `getIndicesForDataSource should filter indices by prefix`() = runBlocking {
        val config = mockk<AppConfig>()
        every { config.elasticHost } returns "localhost"
        every { config.elasticPort } returns 9200
        every { config.elasticHttps } returns false
        every { config.elasticUsername } returns ""
        every { config.elasticPassword } returns ""
        every { config.indexPrefix } returns "pre_"

        val service = spyk(ElasticsearchService(config))

        val catalog1 = buildJsonObject {
            put("linkedIndex", JsonPrimitive("pre_index1"))
            put("plugdescription", buildJsonObject {
                put("dataSourceName", JsonPrimitive("ds1"))
            })
        }
        val catalog2 = buildJsonObject {
            put("linkedIndex", JsonPrimitive("other_index"))
            put("plugdescription", buildJsonObject {
                put("dataSourceName", JsonPrimitive("ds1"))
            })
        }

        coEvery { service.getActiveCatalogs() } returns listOf(catalog1, catalog2)

        val indices = service.getIndicesForDataSource("ds1")

        assertEquals("pre_index1", indices)
    }

    @Test
    fun `getIndicesForDataSource should return null if no index matches prefix`() = runBlocking {
        val config = mockk<AppConfig>()
        every { config.elasticHost } returns "localhost"
        every { config.elasticPort } returns 9200
        every { config.elasticHttps } returns false
        every { config.elasticUsername } returns ""
        every { config.elasticPassword } returns ""
        every { config.indexPrefix } returns "pre_"

        val service = spyk(ElasticsearchService(config))

        val catalog = buildJsonObject {
            put("linkedIndex", JsonPrimitive("other_index"))
            put("plugdescription", buildJsonObject {
                put("dataSourceName", JsonPrimitive("ds1"))
            })
        }

        coEvery { service.getActiveCatalogs() } returns listOf(catalog)

        val indices = service.getIndicesForDataSource("ds1")

        assertNull(indices)
    }
}
