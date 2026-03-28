package my.ssdid.sdk

@RequiresOptIn(
    message = "This is internal SSDID SDK API. It may change without notice.",
    level = RequiresOptIn.Level.ERROR
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class InternalSsdidApi
