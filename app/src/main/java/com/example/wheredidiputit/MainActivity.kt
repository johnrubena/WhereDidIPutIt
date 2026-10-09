package com.example.wheredidiputit







import android.os.Bundle
import android.widget.Toast
import android.content.Intent
import android.app.Activity
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.wheredidiputit.ui.theme.WhereDidIPutItTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase

    /*
     * Delete a photo only if no saved item uses it.
     */
    private suspend fun deletePhotoIfUnused(photoPath: String?) {

        if (photoPath.isNullOrBlank()) {
            return
        }

        val savedItems = database.itemDao().getAllItems()

        val photoStillInUse = savedItems.any { item ->
            item.photoPath == photoPath
        }

        if (!photoStillInUse) {
            val photoFile = java.io.File(photoPath)

            if (photoFile.exists()) {
                photoFile.delete()
            }
        }
    }


    private var selectedPhotoUri by mutableStateOf<android.net.Uri?>(null)

    private val pickItemPhoto =
        registerForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                selectedPhotoUri = uri
            }
        }


    private val createBackupFile =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri ->
            if (uri != null) {
                lifecycleScope.launch {
                    try {
                        val backupItems = database.itemDao().getAllItems()

                        val outputStream =
                            contentResolver.openOutputStream(uri)

                        if (outputStream == null) {
                            Toast.makeText(
                                this@MainActivity,
                                "Could not create the backup file.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            outputStream.use { stream ->
                                BackupManager.createBackup(
                                    context = this@MainActivity,
                                    items = backupItems,
                                    outputStream = stream
                                )
                            }

                            Toast.makeText(
                                this@MainActivity,
                                "Backup created successfully!",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@MainActivity,
                            "Backup failed: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }



    private val restoreBackupFile =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                lifecycleScope.launch {
                    try {
                        val inputStream =
                            contentResolver.openInputStream(uri)

                        if (inputStream == null) {
                            Toast.makeText(
                                this@MainActivity,
                                "Could not open the backup file.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            val restoredItems = inputStream.use { stream ->
                                BackupManager.restoreBackup(
                                    context = this@MainActivity,
                                    inputStream = stream
                                )
                            }

                            if (restoredItems.isNotEmpty()) {
                                restoredItems.forEach { item ->
                                    database.itemDao()
                                        .insertOrReplaceItem(item)
                                }

                                Toast.makeText(
                                    this@MainActivity,
                                    "${restoredItems.size} item(s) restored successfully.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    "No valid items were found in the backup.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@MainActivity,
                            "Restore failed: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        database = AppDatabase.getDatabase(this)

        setContent {

            WhereDidIPutItTheme {

                var currentScreen by remember {
                    mutableStateOf("home")
                }

                var items by remember {
                    mutableStateOf<List<Item>>(emptyList())
                }

                var selectedItem by remember {
                    mutableStateOf<Item?>(null)
                }

                var searchText by remember {
                    mutableStateOf("")
                }

                var showDeleteDialog by remember {
                    mutableStateOf(false)
                }

                var showRestoreDialog by remember { mutableStateOf(false) }

                /*
                 * Load items from the Room database
                 */
                LaunchedEffect(currentScreen) {
                    if (currentScreen == "home") {
                        items = database.itemDao().getAllItems()
                    }
                }

                /*
                 * SEARCH FILTER
                 *
                 * Search works across:
                 * - Item name
                 * - Location
                 * - Specific place
                 * - Container
                 * - Notes
                 * - Category
                 */
                val filteredItems = items.filter { item ->

                    item.name.contains(
                        searchText,
                        ignoreCase = true
                    ) ||
                            item.location.contains(
                                searchText,
                                ignoreCase = true
                            ) ||
                            item.specificPlace.contains(
                                searchText,
                                ignoreCase = true
                            ) ||
                            item.container.contains(
                                searchText,
                                ignoreCase = true
                            ) ||
                            item.notes.contains(
                                searchText,
                                ignoreCase = true
                            ) ||
                            item.category.contains(
                                searchText,
                                ignoreCase = true
                            )
                }

                Scaffold { innerPadding ->

                    if (currentScreen == "home") {

                        HomeScreen(
                            modifier = Modifier.padding(innerPadding),
                            items = filteredItems,
                            searchText = searchText,
                            onSearchTextChange = {
                                searchText = it
                            },
                            onAddItem = {

                                currentScreen = "add"
                            },
                            onItemClick = { item ->

                                selectedItem = item
                                currentScreen = "details"
                            },
                            onAboutHelp = {

                                currentScreen = "about"
                            }
                        )

                    } else if (currentScreen == "add") {


                        AddItemScreen(
                            modifier = Modifier.padding(innerPadding),

                            onPickPhoto = {
                                pickItemPhoto.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },

                            photoUri = selectedPhotoUri,


                            onSave = { newItem ->
                                lifecycleScope.launch {
                                    val savedPhotoPath = selectedPhotoUri?.let { uri ->
                                        val photoDirectory = java.io.File(
                                            filesDir,
                                            "item_photos"
                                        )

                                        if (!photoDirectory.exists()) {
                                            photoDirectory.mkdirs()
                                        }

                                        val mimeType = contentResolver.getType(uri)

                                        val extension = when (mimeType) {
                                            "image/png" -> ".png"
                                            "image/webp" -> ".webp"
                                            "image/gif" -> ".gif"
                                            "image/heic" -> ".heic"
                                            "image/heif" -> ".heif"
                                            else -> ".jpg"
                                        }

                                        val photoFile = java.io.File(
                                            photoDirectory,
                                            "item_${System.currentTimeMillis()}$extension"
                                        )

                                        contentResolver.openInputStream(uri)?.use { input ->
                                            photoFile.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                            photoFile.absolutePath
                                        }
                                    }

                                    val itemToSave = newItem.copy(
                                        photoPath = savedPhotoPath
                                    )

                                    database
                                        .itemDao()
                                        .insertItem(itemToSave)

                                    items = database
                                        .itemDao()
                                        .getAllItems()

                                    selectedPhotoUri = null
                                    currentScreen = "home"
                                }
                            },

                            onCancel = {
                                currentScreen = "home"
                            }
                        )


                    } else if (
                        currentScreen == "details" &&
                        selectedItem != null
                    ) {

                        ItemDetailsScreen(
                            modifier = Modifier.padding(innerPadding),
                            item = selectedItem!!,

                            onBack = {

                                selectedItem = null
                                currentScreen = "home"
                            },

                            onEdit = {

                                currentScreen = "edit"
                            },

                            onDelete = {

                                showDeleteDialog = true
                            }
                        )

                    } else if (
                        currentScreen == "edit" &&
                        selectedItem != null
                    ) {

                        EditItemScreen(
                            modifier = Modifier.padding(innerPadding),
                            item = selectedItem!!,

                            photoUri = selectedPhotoUri,
                            onPickPhoto = {
                                pickItemPhoto.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },


                            onSave = { updatedItem, replacementPhotoUri, shouldRemovePhoto ->
                                lifecycleScope.launch {

                                    val oldPhotoPath = updatedItem.photoPath

                                    val finalPhotoPath = when {
                                        shouldRemovePhoto -> null

                                        replacementPhotoUri != null -> {
                                            val photoDirectory = java.io.File(
                                                filesDir,
                                                "item_photos"
                                            )

                                            if (!photoDirectory.exists()) {
                                                photoDirectory.mkdirs()
                                            }

                                            val mimeType =
                                                contentResolver.getType(replacementPhotoUri)

                                            val extension = when (mimeType) {
                                                "image/png" -> ".png"
                                                "image/webp" -> ".webp"
                                                "image/gif" -> ".gif"
                                                "image/heic" -> ".heic"
                                                "image/heif" -> ".heif"
                                                else -> ".jpg"
                                            }

                                            val photoFile = java.io.File(
                                                photoDirectory,
                                                "item_${System.currentTimeMillis()}$extension"
                                            )

                                            contentResolver
                                                .openInputStream(replacementPhotoUri)
                                                ?.use { input ->
                                                    photoFile.outputStream().use { output ->
                                                        input.copyTo(output)
                                                    }
                                                    photoFile.absolutePath
                                                } ?: updatedItem.photoPath
                                        }

                                        else -> updatedItem.photoPath
                                    }

                                    val itemToUpdate = updatedItem.copy(
                                        photoPath = finalPhotoPath
                                    )

                                    database
                                        .itemDao()
                                        .updateItem(itemToUpdate)

                                    if (replacementPhotoUri != null && oldPhotoPath != finalPhotoPath) {
                                        deletePhotoIfUnused(oldPhotoPath)
                                    }

                                    if (shouldRemovePhoto) {
                                        deletePhotoIfUnused(oldPhotoPath)
                                    }

                                    items = database
                                        .itemDao()
                                        .getAllItems()

                                    selectedItem = itemToUpdate
                                    selectedPhotoUri = null
                                    currentScreen = "details"
                                }
                            },

                            onCancel = {

                                currentScreen = "details"
                            }
                        )
                    }
                    else if (currentScreen == "about") {

                        AboutHelpScreen(
                            modifier = Modifier.fillMaxSize(),
                            onBack = {
                                currentScreen = "home"
                            },
                            onBackup = {
                                createBackupFile.launch("where_did_i_put_it_backup.zip")
                            },
                            onRestore = {
                                showRestoreDialog = true
                            }
                        )
                    }
                }

                /*
                 * DELETE CONFIRMATION DIALOG
                 */
                if (showDeleteDialog) {

                    AlertDialog(
                        onDismissRequest = {
                            showDeleteDialog = false
                        },

                        title = {
                            Text("Delete Item?")
                        },

                        text = {

                            Text(
                                text = if (selectedItem != null) {
                                    "Are you sure you want to delete \"${selectedItem!!.name}\"?"
                                } else {
                                    "Are you sure you want to delete this item?"
                                }
                            )
                        },

                        confirmButton = {

                            Button(
                                onClick = {

                                    selectedItem?.let { item ->

                                        lifecycleScope.launch {

                                            val photoPathToDelete = item.photoPath

                                            database
                                                .itemDao()
                                                .deleteItem(item)

                                            deletePhotoIfUnused(photoPathToDelete)

                                            items = database
                                                .itemDao()
                                                .getAllItems()

                                            selectedItem = null

                                            currentScreen = "home"

                                            showDeleteDialog = false
                                        }
                                    }
                                }
                            ) {

                                Text("DELETE")
                            }
                        },

                        dismissButton = {

                            Button(
                                onClick = {
                                    showDeleteDialog = false
                                }
                            ) {

                                Text("CANCEL")
                            }
                        }
                    )
                }

                if (showRestoreDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showRestoreDialog = false
                        },
                        title = {
                            Text("Restore Backup?")
                        },
                        text = {
                            Text(
                                "Restoring a backup may replace existing items with the same ID. Do you want to continue?"
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showRestoreDialog = false

                                    restoreBackupFile.launch(
                                        arrayOf("application/zip", "application/octet-stream")
                                    )
                                }
                            ) {
                                Text("RESTORE")
                            }
                        },
                        dismissButton = {
                            Button(
                                onClick = {
                                    showRestoreDialog = false
                                }
                            ) {
                                Text("CANCEL")
                            }
                        }
                    )
                }
            }
        }
    }
}



/*
 * ------------------------------------------------------------
 * CATEGORY ICON
 * ------------------------------------------------------------
 */

fun getCategoryIcon(category: String): String {

    return when (category) {

        "Documents" -> "📄"

        "Keys" -> "🔑"

        "Electronics" -> "💻"

        "Clothing" -> "👕"

        "Tools" -> "🔧"

        "Household" -> "🏠"

        else -> "📦"
    }
}


/*
 * ------------------------------------------------------------
 * FORMAT ITEM DATE
 * ------------------------------------------------------------
 */

fun formatItemDate(timestamp: Long): String {
    val formatter = SimpleDateFormat(
        "MMMM d, yyyy",
        Locale.getDefault()
    )
    return formatter.format(Date(timestamp))
}

fun formatUpdatedDate(timestamp: Long): String {
    val formatter = SimpleDateFormat(
        "MMMM d, yyyy",
        Locale.getDefault()
    )
    return formatter.format(Date(timestamp))
}

/*
 * ------------------------------------------------------------
 * HOME SCREEN
 * ------------------------------------------------------------
 */

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    items: List<Item>,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onAddItem: () -> Unit,
    onItemClick: (Item) -> Unit,
    onAboutHelp: () -> Unit
) {

    val scrollState = rememberScrollState()

    /*
     * SORTING
     */
    var sortOption by remember {
        mutableStateOf("Newest")
    }

    /*
     * SORT MENU
     */
    var sortMenuExpanded by remember {
        mutableStateOf(false)
    }

    /*
     * CATEGORY FILTER
     */
    var selectedCategory by remember {
        mutableStateOf("All")
    }
    var importantOnly by remember {
        mutableStateOf(false)
    }

    /*
     * CATEGORY MENU
     */
    var categoryMenuExpanded by remember {
        mutableStateOf(false)
    }

    /*
     * CATEGORY OPTIONS
     */
    val categories = listOf(
        "All",
        "Documents",
        "Keys",
        "Electronics",
        "Clothing",
        "Tools",
        "Household",
        "Other"
    )

    /*
     * CATEGORY FILTERING
     *
     * Search filtering has already happened
     * in MainActivity.
     *
     * Here we apply the selected category.
     */
    val categoryFilteredItems = if (selectedCategory == "All") {

        items

    } else {

        items.filter { item ->
            item.category == selectedCategory
        }
    }

    val filteredByImportance = if (importantOnly) {

        categoryFilteredItems.filter { item ->
            item.isImportant
        }

    } else {

        categoryFilteredItems
    }

    /*
     * SORTING
     *
     * Category filtering happens first,
     * then sorting happens.
     */
    val sortedItems = when (sortOption) {

        "Oldest" ->
            filteredByImportance.sortedBy {
                it.createdAt
            }

        "A–Z" ->
            filteredByImportance.sortedBy {
                it.name.lowercase()
            }

        else ->
            filteredByImportance.sortedByDescending {
                it.createdAt
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {

        /*
         * APP HEADER
         */
        Text(
            text = "Where Did I Put It?",
            fontSize = 30.sp
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "Find your things when you need them.",
            fontSize = 15.sp
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        HorizontalDivider()

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        /*
         * SEARCH
         */
        TextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Search your items")
            },
            placeholder = {
                Text("e.g. keys, passport, charger")
            },
            singleLine = true,
            trailingIcon = {

                if (searchText.isNotEmpty()) {

                    Button(
                        onClick = {
                            onSearchTextChange("")
                            selectedCategory = "All"
                            importantOnly = false
                        }
                    ) {

                        Text("CLEAR")
                    }
                }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Search by item, location, container, notes, or category.",
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Filter & Sort",
            fontSize = 18.sp
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        /*
         * SORT DROPDOWN
         */
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {

            Button(
                onClick = {
                    sortMenuExpanded = !sortMenuExpanded
                },
                modifier = Modifier.fillMaxWidth()
            ) {

                Text("Sort: $sortOption")
            }

            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = {
                    sortMenuExpanded = false
                }
            ) {

                DropdownMenuItem(
                    text = {
                        Text("Newest")
                    },
                    onClick = {

                        sortOption = "Newest"
                        sortMenuExpanded = false
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text("Oldest")
                    },
                    onClick = {

                        sortOption = "Oldest"
                        sortMenuExpanded = false
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text("A–Z")
                    },
                    onClick = {

                        sortOption = "A–Z"
                        sortMenuExpanded = false
                    }
                )
            }
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        /*
         * CATEGORY FILTER DROPDOWN
         */
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {

            Button(
                onClick = {
                    categoryMenuExpanded = !categoryMenuExpanded
                },
                modifier = Modifier.fillMaxWidth()
            ) {

                Text("Category: $selectedCategory")
            }

            DropdownMenu(
                expanded = categoryMenuExpanded,
                onDismissRequest = {
                    categoryMenuExpanded = false
                }
            ) {

                categories.forEach { categoryOption ->

                    DropdownMenuItem(
                        text = {
                            Text(categoryOption)
                        },
                        onClick = {

                            selectedCategory = categoryOption
                            categoryMenuExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Button(
            onClick = {
                importantOnly = !importantOnly
            },
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                if (importantOnly) {
                    "⭐ Showing Important Items"
                } else {
                    "⭐ Show Important Only"
                }
            )
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        /*
         * SEARCH RESULTS COUNT
         */
        if (searchText.isNotBlank()) {

            Text(
                text = "${sortedItems.size} result(s) found.",
                fontSize = 16.sp
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )
        }

        if (importantOnly) {

            Text(
                text = "⭐ Showing ${sortedItems.size} important item(s).",
                fontSize = 15.sp
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )
        }

        /*
         * NO ITEMS AFTER FILTERING
         */
        if (sortedItems.isEmpty()) {

            if (items.isEmpty()) {

                /*
                 * NO ITEMS AT ALL
                 */
                if (searchText.isBlank()) {

                    Spacer(
                        modifier = Modifier.height(30.dp)
                    )

                    Text(
                        text = "📦",
                        fontSize = 48.sp
                    )

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    Text(
                        text = "No Items Yet",
                        fontSize = 26.sp
                    )

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    Text(
                        text = "Start adding your belongings so you can quickly remember where you put them.",
                        fontSize = 16.sp
                    )

                    Spacer(
                        modifier = Modifier.height(24.dp)
                    )

                    Button(
                        onClick = onAddItem,
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Text("ADD YOUR FIRST ITEM")
                    }

                } else {

                    /*
                     * NO SEARCH RESULTS
                     */
                    Spacer(
                        modifier = Modifier.height(20.dp)
                    )

                    Text(
                        text = "🔍",
                        fontSize = 42.sp
                    )

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    Text(
                        text = "No Items Found",
                        fontSize = 26.sp
                    )

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    Text(
                        text = "We couldn't find anything matching your search.",
                        fontSize = 16.sp
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = "Try another item, location, or category.",
                        fontSize = 16.sp
                    )

                    Spacer(
                        modifier = Modifier.height(24.dp)
                    )

                    Button(
                        onClick = {
                                onSearchTextChange("")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Text("CLEAR SEARCH")
                    }
                }

            } else {

                /*
                 * ITEMS EXIST, BUT FILTER RETURNED NONE
                 */
                Spacer(
                    modifier = Modifier.height(20.dp)
                )

                Text(
                    text = "🔎",
                    fontSize = 42.sp
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text = "No Matching Items",
                    fontSize = 26.sp
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text = if (selectedCategory != "All") {
                        "No items found in the \"$selectedCategory\" category."
                    } else {
                        "No items match your search."
                    },
                    fontSize = 16.sp
                )

                Spacer(
                    modifier = Modifier.height(24.dp)
                )

                Button(
                    onClick = {
                        selectedCategory = "All"
                        onSearchTextChange("")
                        importantOnly = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Text("CLEAR FILTERS")
                }
            }

        } else {

            /*
             * ITEM LIST
             */
            sortedItems.forEach { item ->

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    onClick = {
                        onItemClick(item)
                    }
                ) {

                    Row(
                        modifier = Modifier.padding(12.dp)
                    ) {

                        // ITEM PHOTO THUMBNAIL
                        if (!item.photoPath.isNullOrBlank()) {

                            androidx.compose.foundation.Image(
                                painter = rememberAsyncImagePainter(item.photoPath),
                                contentDescription = "Photo of ${item.name}",
                                modifier = Modifier
                                    .height(90.dp)
                                    .fillMaxWidth(0.28f),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )

                            Spacer(
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                        }

                        // ITEM INFORMATION
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {

                        /*
                         * ITEM NAME
                         */
                        Text(
                            text = if (item.isImportant) {
                                "⭐ ${getCategoryIcon(item.category)}  ${item.name}"
                            } else {
                                "${getCategoryIcon(item.category)}  ${item.name}"
                            },
                            fontSize = 22.sp
                        )
                        if (item.isImportant) {

                            Text(
                                text = "⭐ IMPORTANT",
                                fontSize = 13.sp
                            )
                        }

                        Spacer(
                            modifier = Modifier.height(4.dp)
                        )

                        /*
                         * CATEGORY
                         */
                        Text(
                            text = item.category,
                            fontSize = 15.sp
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        /*
                         * LOCATION
                         */
                        Text(
                            text = "📍  Where",
                            fontSize = 13.sp
                        )

                        Text(
                            text = if (item.location.isBlank()) {
                                "Not specified"
                            } else {
                                item.location
                            },
                            fontSize = 18.sp
                        )

                        /*
                         * SPECIFIC PLACE
                         */
                        if (item.specificPlace.isNotBlank()) {

                            Spacer(
                                modifier = Modifier.height(8.dp)
                            )

                            Text(
                                text = "📌 Specific place",
                                fontSize = 13.sp
                            )

                            Text(
                                text = item.specificPlace,
                                fontSize = 17.sp
                            )
                        }

                        /*
                         * CONTAINER
                         */
                        if (item.container.isNotBlank()) {

                            Spacer(
                                modifier = Modifier.height(8.dp)
                            )

                            Text(
                                text = "📦 Container",
                                fontSize = 13.sp
                            )

                            Text(
                                text = item.container,
                                fontSize = 17.sp
                            )
                        }

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )

                        /*
                         * ADDED DATE
                         */
                        /*
 * ADDED DATE
 */
                        Text(
                            text = "Added: ${formatItemDate(item.createdAt)}",
                            fontSize = 14.sp
                        )

                        Spacer(
                            modifier = Modifier.height(6.dp)
                        )

                        /*
                         * LAST UPDATED DATE
                         */
                        Text(
                            text = "Last Updated: ${formatUpdatedDate(item.updatedAt)}",
                            fontSize = 14.sp
                        )

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )

                        /*
                         * VIEW DETAILS
                         */
                            Text(
                                text = "VIEW DETAILS  →",
                                fontSize = 15.sp
                            )
                        } // Closes item information Column
                    } // Closes Row
                } // Closes Card
            }
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        /*
         * ADD ITEM BUTTON
         */
        Button(
            onClick = onAddItem,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text("+ ADD ITEM")
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Button(
            onClick = onAboutHelp,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text("ℹ ABOUT & HELP")
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )
    }
}


/*
 * ------------------------------------------------------------
 * ADD ITEM SCREEN
 * ------------------------------------------------------------
 */

@Composable
fun AddItemScreen(
    modifier: Modifier = Modifier,
    onPickPhoto: () -> Unit,
    photoUri: android.net.Uri?,
    onSave: (Item) -> Unit,
    onCancel: () -> Unit
) {

    val context = androidx.compose.ui.platform.LocalContext.current

    var isImportant by remember {
        mutableStateOf(false)
    }
    var selectedPhotoUri by remember {
        mutableStateOf<android.net.Uri?>(null)
    }
    var name by remember {
        mutableStateOf("")
    }

    var location by remember {
        mutableStateOf("")
    }

    var specificPlace by remember {
        mutableStateOf("")
    }

    var container by remember {
        mutableStateOf("")
    }

    var notes by remember {
        mutableStateOf("")
    }

    var category by remember {
        mutableStateOf("Other")
    }

    var nameError by remember {
        mutableStateOf(false)
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()

            if (!spokenText.isNullOrBlank()) {
                name = spokenText
                nameError = false
            }
        }
    }

    var categoryExpanded by remember {
        mutableStateOf(false)
    }

    val categories = listOf(
        "Documents",
        "Keys",
        "Electronics",
        "Clothing",
        "Tools",
        "Household",
        "Other"
    )

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {

        /*
         * SCREEN TITLE
         */
        Text(
            text = "Add Item",
            fontSize = 28.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Remember where you put your things.",
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(12.dp))


        Text(
            text = "Add a few details below so you can find this item quickly later.",
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // CHOOSE ITEM PHOTO
        // CHOOSE ITEM PHOTO
        Button(
            onClick = onPickPhoto,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("📷 CHOOSE ITEM PHOTO")
        }


        if (photoUri != null) {
            Spacer(modifier = Modifier.height(12.dp))

            androidx.compose.foundation.Image(
                painter = rememberAsyncImagePainter(photoUri),
                contentDescription = "Selected item photo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        }


        Spacer(modifier = Modifier.height(16.dp))


        Button(
            onClick = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE,
                        Locale.getDefault().toLanguageTag()
                    )
                    putExtra(
                        RecognizerIntent.EXTRA_PROMPT,
                        "Say the name of your item"
                    )
                }

                try {
                    speechLauncher.launch(intent)
                } catch (e: android.content.ActivityNotFoundException) {
                    android.widget.Toast.makeText(
                        context,
                        "Speech recognition is not available on this device.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🎤 ENTER ITEM BY VOICE")
        }


        /*
         * ITEM NAME
         */
        TextField(
            value = name,
            onValueChange = {
                name = it
                nameError = false
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("What is the item?")
            },
            placeholder = {
                Text("e.g. Passport, house keys, laptop charger")
            },
            isError = nameError
        )

        if (nameError) {

            Text(
                text = "Item name is required.",
                fontSize = 13.sp
            )
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        /*
         * CATEGORY
         */
        Text(
            text = "What type of item is this?",
            fontSize = 13.sp
        )

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Box(
            modifier = Modifier.fillMaxWidth()
        ) {

            Button(
                onClick = {
                    categoryExpanded = !categoryExpanded
                },
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(category)
            }

            DropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = {
                    categoryExpanded = false
                }
            ) {

                categories.forEach { categoryOption ->

                    DropdownMenuItem(
                        text = {
                            Text(categoryOption)
                        },
                        onClick = {

                            category = categoryOption
                            categoryExpanded = false
                        }
                    )
                }
            }
        }
        /*
 * IMPORTANT ITEM
 */
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {

            Checkbox(
                checked = isImportant,
                onCheckedChange = {
                    isImportant = it
                }
            )

            Text(
                text = "⭐ Mark as Important",
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        /*
         * LOCATION
         */
        TextField(
            value = location,
            onValueChange = {
                location = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Where is it?")
            },
            placeholder = {
                Text("e.g. Kitchen, bedroom, garage")
            },
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        /*
         * SPECIFIC PLACE
         */
        TextField(
            value = specificPlace,
            onValueChange = {
                specificPlace = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Exact spot")
            },
            placeholder = {
                Text("e.g. top drawer, left shelf, bedside table")
            },
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        /*
         * CONTAINER
         */
        TextField(
            value = container,
            onValueChange = {
                container = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("What is it stored in?")
            },
            placeholder = {
                Text("e.g. blue box, drawer, backpack")
            },
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        /*
         * NOTES
         */
        TextField(
            value = notes,
            onValueChange = {
                notes = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Notes")
            },
            placeholder = {
                Text("e.g. important details or reminders")
            },
            minLines = 4
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        /*
         * SAVE ITEM
         */
        Text(
            text = "You can edit or delete this item later.",
            fontSize = 13.sp
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )
        Button(
            onClick = {

                if (name.isBlank()) {

                    nameError = true

                } else {

                    val newItem = Item(
                        name = name.trim(),
                        location = location.trim(),
                        specificPlace = specificPlace.trim(),
                        container = container.trim(),
                        notes = notes.trim(),
                        category = category,
                        isImportant = isImportant
                    )

                    onSave(newItem)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {

            Text("SAVE ITEM")
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        /*
         * BACK TO HOME
         */
        Button(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text("BACK TO HOME")
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )
    }
}


/*
 * ------------------------------------------------------------
 * ITEM DETAILS SCREEN
 * ------------------------------------------------------------
 */

@Composable
fun ItemDetailsScreen(
    modifier: Modifier = Modifier,
    item: Item,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {

        /*
         * SCREEN TITLE
         */
        Text(
            text = "Item Details",
            fontSize = 28.sp
        )

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Text(
            text = "Here is where you put this item.",
            fontSize = 15.sp
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        /*
         * ITEM NAME
         */
        Text(
            text = "${getCategoryIcon(item.category)}  ${item.name}",
            fontSize = 24.sp
        )


        if (!item.photoPath.isNullOrBlank()) {
            Spacer(
                modifier = Modifier.height(16.dp)
            )

            androidx.compose.foundation.Image(
                painter = rememberAsyncImagePainter(item.photoPath),
                contentDescription = "Photo of ${item.name}",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )
        }


        if (item.isImportant) {

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = "⭐ Important Item",
                fontSize = 16.sp
            )
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        /*
         * CATEGORY
         */
        Text(
            text = "Category",
            fontSize = 13.sp
        )

        Text(
            text = item.category,
            fontSize = 18.sp
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        /*
         * LOCATION
         */
        Text(
            text = "Location",
            fontSize = 13.sp
        )

        Text(
            text = if (item.location.isBlank()) {
                "Not specified"
            } else {
                item.location
            },
            fontSize = 18.sp
        )

        /*
         * SPECIFIC PLACE
         */
        if (item.specificPlace.isNotBlank()) {

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(
                text = "Specific place",
                fontSize = 13.sp
            )

            Text(
                text = item.specificPlace,
                fontSize = 18.sp
            )
        }

        /*
         * CONTAINER
         */
        if (item.container.isNotBlank()) {

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(
                text = "Container",
                fontSize = 13.sp
            )

            Text(
                text = item.container,
                fontSize = 18.sp
            )
        }

        /*
         * NOTES
         */
        if (item.notes.isNotBlank()) {

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(
                text = "Notes",
                fontSize = 13.sp
            )

            Text(
                text = item.notes,
                fontSize = 18.sp
            )
        }

        /*
         * ADDED DATE
         */
        /*
 * ADDED DATE
 */
        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text = "Added",
            fontSize = 13.sp
        )

        Text(
            text = formatItemDate(item.createdAt),
            fontSize = 18.sp
        )

        /*
         * LAST UPDATED DATE
         */
        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text = "Last Updated",
            fontSize = 13.sp
        )

        Text(
            text = formatUpdatedDate(item.updatedAt),
            fontSize = 18.sp
        )

        Spacer(
            modifier = Modifier.height(30.dp)
        )

        /*
         * EDIT BUTTON
         */
        Button(
            onClick = onEdit,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text("EDIT ITEM")
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        /*
         * DELETE BUTTON
         */
        Button(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text("DELETE ITEM")
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        /*
         * BACK BUTTON
         */
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text("BACK")
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )
    }
}


/*
 * ------------------------------------------------------------
 * EDIT ITEM SCREEN
 * ------------------------------------------------------------
 */

@Composable
fun EditItemScreen(
    modifier: Modifier = Modifier,
    item: Item,
    photoUri: android.net.Uri?,
    onPickPhoto: () -> Unit,
    onSave: (Item, android.net.Uri?, Boolean) -> Unit,
    onCancel: () -> Unit
) {

    var name by remember(item.id) {
        mutableStateOf(item.name)
    }


    var editedPhotoPath by remember(item.id) {
        mutableStateOf(item.photoPath)
    }

    var newPhotoUri by remember(item.id) {
        mutableStateOf<android.net.Uri?>(null)
    }

    var removePhoto by remember(item.id) {
        mutableStateOf(false)
    }


    var location by remember(item.id) {
        mutableStateOf(item.location)
    }

    var specificPlace by remember(item.id) {
        mutableStateOf(item.specificPlace)
    }

    var container by remember(item.id) {
        mutableStateOf(item.container)
    }

    var notes by remember(item.id) {
        mutableStateOf(item.notes)
    }

    var category by remember(item.id) {
        mutableStateOf(item.category)
    }
    var isImportant by remember(item.id) {
        mutableStateOf(item.isImportant)
    }

    var nameError by remember(item.id) {
        mutableStateOf(false)
    }

    var categoryExpanded by remember(item.id) {
        mutableStateOf(false)
    }

    val categories = listOf(
        "Documents",
        "Keys",
        "Electronics",
        "Clothing",
        "Tools",
        "Household",
        "Other"
    )

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {

        /*
         * SCREEN TITLE
         */
        Text(
            text = "Edit Item",
            fontSize = 26.sp
        )

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Text(
            text = "Update the information for this item.",
            fontSize = 15.sp
        )


        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Item Photo",
            fontSize = 18.sp
        )

        if (!removePhoto && photoUri == null && !editedPhotoPath.isNullOrBlank()) {
            androidx.compose.foundation.Image(
                painter = rememberAsyncImagePainter(editedPhotoPath),
                contentDescription = "Current photo of ${item.name}",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        }

        if (!removePhoto && photoUri != null) {
            androidx.compose.foundation.Image(
                painter = rememberAsyncImagePainter(photoUri),
                contentDescription = "Replacement photo of ${item.name}",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                removePhoto = false
                onPickPhoto()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("CHOOSE OR REPLACE PHOTO")
        }

        if (!editedPhotoPath.isNullOrBlank() || photoUri != null) {
            Button(
                onClick = {
                    removePhoto = true
                    editedPhotoPath = null
                    newPhotoUri = null
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("REMOVE PHOTO")
            }
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        /*
         * ITEM NAME
         */
        TextField(
            value = name,
            onValueChange = {
                name = it
                nameError = false
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Item name")
            },
            isError = nameError
        )

        if (nameError) {

            Text(
                text = "Item name is required.",
                fontSize = 13.sp
            )
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        /*
         * CATEGORY
         */
        Text(
            text = "Category",
            fontSize = 13.sp
        )

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Box(
            modifier = Modifier.fillMaxWidth()
        ) {

            Button(
                onClick = {
                    categoryExpanded = !categoryExpanded
                },
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(category)
            }

            DropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = {
                    categoryExpanded = false
                }
            ) {

                categories.forEach { categoryOption ->

                    DropdownMenuItem(
                        text = {
                            Text(categoryOption)
                        },
                        onClick = {

                            category = categoryOption
                            categoryExpanded = false
                        }
                    )
                }
            }
        }
        /*
         * IMPORTANT ITEM
         */
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {

            Checkbox(
                checked = isImportant,
                onCheckedChange = {
                    isImportant = it
                }
            )

            Text(
                text = "⭐ Mark as Important",
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        /*
         * LOCATION
         */
        TextField(
            value = location,
            onValueChange = {
                location = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Location")
            }
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        /*
         * SPECIFIC PLACE
         */
        TextField(
            value = specificPlace,
            onValueChange = {
                specificPlace = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Specific place")
            }
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        /*
         * CONTAINER
         */
        TextField(
            value = container,
            onValueChange = {
                container = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Container")
            }
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        /*
         * NOTES
         */
        TextField(
            value = notes,
            onValueChange = {
                notes = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Notes")
            },
            minLines = 4
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        /*
         * SAVE CHANGES
         */
        Button(
            onClick = {

                if (name.isBlank()) {

                    nameError = true

                } else {


                    val updatedItem = item.copy(
                        name = name.trim(),
                        location = location.trim(),
                        specificPlace = specificPlace.trim(),
                        container = container.trim(),
                        notes = notes.trim(),
                        category = category,
                        isImportant = isImportant,
                        photoPath = when {
                            removePhoto -> null
                            photoUri != null -> item.photoPath
                            else -> editedPhotoPath
                        },
                        updatedAt = System.currentTimeMillis()
                    )


                    onSave(updatedItem, photoUri, removePhoto)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {

            Text("SAVE CHANGES")
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        /*
         * BACK TO DETAILS
         */
        Button(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text("BACK TO DETAILS")
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )
    }
}
@Composable
fun AboutHelpScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        Spacer(
            modifier = Modifier.height(40.dp)
        )

        Text(
            text = "About & Help",
            fontSize = 28.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Where Did I Put It?",
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "What is this app?",
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Where Did I Put It? helps you remember where you placed your belongings so you can find them quickly later."
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "How to add an item",
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Tap ADD ITEM and enter the item's name, category, location, and any additional details you want to remember."
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "How to find an item",
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Use the search box to search by item name, location, specific place, container, notes, or category."
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "How to edit or delete an item",
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Tap an item to view its details. From there, you can edit the information or delete the item."
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Version",
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Version 1.0"
        )

        Spacer(modifier = Modifier.height(30.dp))

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Button(
            onClick = onBackup,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("💾 BACK UP MY ITEMS")
        }
        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Button(
            onClick = onRestore,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("📂 RESTORE MY ITEMS")
        }

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("BACK TO HOME")
        }
    }
}
