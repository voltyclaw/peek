package app.pane.android

/**
 * Instrumented tests the nightly emulator job runs.
 * The runner filter is the fully qualified name app.pane.android.DeviceSmoke.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DeviceSmoke
