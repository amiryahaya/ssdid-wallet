package my.ssdid.sdk.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class PresentationSubmissionTest {

    @Test
    fun toJsonContainsAllFields() {
        val submission = PresentationSubmission(
            id = "sub-123",
            definitionId = "pd-1",
            descriptorMap = listOf(
                DescriptorMapEntry(id = "id-1", format = "vc+sd-jwt", path = "$")
            )
        )
        val jsonStr = submission.toJson()
        val parsed = Json.parseToJsonElement(jsonStr)
        assertThat(jsonStr).contains("\"id\":\"sub-123\"")
        assertThat(jsonStr).contains("\"definition_id\":\"pd-1\"")
        assertThat(jsonStr).contains("\"descriptor_map\"")
        assertThat(jsonStr).contains("\"format\":\"vc+sd-jwt\"")
        assertThat(jsonStr).contains("\"path\":\"$\"")
    }

    @Test
    fun generatesUuidIdWhenNotProvided() {
        val submission = PresentationSubmission.create("pd-1", listOf("id-1"))
        val jsonStr = submission.toJson()
        assertThat(submission.id).isNotEmpty()
        assertThat(submission.id.length).isEqualTo(36) // UUID format
        assertThat(jsonStr).contains("\"definition_id\":\"pd-1\"")
    }
}
