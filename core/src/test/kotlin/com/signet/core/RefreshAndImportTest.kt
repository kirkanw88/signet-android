package com.signet.core

import org.junit.Test
import kotlin.test.*

class RefreshAndImportTest {
    private fun profile(auto:Boolean=false,details:String="")=Profiles.parseJson("""{"remarks":"${if(auto)"AUTO"else"Server"}","outbounds":[{"protocol":"freedom"}],"signet":{"description":"$details"}}""").single()
    @Test fun updatedAutoKeepsIdentityWithoutChoosingANode(){val before=ProfileRefresh.reconcile("sub",emptyList(),listOf(profile(),profile(true)));val after=ProfileRefresh.reconcile("sub",before,listOf(profile(true,"updated"),profile(false,"other")));assertEquals(before.last().id,after.first().id);assertEquals(after.first().id,ProfileRefresh.selection(before.last().id,after,false))}
    @Test fun removedAutoNeverFallsBackToFirstServer(){val old=ProfileRefresh.reconcile("sub",emptyList(),listOf(profile(true))).single();val next=ProfileRefresh.reconcile("sub",listOf(old),listOf(profile()));assertNull(ProfileRefresh.selection(old.id,next,false))}
    @Test fun duplicateTitlesAndConfigsDoNotCollapse(){val list=ProfileRefresh.reconcile("s",emptyList(),listOf(profile(),profile()));assertEquals(2,list.map{it.id}.distinct().size)}
    @Test fun qrRejectsUnsupportedAndInsecureContent(){assertFalse(ImportReview.supported("http://example.com/sub"));assertFalse(ImportReview.supported("https://user:secret@example.com/sub"));assertFalse(ImportReview.supported("Hello world"));assertFalse(ImportReview.supported("{\"token\":\"x\"}"));assertTrue(ImportReview.supported("https://example.com/sub"));assertTrue(ImportReview.supported("vless://uuid@example.com:443"))}
}
