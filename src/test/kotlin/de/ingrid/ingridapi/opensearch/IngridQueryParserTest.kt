package de.ingrid.ingridapi.opensearch

import de.ingrid.ingridapi.opensearch.service.DocumentIdSearch
import de.ingrid.ingridapi.opensearch.service.EsQueryBuilder
import de.ingrid.ingridapi.opensearch.service.FieldSearch
import de.ingrid.ingridapi.opensearch.service.IngridQueryParser
import de.ingrid.ingridapi.opensearch.service.SearchPhrase
import de.ingrid.ingridapi.opensearch.service.SearchTerm
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IngridQueryParserTest {
    @Test
    fun testPlainTermsAreJoinedWithAnd() {
        val parsed = IngridQueryParser.parse("boden wasser")
        assertEquals(1, parsed.groups.size)
        assertEquals(listOf(SearchTerm("boden"), SearchTerm("wasser")), parsed.groups[0].must)
        assertTrue(parsed.groups[0].mustNot.isEmpty())
    }

    @Test
    fun testQuotedPhrase() {
        val parsed = IngridQueryParser.parse("\"wind energy\"")
        assertEquals(1, parsed.groups.size)
        assertEquals(listOf(SearchPhrase("wind energy")), parsed.groups[0].must)
    }

    @Test
    fun testMetaFieldsAreIgnored() {
        val parsed = IngridQueryParser.parse("wasser datatype:default ranking:score")
        assertEquals(1, parsed.groups.size)
        assertEquals(listOf(SearchTerm("wasser")), parsed.groups[0].must)
    }

    @Test
    fun testFieldSearch() {
        val parsed = IngridQueryParser.parse("title:boden")
        assertEquals(listOf(FieldSearch("title", "boden")), parsed.groups[0].must)
    }

    @Test
    fun testExcludedTerm() {
        val parsed = IngridQueryParser.parse("boden -solar")
        assertEquals(listOf(SearchTerm("boden")), parsed.groups[0].must)
        assertEquals(listOf(SearchTerm("solar")), parsed.groups[0].mustNot)
    }

    @Test
    fun testNotOperator() {
        val parsed = IngridQueryParser.parse("boden NOT solar")
        assertEquals(listOf(SearchTerm("boden")), parsed.groups[0].must)
        assertEquals(listOf(SearchTerm("solar")), parsed.groups[0].mustNot)
    }

    @Test
    fun testOrOperatorSplitsGroups() {
        val parsed = IngridQueryParser.parse("boden OR wasser")
        assertEquals(2, parsed.groups.size)
        assertEquals(listOf(SearchTerm("boden")), parsed.groups[0].must)
        assertEquals(listOf(SearchTerm("wasser")), parsed.groups[1].must)
    }

    @Test
    fun testOrGroupingWithMultipleTerms() {
        // "a b OR c d" shall be interpreted as "(a AND b) OR (c AND d)"
        val parsed = IngridQueryParser.parse("a b OR c d")
        assertEquals(2, parsed.groups.size)
        assertEquals(listOf(SearchTerm("a"), SearchTerm("b")), parsed.groups[0].must)
        assertEquals(listOf(SearchTerm("c"), SearchTerm("d")), parsed.groups[1].must)
    }

    @Test
    fun testDocumentIdFields() {
        val parsed = IngridQueryParser.parse("t01_object.obj_id:abc-123")
        assertEquals(listOf(DocumentIdSearch("abc-123")), parsed.groups[0].must)
    }

    @Test
    fun testTokenizerKeepsQuotedPhrasesTogether() {
        val tokens = IngridQueryParser.tokenize("a \"b c\" d")
        assertEquals(listOf("a", "\"b c\"", "d"), tokens)
    }

    @Test
    fun testEmptyQueryMatchesAll() {
        val parsed = IngridQueryParser.parse("")
        assertTrue(parsed.groups.isEmpty())
        val query = EsQueryBuilder.build(parsed, 0, 10)
        assertTrue(query.toString().contains("match_all"))
    }

    @Test
    fun testQueryBuilderIncludesPaging() {
        val parsed = IngridQueryParser.parse("boden")
        val query = EsQueryBuilder.build(parsed, 10, 20).jsonObject
        assertEquals(10, query["from"]!!.jsonPrimitive.content.toInt())
        assertEquals(20, query["size"]!!.jsonPrimitive.content.toInt())
        val bool = query["query"]!!.jsonObject["bool"]!!.jsonObject
        val must = bool["must"]!!.jsonArray
        assertEquals(1, must.size)
        assertTrue(must[0].jsonObject.containsKey("multi_match"))
    }

    @Test
    fun testQueryBuilderBuildsOrGroups() {
        val parsed = IngridQueryParser.parse("a OR b")
        val query = EsQueryBuilder.build(parsed, 0, 10).jsonObject
        val bool = query["query"]!!.jsonObject["bool"]!!.jsonObject
        assertTrue(bool.containsKey("should"))
        assertEquals(1, bool["minimum_should_match"]!!.jsonPrimitive.content.toInt())
    }
}
