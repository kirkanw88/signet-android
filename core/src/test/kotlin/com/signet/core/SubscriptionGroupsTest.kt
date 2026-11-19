package com.signet.core

import org.junit.Test
import kotlin.test.*

class SubscriptionGroupsTest {
    @Test fun sameServerInDifferentSubscriptionsHasIndependentIdentity(){
        val parsed=Profiles.parseJson("""{"remarks":"same host","outbounds":[{"protocol":"vless"}]}""")
        val a=ProfileRefresh.reconcile("source-a",emptyList(),parsed)
        val b=ProfileRefresh.reconcile("source-b",emptyList(),parsed)
        assertNotEquals(a.single().id,b.single().id)
        assertEquals(b.single().id,ProfileRefresh.selection(b.single().id,a+b,false))
        assertNull(ProfileRefresh.selection(a.single().id,b,false))
    }
    @Test fun foldsAreIndependentAndStaleIdsArePruned(){
        val ids=setOf("a","b")
        val first=SubscriptionGroups.toggle(setOf("deleted"),"a",ids)
        assertEquals(setOf("a"),first)
        val second=SubscriptionGroups.toggle(first,"b",ids)
        assertEquals(setOf("a","b"),second)
        assertEquals(setOf("b"),SubscriptionGroups.toggle(second,"a",ids))
    }
    @Test fun pinnedGroupsMoveToTopWithoutChangingOrderOrFoldState(){
        val groups=listOf("a","b","c","d")
        val pinned=SubscriptionGroups.toggle(setOf("deleted"),"c",groups.toSet())
        assertEquals(listOf("c","a","b","d"),SubscriptionGroups.pinnedFirst(groups,pinned){it})
        assertEquals(listOf("b","c","a","d"),SubscriptionGroups.pinnedFirst(groups,pinned+"b"){it})
        val restored=SubscriptionGroups.toggle(pinned,"c",groups.toSet())
        assertEquals(groups,SubscriptionGroups.pinnedFirst(groups,restored){it})
        assertEquals(setOf("a"),SubscriptionGroups.toggle(emptySet(),"a",groups.toSet()))
    }
    @Test fun refreshKeepsOrderAndNewImportAppends(){
        val sources=listOf("a" to 1,"b" to 2)
        assertEquals(listOf("a" to 3,"b" to 2),SubscriptionGroups.upsert(sources,"a" to 3){it.first})
        assertEquals(listOf("a" to 1,"b" to 2,"c" to 3),SubscriptionGroups.upsert(sources,"c" to 3){it.first})
    }
}
