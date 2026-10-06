package app.pane.android.ui.home

data class SamplePickerGroup(
    val title: String,
    val samples: List<SamplePickerRow>,
)

data class SamplePickerRow(
    val id: String,
    val label: String,
    val url: String,
)
