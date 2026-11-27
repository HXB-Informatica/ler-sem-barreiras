package pt.lersembarreiras.app.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectInfoTest {
    @Test
    fun supportersStartEmptyUntilSchoolApproval() {
        assertTrue(ProjectInfo.supporters.isEmpty())
        assertTrue(ProjectInfo.approvedSupporters().isEmpty())
    }

    @Test
    fun identifiesSchoolPartnerAndLicense() {
        assertTrue(ProjectInfo.SCHOOL.contains("Gil Vicente"))
        assertTrue(ProjectInfo.TECH_PARTNER.contains("HXB"))
        assertEquals("https://github.com/HXB-Informatica/ler-sem-barreiras", ProjectInfo.GITHUB_URL)
        assertTrue(ProjectInfo.LICENSE.contains("GPL"))
    }
}
