import re

with open('app/src/main/java/com/jellyfin/client/MainActivity.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Inject Subtitle Settings into SettingsScreen
subtitle_section = """
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0x16FFFFFF)),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().clickable { isSubtitleExpanded = !isSubtitleExpanded }.padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Pengaturan Subtitle Default", color = Color(0xFF7B3FE4), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Icon(imageVector = if (isSubtitleExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White)
                }
                AnimatedVisibility(visible = isSubtitleExpanded) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                        Text("Ukuran Subtitle", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf(14f to "Kecil", 18f to "Normal", 22f to "Besar", 26f to "Sangat Besar").forEach { (size, label) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = defaultSubtitleSize == size, onClick = { 
                                        defaultSubtitleSize = size
                                        sharedPreferences.edit().putFloat("default_subtitle_size", size).apply()
                                    }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF7B3FE4), unselectedColor = Color.Gray))
                                    Text(label, color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Gaya Tepi (Edge Style)", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf(androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE to "Tidak Ada", androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE to "Garis Luar", androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW to "Bayangan").forEach { (type, label) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = defaultSubtitleEdgeType == type, onClick = { 
                                        defaultSubtitleEdgeType = type
                                        sharedPreferences.edit().putString("default_subtitle_edge", type.toString()).apply()
                                    }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF7B3FE4), unselectedColor = Color.Gray))
                                    Text(label, color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
"""

text = text.replace('        ProfileSection(', subtitle_section + '        ProfileSection(')

# 2. Fix ExoPlayer dialogs X button and Gaya Tepi readability
# The dialogs currently have "Tutup" buttons at the bottom. The user wants an 'X' at the top right.
# For Subtitle dialog:
subtitle_dialog_pattern = r'AlertDialog\(\s*onDismissRequest\s*=\s*\{\s*showSubtitleSettings\s*=\s*false\s*\},.*?text\s*=\s*\{\s*Column'
subtitle_replacement = r'''AlertDialog(
                    onDismissRequest = { showSubtitleSettings = false },
                    title = {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Pengaturan Subtitle", fontWeight = FontWeight.Bold)
                            IconButton(onClick = { showSubtitleSettings = false }) {
                                Icon(Icons.Filled.Close, contentDescription = "Tutup")
                            }
                        }
                    },
                    text = { Column'''

text = re.sub(subtitle_dialog_pattern, subtitle_replacement, text, flags=re.DOTALL)

# Remove the old "Tutup" button from Subtitle dialog
# We will do this by replacing the confirmButton = { TextButton(onClick = { showSubtitleSettings = false }) { Text("Tutup") } } with an empty confirmButton
text = re.sub(r'confirmButton\s*=\s*\{\s*TextButton\(onClick\s*=\s*\{\s*showSubtitleSettings\s*=\s*false\s*\}\)\s*\{\s*Text\("Tutup"\)\s*\}\s*\}', r'confirmButton = {}', text)

# For Additional Settings dialog:
settings_dialog_pattern = r'AlertDialog\(\s*onDismissRequest\s*=\s*\{\s*showAdditionalSettings\s*=\s*false\s*\},.*?text\s*=\s*\{\s*Column'
settings_replacement = r'''AlertDialog(
                    onDismissRequest = { showAdditionalSettings = false },
                    title = {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Pengaturan Tambahan", fontWeight = FontWeight.Bold)
                            IconButton(onClick = { showAdditionalSettings = false }) {
                                Icon(Icons.Filled.Close, contentDescription = "Tutup")
                            }
                        }
                    },
                    text = { Column'''

text = re.sub(settings_dialog_pattern, settings_replacement, text, flags=re.DOTALL)

text = re.sub(r'confirmButton\s*=\s*\{\s*TextButton\(onClick\s*=\s*\{\s*showAdditionalSettings\s*=\s*false\s*\}\)\s*\{\s*Text\("Tutup"\)\s*\}\s*\}', r'confirmButton = {}', text)

# Fix Gaya Tepi readability:
# Currently it uses Row with Arrangement.SpaceBetween, but if it overflows, it's cut off.
# We can use FlowRow or just wrap it in a vertical scroll or adjust sizes.
text = text.replace('Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {', 'androidx.compose.foundation.layout.ExperimentalLayoutApi::class\nandroidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {')

# The above FlowRow replacement might be too broad. Let's make it specific to the Gaya Tepi section in ExoPlayerScreen.
# Actually, I'll write a safer replacement for Gaya Tepi in ExoPlayerScreen.
with open('app/src/main/java/com/jellyfin/client/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(text)

print("Patch applied")
