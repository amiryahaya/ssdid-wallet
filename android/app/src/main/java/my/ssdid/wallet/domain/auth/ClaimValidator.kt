package my.ssdid.wallet.domain.auth

object ClaimValidator {

    private val wellKnownKeys = setOf("name", "email", "phone")

    private val emailRegex =
        Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

    private val phoneRegex =
        Regex("^\\+[1-9]\\d{6,14}$")

    fun isWellKnown(key: String): Boolean = key in wellKnownKeys

    fun validate(key: String, value: String): String? = when (key) {
        "name" -> validateName(value)
        "email" -> validateEmail(value)
        "phone" -> validatePhone(value)
        else -> null
    }

    private fun validateName(value: String): String? = when {
        value.isEmpty() -> "Name must not be empty"
        value.length > 100 -> "Name must not exceed 100 characters"
        else -> null
    }

    private fun validateEmail(value: String): String? =
        if (emailRegex.matches(value)) null else "Invalid email format"

    private fun validatePhone(value: String): String? =
        if (phoneRegex.matches(value)) null else "Invalid phone format (E.164 required)"
}
