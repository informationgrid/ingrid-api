package de.ingrid.ingridapi.ogc.records.items

import kotlin.test.Test
import kotlin.test.assertEquals

class PagingUtilsTest {
    @Test
    fun testCreatePagingLinksWithFParamInBaseUrl() {
        val baseUrl = "http://localhost/items?f=html"
        val total = 100L
        val limit = 10
        val offset = 0
        val format = "html"
        
        val links = createPagingLinks(baseUrl, total, limit, offset, format)
        
        val nextLink = links.find { it.rel == "next" }
        // Should handle existing query parameters correctly and avoid duplicates
        assertEquals("http://localhost/items?f=html&limit=10&offset=10", nextLink?.href)
    }

    @Test
    fun testCreatePagingLinksWithoutQueryInBaseUrl() {
        val baseUrl = "http://localhost/items"
        val total = 100L
        val limit = 10
        val offset = 0
        val format = "html"
        
        val links = createPagingLinks(baseUrl, total, limit, offset, format)
        
        val nextLink = links.find { it.rel == "next" }
        assertEquals("http://localhost/items?limit=10&offset=10&f=html", nextLink?.href)
    }

    @Test
    fun testCreatePagingLinksWithRelativeUrl() {
        val baseUrl = "/ogc/records/collections/test/items?f=html"
        val total = 100L
        val limit = 10
        val offset = 0
        val format = "html"
        
        val links = createPagingLinks(baseUrl, total, limit, offset, format)
        
        val nextLink = links.find { it.rel == "next" }
        assertEquals("/ogc/records/collections/test/items?f=html&limit=10&offset=10", nextLink?.href)
    }
}
