package com.wren.app

/**
 * Main-thread-confined owner of the open-tab collection and selected tab.
 *
 * Identity is intentional: two tabs may point at the same URL and must remain distinct.
 * The registry owns membership/selection only; GeckoSession lifecycle stays with the
 * browser coordinator until that responsibility is extracted in a later step.
 */
internal class BrowserTabRegistry<T : Any> {
    private val mutableTabs = mutableListOf<T>()

    val tabs: List<T>
        get() = mutableTabs.toList()

    var activeTab: T? = null
        private set

    fun add(tab: T): Boolean {
        if (mutableTabs.any { it === tab }) return false
        mutableTabs.add(tab)
        return true
    }

    fun addAll(tabs: Iterable<T>) {
        tabs.forEach(::add)
    }

    fun removeAt(index: Int): T {
        val removed = mutableTabs.removeAt(index)
        if (activeTab === removed) activeTab = null
        return removed
    }

    fun select(tab: T?): Boolean {
        if (tab != null && mutableTabs.none { it === tab }) return false
        activeTab = tab
        return true
    }

    fun replaceAll(tabs: Iterable<T>) {
        mutableTabs.clear()
        activeTab = null
        addAll(tabs)
    }

    fun clear() {
        mutableTabs.clear()
        activeTab = null
    }
}
