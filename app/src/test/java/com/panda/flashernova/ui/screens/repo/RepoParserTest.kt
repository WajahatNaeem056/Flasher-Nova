package com.panda.flashernova.ui.screens.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the parsing rules of the two cloud G(O)KI repository endpoints.
 *
 * All sample data comes from real online responses (the product branch list and community JSON captured on 2026-11-15),
 * covering the three MIXKI file name forms (with GKI_ prefix / with MTK_ prefix / neither) and OORB model packages.
 */
class RepoParserTest {

    private val rawPrefix = RepoParser.RAW_PREFIX

    private val officialPaths = listOf(
        "AnyKernel3_Official/MIXKI/6.1.x/KSU-Next/AK3_KSUNext_6.1.118_33548_NOSUSFS_KPN_LZ4_DS_IN_SSG_BBG.zip",
        "AnyKernel3_Official/MIXKI/6.1.x/KSU-Next/AK3_KSUNext_6.1.57_33548_NOSUSFS_KPN_LZ4_DS_IN_SSG_BBG.zip",
        "AnyKernel3_Official/MIXKI/6.1.x/KSU-Next/AK3_MTK_KSUNext_6.1.115_33548_NOSUSFS_KPN_LZ4_DS_IN_SSG_BBG.zip",
        "AnyKernel3_Official/MIXKI/6.6.x/BakaSU/AK3_BAKASU_6.6.30_35259_SUSFS_KPN_LZ4_DS_IN_ADIOS_BBG.zip",
        "AnyKernel3_Official/MIXKI/6.6.x/BakaSU/AK3_BAKASU_6.6.118_35259_SUSFS_KPN_LZ4_DS_IN_ADIOS_BBG.zip",
        "AnyKernel3_Official/MIXKI/6.6.x/BakaSU/AK3_MTK_BAKASU_6.6.89_35259_SUSFS_KPN_LZ4_DS_IN_ADIOS_BBG.zip",
        "AnyKernel3_Official/MIXKI/6.12.x/BakaSU/AK3_GKI_BAKASU_6.12.69_35259_SUSFS_KPN_LZ4_DS_IN_ADIOS_BBG.zip",
        "AnyKernel3_Official/OORB/BakaSU/AK3_BAKASU_ONEP13T_35259_SUSFS_KPM_BBG_IN_FIX_HMBRID_DS.zip",
        "AnyKernel3_Official/OORB/BakaSU/AK3_BAKASU_ONEPACE5PRO_35259_SUSFS_KPM_BBG_IN_FIX_DS.zip",
        "AnyKernel3_Official/OORB/SukiSU/AK3_SUKISU_ONEP13R_41179_SUSFS_KPM_BBG_IN_FIX_DS.zip",
        "AnyKernel3_Official/OORB/SukiSU/AK3_SUKISU_ONEPNORD3_41179_SUSFS_KPM_BBG_IN_FIX_DS.zip",
    )

    private val officialSample = officialPaths.joinToString("\n") { rawPrefix + it }

    private val communitySample = """
        [
          {
            "id": 18,
            "filename": "AK3_OKI-6.12.58_Bacillusf_None_0000_DroidSpace-UFW-Support-NTsync.zip",
            "device": "OKI-6.12.58",
            "manager": "None",
            "manager_version": "0000",
            "features": ["DroidSpace", "UFW-Support", "NTsync"],
            "size": 20386655,
            "size_human": "19.4 MB",
            "sha256": "dcc92b40b7f0466d179bd1d7be47fe3b24cc8659392d11710d932bf34af68aa5",
            "author": {
              "login": "Bacillusf",
              "url": "https://github.com/Bacillusf",
              "avatar": "https://avatars.githubusercontent.com/u/266071904?v=4"
            },
            "download_url": "https://raw.githubusercontent.com/Bacillusf/RKF-Resource-Repo/product/AnyKernel3_community/AK3_OKI-6.12.58_Bacillusf_None_0000_DroidSpace-UFW-Support-NTsync.zip",
            "github_url": "https://github.com/Bacillusf/RKF-Resource-Repo/blob/product/AnyKernel3_community/AK3_OKI-6.12.58_Bacillusf_None_0000_DroidSpace-UFW-Support-NTsync.zip",
            "notes": "Only for OPPO/Realme/OnePlus models with kernel version 6.12.58",
            "created_at": "2026-11-14T16:48:26+08:00",
            "published_at": "2026-11-14T16:48:35+08:00"
          },
          {
            "id": 13,
            "filename": "AK3_OKI-6.6.66_Bacillusf_None_0000_UFW-Support-NTsync-DroidSpace.zip",
            "device": "OKI-6.6.66",
            "manager": "BakaSU",
            "manager_version": "35259",
            "features": [],
            "size_human": "17.8 MB",
            "sha256": "aa",
            "author": {"login": "someone", "url": "https://github.com/someone"},
            "download_url": "https://raw.githubusercontent.com/Bacillusf/RKF-Resource-Repo/product/AnyKernel3_community/example.zip",
            "github_url": "https://github.com/Bacillusf/RKF-Resource-Repo/blob/product/AnyKernel3_community/example.zip",
            "notes": "",
            "created_at": "2026-11-14T16:38:43+08:00",
            "published_at": "2026-11-14T16:38:43+08:00"
          }
        ]
    """.trimIndent()

    @Test
    fun officialSplitsUniversalAndOnePlusByPath() {
        val items = RepoParser.parseOfficial(officialSample)
        assertEquals(11, items.size)
        assertEquals(7, items.count { it.kind == RepoKind.UNIVERSAL })
        assertEquals(4, items.count { it.kind == RepoKind.ONEPLUS })
        assertTrue(items.all { it.source == RepoSource.OFFICIAL })
        assertTrue(items.all { it.url.startsWith(rawPrefix) })
    }

    @Test
    fun officialUniversalKeepsSeriesAndKernelVersion() {
        val items = RepoParser.parseOfficial(officialSample)
        val kernel = items.first { it.filename.contains("BAKASU_6.6.118") }
        assertEquals(RepoKind.UNIVERSAL, kernel.kind)
        assertEquals("6.6.x", kernel.kernelSeries)
        assertEquals("6.6.118", kernel.kernelVersion)
        assertEquals("BakaSU", kernel.manager)
        assertEquals("35259", kernel.managerVer)
        assertNull(kernel.deviceModel)
        // manager:version is the first red capsule on the card
        assertEquals("BakaSU:35259", kernel.managerVersion)
        // The second capsule of a universal kernel flash package shows the kernel version
        assertEquals("Kernel 6.6.118", kernel.line2)
    }

    @Test
    fun officialPlatformPrefixesAreNotTreatedAsFeatures() {
        val items = RepoParser.parseOfficial(officialSample)
        // MTK / GKI prefixes are not parsed as a platform and do not enter the feature chips (requirement: the platform is not distinguished)
        for (item in items) {
            assertFalse(item.filename.contains("MTK_") && item.features.contains("MTK"))
            assertFalse(item.features.contains("GKI"))
            assertFalse(item.features.contains("AK3"))
        }
        val mtk = items.first { it.filename.startsWith("AK3_MTK_KSUNext_6.1.115") }
        assertEquals("KSU-Next", mtk.manager)
        assertEquals("33548", mtk.managerVer)
        assertEquals("6.1.115", mtk.kernelVersion)
        assertEquals(listOf("NOSUSFS", "KPN", "LZ4", "SSG", "DS", "IN", "BBG"), mtk.features)
    }

    @Test
    fun officialOnePlusModelsAreHumanised() {
        val items = RepoParser.parseOfficial(officialSample)
        val onep = items.first { it.filename.contains("ONEP13T") }
        assertEquals(RepoKind.ONEPLUS, onep.kind)
        assertEquals("OnePlus 13T", onep.deviceModel)
        assertEquals("OnePlus 13T", onep.line2)
        // OORB has no kernel version and no kernel series
        assertNull(onep.kernelVersion)
        assertNull(onep.kernelSeries)
        assertEquals(
            "OnePlus Ace 5 Pro",
            items.first { it.filename.contains("ONEPACE5PRO") }.deviceModel
        )
        assertEquals(
            "OnePlus Nord 3",
            items.first { it.filename.contains("ONEPNORD3") }.deviceModel
        )
    }

    @Test
    fun commonFeaturesAreSortedToTheEnd() {
        val items = RepoParser.parseOfficial(officialSample)
        val onep = items.first { it.filename.contains("ONEP13T") }
        assertEquals(
            listOf("SUSFS", "KPM", "HMBRID", "FIX", "DS", "IN", "BBG"),
            onep.features
        )
    }

    @Test
    fun latestIsOnePerSeriesAndManager() {
        val items = RepoParser.parseOfficial(officialSample)
        RepoParser.markLatest(items)
        val latest = items.filter { it.isLatest }
        // (6.1.x, KSU-Next) → 6.1.118 ; (6.6.x, BakaSU) → 6.6.118 ; (6.12.x, BakaSU) → 6.12.69
        assertEquals(3, latest.size)
        assertTrue(latest.all { it.kind == RepoKind.UNIVERSAL })
        assertEquals(
            setOf("6.1.118", "6.6.118", "6.12.69"),
            latest.mapNotNull { it.kernelVersion }.toSet()
        )
        // OnePlus model packages are never marked latest
        assertTrue(items.filter { it.kind == RepoKind.ONEPLUS }.none { it.isLatest })
    }

    @Test
    fun communityParsesStructuredFields() {
        val items = RepoParser.parseCommunity(communitySample)
        assertEquals(2, items.size)
        val first = items[0]
        assertEquals(RepoSource.COMMUNITY, first.source)
        assertEquals(RepoKind.COMMUNITY, first.kind)
        assertEquals("OKI-6.12.58", first.deviceModel)
        assertEquals("6.12.58", first.kernelVersion)
        // manager = "None" -> treated as no manager; the card shows "No manager"
        assertNull(first.manager)
        assertEquals("No manager", first.managerVersion)
        assertEquals("19.4 MB", first.sizeHuman)
        assertEquals(
            "dcc92b40b7f0466d179bd1d7be47fe3b24cc8659392d11710d932bf34af68aa5",
            first.sha256
        )
        assertEquals("Bacillusf", first.authorLogin)
        assertEquals("https://github.com/Bacillusf", first.authorUrl)
        assertTrue(first.notes!!.startsWith("Only for OPPO/Realme/OnePlus"))
        assertEquals("2026-11-14T16:48:35+08:00", first.publishedAt)
        assertNotNull(first.githubUrl)
        assertEquals(
            listOf("DroidSpace", "UFW-Support", "NTsync"),
            first.features
        )
        // The second package has a real manager
        assertEquals("BakaSU", items[1].manager)
        assertEquals("BakaSU:35259", items[1].managerVersion)
    }

    @Test
    fun searchIndexCoversEveryVisibleField() {
        val community = RepoParser.parseCommunity(communitySample)[0]
        for (needle in listOf(
            "oki-6.12.58", "bacillusf", "droidspace", "6.12.58", "community source", "community", "only for oppo"
        )) {
            assertTrue("searchBlob is missing $needle", community.searchBlob.contains(needle))
        }
        val onep = RepoParser.parseOfficial(officialSample).first { it.filename.contains("ONEP13T") }
        for (needle in listOf("oneplus 13t", "bakasu", "35259", "hmbrid", "official source", "universal kernel")) {
            if (needle == "universal kernel") continue
            assertTrue("searchBlob is missing $needle", onep.searchBlob.contains(needle))
        }
    }

    @Test
    fun prettyModelCoversEveryCodeSpottedOnline() {
        assertEquals("OnePlus 13", RepoParser.prettyOnePlusModel("13"))
        assertEquals("OnePlus 13T", RepoParser.prettyOnePlusModel("13T"))
        assertEquals("OnePlus 11R", RepoParser.prettyOnePlusModel("11R"))
        assertEquals("OnePlus 10 Pro", RepoParser.prettyOnePlusModel("10PRO"))
        assertEquals("OnePlus Ace 5 Pro", RepoParser.prettyOnePlusModel("ACE5PRO"))
        assertEquals("OnePlus Ace 5 Race", RepoParser.prettyOnePlusModel("ACE5RACE"))
        assertEquals("OnePlus Ace 3V", RepoParser.prettyOnePlusModel("ACE3V"))
        assertEquals("OnePlus Ace Race V", RepoParser.prettyOnePlusModel("ACERACEV"))
        assertEquals("OnePlus Nord CE 4 Lite 5G", RepoParser.prettyOnePlusModel("NORDCE4LITE5G"))
        assertEquals("OnePlus Nord CE 6", RepoParser.prettyOnePlusModel("NORDCE6"))
        assertEquals("OnePlus Nord N30 SE 5G V", RepoParser.prettyOnePlusModel("NORDN30SE5GV"))
        assertEquals("OnePlus Pad 2 Pro", RepoParser.prettyOnePlusModel("PAD2PRO"))
        assertEquals("OnePlus Pad 2 MT6991", RepoParser.prettyOnePlusModel("PAD2MT6991"))
        assertEquals("OnePlus Pad Go 2", RepoParser.prettyOnePlusModel("PADGO2"))
        assertEquals("OnePlus Turbo 6X", RepoParser.prettyOnePlusModel("TURBO6X"))
        assertEquals("OnePlus Open", RepoParser.prettyOnePlusModel("OPEN"))
    }

    @Test
    fun ranksOrderSeriesAndVersionsNumerically() {
        assertTrue(RepoParser.seriesRank("6.12.x") > RepoParser.seriesRank("6.6.x"))
        assertTrue(RepoParser.seriesRank("6.6.x") > RepoParser.seriesRank("6.1.x"))
        assertTrue(RepoParser.kernelRank("6.6.118") > RepoParser.kernelRank("6.6.89"))
        assertTrue(RepoParser.kernelRank("6.12.23") > RepoParser.kernelRank("6.6.118"))
        assertEquals(-1, RepoParser.kernelRank(null))
        assertEquals(-1, RepoParser.seriesRank(null))
    }

    @Test
    fun unparsableLinesAreSkippedInsteadOfCrashing() {
        val messy = """
            $rawPrefix AnyKernel3_Official/MIXKI/6.6.x/BakaSU/AK3_BAKASU_6.6.89_35259_SUSFS_BBG.zip

            not-a-url
            https://example.com/foo.zip
            $rawPrefix AnyKernel3_Official/UNKNOWN/6.6.x/BakaSU/x.zip
            $rawPrefix AnyKernel3_Official/OORB/BakaSU/readme.txt
        """.trimIndent().replace("$rawPrefix ", rawPrefix)
        val items = RepoParser.parseOfficial(messy)
        assertEquals(1, items.size)
        assertEquals("6.6.x", items[0].kernelSeries)
    }

    @Test
    fun featureLabelsExpandTheThreeOfficialAbbreviations() {
        // Official source: IN -> BetterNet / DS -> DroidSpace / LZ4 -> Lz4-Zarm, other tokens unchanged
        assertEquals("BetterNet", RepoParser.featureLabel("IN"))
        assertEquals("DroidSpace", RepoParser.featureLabel("DS"))
        assertEquals("Lz4-Zarm", RepoParser.featureLabel("LZ4"))
        assertEquals("SUSFS", RepoParser.featureLabel("SUSFS"))
        assertEquals("NOSUSFS", RepoParser.featureLabel("NOSUSFS"))
        assertEquals("SSG", RepoParser.featureLabel("SSG"))
        // Community source names are already readable; running them through the mapping changes nothing
        assertEquals("DroidSpace", RepoParser.featureLabel("DroidSpace"))
        assertEquals("UFW-Support", RepoParser.featureLabel("UFW-Support"))

        val item = RepoParser.parseOfficial(officialSample).first { it.filename.contains("AK3_BAKASU_6.6.118") }
        assertEquals(
            listOf("SUSFS", "KPN", "Lz4-Zarm", "ADIOS", "DroidSpace", "BetterNet", "BBG"),
            item.featureLabels
        )
    }

    @Test
    fun searchIndexAcceptsBothAbbreviationAndLabel() {
        val item = RepoParser.parseOfficial(officialSample).first { it.filename.contains("AK3_BAKASU_6.6.118") }
        // Both abbreviations and display names must be searchable
        assertTrue(item.searchBlob.contains(" betternet"))
        assertTrue(item.searchBlob.contains(" droidspace"))
        assertTrue(item.searchBlob.contains(" lz4-zarm"))
        assertTrue(item.searchBlob.contains(" in "))
    }

    @Test
    fun communityInfoCarriesEverythingTheDownloadGateNeeds() {
        // The pre-download dialog must show: model / manager / features / size / uploader / hash / notes / time
        val item = RepoParser.parseCommunity(communitySample)[0]
        assertNotNull(item.deviceModel)
        assertNotNull(item.featureLabels)
        assertNotNull(item.sizeHuman)
        assertNotNull(item.authorLogin)
        assertNotNull(item.sha256)
        assertNotNull(item.notes)
        assertNotNull(item.publishedAt)
        assertEquals(listOf("DroidSpace", "UFW-Support", "NTsync"), item.featureLabels)
    }
}
