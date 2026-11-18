package com.signet.core
import org.json.*
import org.junit.Test
import kotlin.test.*
class TransportChecksTest {
    @Test fun xdriveRequiresEncryptionAndExplicitOAuthConsent(){val c=JSONObject("""{"outbounds":[{"protocol":"vless","settings":{"vnext":[{"users":[{"encryption":"none"}]}]},"streamSettings":{"network":"xdrive","xdriveSettings":{"service":"google-drive","secrets":["test-client","test-secret","test-refresh"]}}}]}""");assertTrue(TransportChecks.requiresOAuthConsent(c));assertTrue(TransportChecks.issues(c).any{it.code=="XDRIVE_ENCRYPTION"&&it.error});assertFalse(c.toString().contains("xhttp"))}
    @Test fun masqueNeedsTlsAndHasNoImplicitFallback(){val c=JSONObject("""{"outbounds":[{"protocol":"masque","mux":{"enabled":true},"streamSettings":{"network":"tcp","security":"none"}}]}""");val before=c.toString();assertEquals(2,TransportChecks.issues(c).count{it.error});assertEquals(before,c.toString())}
    @Test fun systemDnsDoesNotUseDomainScopedResolver(){val c="""{"outbounds":[{"protocol":"freedom"}],"dns":{"servers":[{"address":"198.18.0.3","domains":["domain:signet.test"]},"9.9.9.9"]}}""";assertEquals("9.9.9.9",TemplateCompatibility.deviceDns(Profiles.parseJson(c).single(),"1.1.1.1"))}
}
