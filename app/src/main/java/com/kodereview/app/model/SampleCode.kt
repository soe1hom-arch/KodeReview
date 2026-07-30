package com.kodereview.app.model

object SampleCode {
    val defaultSample = """@Composable
fun greeting(name: String) {
    var count by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .size(200.dp)
    ) {
        Text(
            text = "Hello, $name!",
            modifier = Modifier.padding(8.dp)
        )

        Button(onClick = { count++ }) {
            Text("Clicked $count times")
        }
    }
}

@Composable
fun MyScreen() {
    val items = remember { listOf("A", "B", "C") }

    LazyColumn {
        items(items) { item ->
            Text(item)
        }
    }
}

class myClass {
    val MY_VALUE = 42

    fun double(x: Int): Int {
        return x * 2
    }
}
""".trimIndent()
}
