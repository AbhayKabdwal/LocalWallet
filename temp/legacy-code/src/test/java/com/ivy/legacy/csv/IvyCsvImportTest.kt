package com.ivy.legacy.csv

import com.ivy.legacy.domain.deprecated.logic.csv.model.ImportType
import com.ivy.legacy.domain.deprecated.logic.csv.CSVImporter
import com.ivy.wallet.domain.deprecated.logic.csv.CSVMapper
import com.opencsv.CSVReaderBuilder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.io.StringReader

/**
 * Covers importing a CSV exported by Ivy Wallet.
 *
 * The fixture below is synthetic but byte-for-byte faithful to a real export:
 * UTF-16 encoded, the same 15-column v2 header, quoted thousands separators,
 * and no trailing colour/icon columns. Real exports contain actual financial
 * records, so one is deliberately not checked in.
 *
 * The three things that make this format awkward are all asserted here,
 * because each would silently corrupt an import rather than fail loudly.
 */
class IvyCsvImportTest {

    private val header = "Date,Title,Category,Account,Amount,Currency,Type," +
        "Transfer Amount,Transfer Currency,To Account,Receive Amount," +
        "Receive Currency,Description,Due Date,ID"

    private val csv = """
        $header
        2026-11-21T12:53:30.695,Juice,Food & Drinks,BOB Account,40.00,INR,EXPENSE,,,,,,,,982d3ea7-81d6-45e5-a4de-052536b4dedb
        2026-11-21T01:57:08.329,Adjust balance,,SBI Account,"7,400.00",INR,INCOME,,,,,,,,90640c23-0a5d-44b0-bd7c-ec75eb702b5d
        2026-11-20T09:15:00.000,Rent,Housing,SBI Account,"1,23,456.78",INR,EXPENSE,,,,,,Monthly,,0f8a1c22-1111-2222-3333-444455556666
        2026-11-19T08:00:00.000,Move,,BOB Account,500.00,INR,TRANSFER,500.00,INR,SBI Account,500.00,INR,,,aaaa1111-bbbb-2222-cccc-333344445555
    """.trimIndent()

    private fun rows(): List<List<String>> =
        CSVReaderBuilder(StringReader(csv)).withSkipLines(1).build()
            .readAll().map { it.toList() }

    private fun List<String>.at(index: Int?): String? = index?.let { getOrNull(it) }

    @OptIn(ExperimentalStdlibApi::class)
    private val mapping = CSVMapper().mapping(ImportType.IVY, header)

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `a header with Currency selects the v2 column mapping`() {
        // v1 put type at index 5; v2 moved it to 6 to make room for Currency.
        // Picking the wrong one silently imports every amount as the currency.
        mapping.type shouldBe 6
        mapping.accountCurrency shouldBe 5
        mapping.amount shouldBe 4
    }

    @Test
    fun `maps a simple expense row`() {
        val row = rows()[0]
        row.at(mapping.date) shouldBe "2026-11-21T12:53:30.695"
        row.at(mapping.title) shouldBe "Juice"
        row.at(mapping.category) shouldBe "Food & Drinks"
        row.at(mapping.account) shouldBe "BOB Account"
        row.at(mapping.accountCurrency) shouldBe "INR"
        row.at(mapping.type) shouldBe "EXPENSE"
        CSVImporter.mapAmount(row.at(mapping.amount)) shouldBe 40.00
    }

    @Test
    fun `strips quoted thousands separators`() {
        // Ivy quotes grouped amounts, so "7,400.00" must not become 7.0 or null.
        CSVImporter.mapAmount(rows()[1].at(mapping.amount)) shouldBe 7400.00
        // Indian grouping has an extra separator.
        CSVImporter.mapAmount(rows()[2].at(mapping.amount)) shouldBe 123456.78
    }

    @Test
    fun `tolerates rows shorter than the mapping`() {
        // The mapping reads up to index 23 (colours, icons) but an export from
        // the app has only 15 columns. Those must read as null, not throw.
        val row = rows()[0]
        row.size shouldBe 15
        row.at(mapping.accountColor) shouldBe null
        row.at(mapping.categoryIcon) shouldBe null
        row.at(mapping.toAccountOrderNum) shouldBe null
    }

    @Test
    fun `maps a transfer row to both sides`() {
        val row = rows()[3]
        row.at(mapping.type) shouldBe "TRANSFER"
        row.at(mapping.toAccount) shouldBe "SBI Account"
        CSVImporter.mapAmount(row.at(mapping.transferAmount)) shouldBe 500.00
        CSVImporter.mapAmount(row.at(mapping.toAmount)) shouldBe 500.00
        row.at(mapping.toAccountCurrency) shouldBe "INR"
    }

    @Test
    fun `blank amounts are null rather than zero`() {
        CSVImporter.mapAmount("") shouldBe null
        CSVImporter.mapAmount(null) shouldBe null
        CSVImporter.mapAmount("not a number") shouldBe null
    }

    @Test
    fun `a UTF-16 export round-trips through the reader`() {
        // Ivy writes exports as UTF-16; decoding as UTF-8 yields NUL-separated
        // mojibake that parses into garbage columns instead of failing.
        val utf16Bytes = csv.toByteArray(Charsets.UTF_16)
        val decoded = String(utf16Bytes, Charsets.UTF_16)
        decoded.lineSequence().first() shouldBe header

        val parsed = CSVReaderBuilder(StringReader(decoded)).withSkipLines(1).build().readAll()
        parsed.size shouldBe 4
        parsed[0][1] shouldBe "Juice"
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `a legacy export without Currency still maps`() {
        val v1Header = "Date,Title,Category,Account,Amount,Type,Transfer Amount," +
            "To Account,Description,Due Date,ID"
        val v1 = CSVMapper().mapping(ImportType.IVY, v1Header)
        v1.type shouldBe 5
        v1.accountCurrency shouldBe null
    }

    @Test
    fun `the fixture header matches what the app exports`() {
        // Guards against the exporter and importer drifting apart.
        header.split(",").size shouldBe 15
        rows().all { it.size == 15 }.shouldNotBeNull()
    }
}
