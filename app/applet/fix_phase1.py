import os
import re

# 1. ImagePickerScreen.kt (Real Tab Filtering)
file_path = "app/src/main/java/com/example/ui/screens/ImagePickerScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

# Add data class
if "data class ImageItem(" not in content:
    content = content.replace("@Composable\nfun ImagePickerScreen", "data class ImageItem(val uri: String, val bucket: String)\n\n@Composable\nfun ImagePickerScreen")

# Add BUCKET_DISPLAY_NAME
content = content.replace(
    """val projection = arrayOf(
                        android.provider.MediaStore.Images.Media._ID,
                        android.provider.MediaStore.Images.Media.DATE_ADDED
                    )""",
    """val projection = arrayOf(
                        android.provider.MediaStore.Images.Media._ID,
                        android.provider.MediaStore.Images.Media.DATE_ADDED,
                        android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                    )"""
)

# Update state type
content = content.replace(
    "var images by remember { mutableStateOf(emptyList<String>()) }",
    "var images by remember { mutableStateOf(emptyList<ImageItem>()) }"
)

# Update fetched list type
content = content.replace(
    "val fetched = mutableListOf<String>()",
    "val fetched = mutableListOf<ImageItem>()"
)

# Update cursor reading
content = content.replace(
    """val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    fetched.add(contentUri.toString())
                }""",
    """val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                val bucketColumn = cursor.getColumnIndex(android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val bucket = if (bucketColumn != -1) cursor.getString(bucketColumn) ?: "" else ""
                    val contentUri = android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    fetched.add(ImageItem(contentUri.toString(), bucket))
                }"""
)

# Filtering logic
filter_logic = """
            val filteredImages = remember(images, selectedTabIndex) {
                when (selectedTabIndex) {
                    0 -> images.map { it.uri }
                    1 -> images.filter { !it.bucket.contains("Screenshot", ignoreCase = true) }.map { it.uri }
                    2 -> images.filter { it.bucket.contains("Screenshot", ignoreCase = true) }.map { it.uri }
                    else -> images.map { it.uri }
                }
            }

            // Grid
            LazyVerticalGrid("""

content = content.replace("// Grid\n            LazyVerticalGrid(", filter_logic)

content = content.replace("itemsIndexed(images)", "itemsIndexed(filteredImages)")
content = content.replace("onContinue(selectedIndices.map { images[it] })", "onContinue(selectedIndices.map { filteredImages[it] })")
content = content.replace("data(images[index])", "data(filteredImages[index])")

with open(file_path, "w") as f:
    f.write(content)


# 2. EnhanceOptionsScreen.kt
file_path = "app/src/main/java/com/example/ui/screens/EnhanceOptionsScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

# Add LaunchedEffect
dynamic_logic = """    var origW by remember { mutableIntStateOf(0) }
    var origH by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    
    LaunchedEffect(selectedImages) {
        selectedImages.firstOrNull()?.let { uriString ->
            try {
                val uri = android.net.Uri.parse(uriString)
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream, null, options)
                    origW = options.outWidth
                    origH = options.outHeight
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {"""

content = content.replace("    val context = LocalContext.current\n\n    Box(modifier = Modifier.fillMaxSize()) {", dynamic_logic)

content = content.replace('text = "Original Resolution"', 'text = "${origW} × ${origH}"')
content = content.replace('text = if (scaleIndex == 0) "2560 × 1920" else "5120 × 3840"', 'text = if (scaleIndex == 0) "${origW * 2} × ${origH * 2}" else "${origW * 4} × ${origH * 4}"')

with open(file_path, "w") as f:
    f.write(content)


# 3. ResultScreen.kt
file_path = "app/src/main/java/com/example/ui/screens/ResultScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

dynamic_logic_result = """    var origW by remember { mutableIntStateOf(0) }
    var origH by remember { mutableIntStateOf(0) }
    val scaleIndex by viewModel.scaleIndex.collectAsState()
    
    LaunchedEffect(viewModel.selectedImages.value) {
        viewModel.selectedImages.value.firstOrNull()?.let { uriString ->
            try {
                val uri = android.net.Uri.parse(uriString)
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream, null, options)
                    origW = options.outWidth
                    origH = options.outHeight
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column("""

content = content.replace("    Column(", dynamic_logic_result)

content = content.replace('Text(text = "1280 × 960"', 'Text(text = "${origW} × ${origH}"')
content = content.replace('Text(text = "5120 × 3840"', 'Text(text = if (scaleIndex == 0) "${origW * 2} × ${origH * 2}" else "${origW * 4} × ${origH * 4}"')
content = content.replace('Text(text = "4x Upscale"', 'Text(text = if (scaleIndex == 0) "2x Upscale" else "4x Upscale"')

with open(file_path, "w") as f:
    f.write(content)
