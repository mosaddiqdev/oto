package com.oto.stats.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fails the build if the database version, the committed schemas and the migrations drift apart. */
class SchemaGuardTest {

    private val dir = File("schemas/com.oto.stats.data.OtoDatabase")

    private fun versions(): List<Int> =
        dir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { it.nameWithoutExtension.toIntOrNull() }.sorted()

    @Test
    fun everyVersionHasACommittedSchema() {
        assertEquals((1..OTO_DB_VERSION).toList(), versions())
    }

    @Test
    fun migrationsFormAnUnbrokenChainToTheCurrentVersion() {
        val steps = Migrations.ALL.sortedBy { it.startVersion }
        assertEquals("Need one migration per version step", OTO_DB_VERSION - 1, steps.size)
        steps.forEachIndexed { i, m ->
            assertEquals(i + 1, m.startVersion)
            assertEquals(i + 2, m.endVersion)
        }
    }

    @Test
    fun schemaFilesDescribeTheirOwnVersion() {
        versions().forEach { v ->
            val text = File(dir, "$v.json").readText()
            assertTrue("$v.json has the wrong version", Regex("\"version\":\\s*$v\\b").containsMatchIn(text))
        }
    }
}
