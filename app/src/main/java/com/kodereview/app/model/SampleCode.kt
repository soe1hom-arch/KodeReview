package com.kodereview.app.model

object SampleCode {
    val defaultSample = """
@Composable
fun myScreen() {
    var counter = remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("KodeReview") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Welcome to KodeReview!",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(8.dp).fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { counter.value++ }) {
            Text(text = "Count: " + counter.value)
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(onClick = { }) {
            Text(text = "Reset")
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Powered by KodeReview",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}
""".trimIndent()
}
