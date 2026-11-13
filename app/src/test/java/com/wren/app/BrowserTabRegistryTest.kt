package com.wren.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserTabRegistryTest {
    private data class FakeTab(val url: String)

    @Test
    fun keepsEqualUrlTabsAsDistinctInstances() {
        val registry = BrowserTabRegistry<FakeTab>()
        val first = FakeTab("https://example.com")
        val second = FakeTab("https://example.com")

        assertTrue(registry.add(first))
        assertTrue(registry.add(second))
        assertEquals(2, registry.tabs.size)
        assertFalse(registry.add(first))
    }

    @Test
    fun equalButDistinctTabsRemainDistinctForSelectionAndRemoval() {
        val registry = BrowserTabRegistry<FakeTab>()
        val first = FakeTab("https://same.example")
        val second = FakeTab("https://same.example")

        assertTrue(first == second)
        assertTrue(registry.add(first))
        assertTrue(registry.add(second))
        assertTrue(registry.select(second))
        assertTrue(registry.activeTab === second)

        val selectedIndex = registry.tabs.indexOfFirst { it === registry.activeTab }
        assertEquals(1, selectedIndex)
        registry.removeAt(0)
        assertTrue(registry.activeTab === second)
    }

    @Test
    fun selectionRequiresMembershipAndRemovalClearsSelectedTab() {
        val registry = BrowserTabRegistry<FakeTab>()
        val first = FakeTab("https://one.example")
        val second = FakeTab("https://two.example")

        registry.add(first)
        assertFalse(registry.select(second))
        assertTrue(registry.select(first))
        assertEquals(first, registry.activeTab)

        assertEquals(first, registry.removeAt(0))
        assertNull(registry.activeTab)
    }

    @Test
    fun replaceAndClearResetSelectionWithoutChangingIdentityRules() {
        val registry = BrowserTabRegistry<FakeTab>()
        val first = FakeTab("https://one.example")
        val second = FakeTab("https://two.example")
        registry.add(first)
        registry.select(first)

        registry.replaceAll(listOf(second))
        assertEquals(listOf(second), registry.tabs)
        assertNull(registry.activeTab)

        registry.select(second)
        registry.clear()
        assertTrue(registry.tabs.isEmpty())
        assertNull(registry.activeTab)
    }
}
