package com.mikotrack.core

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests of the permission catalogue.
 *
 * The catalogue drives the guidance shown to the user, so every permission has to
 * carry a purpose, a rationale and the consequence of a refusal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = com.mikotrack.TestApplication::class)
class PermissionRequirementTest {

    @Test
    fun `every permission explains itself in every language`() {
        val base = ApplicationProvider.getApplicationContext<android.content.Context>()
        listOf("en", "ca", "es").forEach { tag ->
            val configuration = android.content.res.Configuration(base.resources.configuration)
            configuration.setLocale(java.util.Locale.forLanguageTag(tag))
            val context = base.createConfigurationContext(configuration)
            PermissionRequirement.ALL.forEach { requirement ->
                listOf(
                    requirement.titleRes,
                    requirement.purposeRes,
                    requirement.rationaleRes,
                    requirement.consequenceRes,
                ).forEach { res ->
                    assertTrue(
                        "${requirement.id} has an empty text in $tag",
                        context.getString(res).isNotBlank(),
                    )
                }
                assertTrue("${requirement.id} has no permissions", requirement.permissions.isNotEmpty())
            }
        }
    }

    @Test
    fun `the foreground location is requested before the background one`() {
        val order = PermissionRequirement.ALL.map { it.id }
        val foreground = order.indexOf(PermissionRequirement.FOREGROUND_LOCATION.id)
        val background = order.indexOf(PermissionRequirement.BACKGROUND_LOCATION.id)

        assertTrue("The foreground location must be requested first", foreground < background)
    }

    @Test
    fun `the permissions needed to record are the required ones`() {
        val required = PermissionRequirement.ALL.filter { it.required }
        assertEquals(required, PermissionRequirement.REQUIRED_FOR_RECORDING)
        assertTrue(required.contains(PermissionRequirement.FOREGROUND_LOCATION))
        assertTrue(required.contains(PermissionRequirement.NOTIFICATIONS))
        assertTrue(required.contains(PermissionRequirement.BACKGROUND_LOCATION))
        assertFalse(required.contains(PermissionRequirement.CAMERA))
    }

    @Test
    fun `a permission that has not been granted is missing`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        PermissionRequirement.ALL.forEach { requirement ->
            assertFalse(
                "${requirement.id} should not be granted",
                requirement.isGranted(context),
            )
        }
        assertEquals(PermissionRequirement.ALL.size, PermissionRequirement.missing(context).size)
        assertEquals(
            PermissionRequirement.REQUIRED_FOR_RECORDING.size,
            PermissionRequirement.missingForRecording(context).size,
        )
    }
}
