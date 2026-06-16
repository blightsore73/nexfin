import re

with open('app/src/main/java/com/jellyfin/client/MainActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# First, fix the 3 extra closing braces before data class LocalResumeItem
content = content.replace('}\n}\n}\n}\n\ndata class LocalResumeItem(', '}\n\ndata class LocalResumeItem(')

# Now replace from LocalResumeItem to NotLoggedInStub
start_idx = content.find('data class LocalResumeItem(')
end_idx = content.find('@Composable\nfun NotLoggedInStub', start_idx)

if start_idx != -1 and end_idx != -1:
    new_manager = """data class LocalResumeItem(
    val id: String,
    val name: String,
    val type: String,
    val imageUrl: String,
    val streamUrl: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val timestamp: Long = 0
)

object LocalResumeManager {
    fun saveLocalProgress(
        context: android.content.Context,
        itemId: String,
        name: String,
        type: String,
        imageUrl: String,
        streamUrl: String,
        positionMs: Long,
        durationMs: Long
    ) {
        val prefs = context.getSharedPreferences("LocalResumePrefs", android.content.Context.MODE_PRIVATE)
        val gson = com.google.gson.Gson()
        
        if (positionMs < 5000L) {
            removeLocalProgress(context, itemId)
            return
        }
        
        val items = getLocalResumeItems(context).toMutableList()
        val existingIndex = items.indexOfFirst { it.id == itemId }
        val newItem = LocalResumeItem(itemId, name, type, imageUrl, streamUrl, positionMs, durationMs, System.currentTimeMillis())
        
        if (existingIndex != -1) {
            items[existingIndex] = newItem
        } else {
            items.add(0, newItem)
        }
        
        prefs.edit().putString("resume_items", gson.toJson(items.take(20))).apply()
    }

    fun removeLocalProgress(context: android.content.Context, itemId: String) {
        val prefs = context.getSharedPreferences("LocalResumePrefs", android.content.Context.MODE_PRIVATE)
        val items = getLocalResumeItems(context).toMutableList()
        items.removeAll { it.id == itemId }
        prefs.edit().putString("resume_items", com.google.gson.Gson().toJson(items)).apply()
    }

    fun getLocalResumeItems(context: android.content.Context): List<LocalResumeItem> {
        val prefs = context.getSharedPreferences("LocalResumePrefs", android.content.Context.MODE_PRIVATE)
        val itemsJson = prefs.getString("resume_items", null)
        if (itemsJson.isNullOrEmpty()) return emptyList()
        
        val type = object : com.google.gson.reflect.TypeToken<List<LocalResumeItem>>() {}.type
        return try {
            com.google.gson.Gson().fromJson(itemsJson, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

"""
    new_content = content[:start_idx] + new_manager + content[end_idx:]
    with open('app/src/main/java/com/jellyfin/client/MainActivity.kt', 'w', encoding='utf-8') as f:
        f.write(new_content)
    print('Replaced LocalResumeManager successfully')
else:
    print('Indices not found')
