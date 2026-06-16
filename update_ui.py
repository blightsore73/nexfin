import re

with open('app/src/main/java/com/jellyfin/client/MainActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

settings_screen_pattern = r'fun SettingsScreen\(.*?\)\s*\{.*?(?=\n@Composable\nfun ProfileSection)'
profile_section_pattern = r'@Composable\nfun ProfileSection\(.*?\)\s*\{.*?(?=\n@Composable\nfun |$)'

settings_replacement = """fun SettingsScreen(
    onLoginSuccess: (String, String, String) -> Unit,
    onLogout: () -> Unit,
    layoutMode: String,
    onLayoutModeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("JellyfinPrefs", android.content.Context.MODE_PRIVATE) }
    var isLayoutExpanded by remember { mutableStateOf(false) }
    var isSubtitleExpanded by remember { mutableStateOf(false) }

    var defaultSubtitleSize by remember { mutableStateOf(sharedPreferences.getFloat("default_subtitle_size", 18f)) }
    var defaultSubtitleEdgeType by remember { mutableStateOf(sharedPreferences.getString("default_subtitle_edge", androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE.toString())?.toIntOrNull() ?: androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .padding(top = 40.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Pengaturan",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 1. App Layout Configuration Section
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0x16FFFFFF)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().clickable { isLayoutExpanded = !isLayoutExpanded }.padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Pengaturan Layout Tampilan", color = Color(0xFF7B3FE4), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Icon(imageVector = if (isLayoutExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White)
                }
                AnimatedVisibility(visible = isLayoutExpanded) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLayoutModeChange("mobile") },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = layoutMode == "mobile" || layoutMode == "phone",
                                onClick = { onLayoutModeChange("mobile") },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF7B3FE4))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Mobile (Hanya Portrait)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text("Koleksi & menu akan dikunci tegak. Rotasi landscape hanya aktif saat memutar video.", color = Color.Gray, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLayoutModeChange("tablet") },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = layoutMode == "tablet",
                                onClick = { onLayoutModeChange("tablet") },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF7B3FE4))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Tablet (Hanya Landscape)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text("Seluruh halaman aplikasi dikunci landscape dengan navigasi sidebar.", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Subtitle Defaults Section
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0x16FFFFFF)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().clickable { isSubtitleExpanded = !isSubtitleExpanded }.padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Pengaturan Subtitle Bawaan", color = Color(0xFF7B3FE4), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Icon(imageVector = if (isSubtitleExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White)
                }
                AnimatedVisibility(visible = isSubtitleExpanded) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ukuran Font Bawaan:", color = Color.White, fontSize = 14.sp)
                            Text("${defaultSubtitleSize.toInt()} sp", color = Color(0xFF7B3FE4), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = defaultSubtitleSize,
                            onValueChange = { 
                                defaultSubtitleSize = it 
                                sharedPreferences.edit().putFloat("default_subtitle_size", it).apply()
                            },
                            valueRange = 12f..36f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF7B3FE4), activeTrackColor = Color(0xFF7B3FE4))
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Gaya Tepi Bawaan", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        val edgeOptions = listOf(
                            Pair("Garis", androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE),
                            Pair("Drop Shadow", androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW),
                            Pair("Timbul", androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_RAISED)
                        )
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            edgeOptions.forEach { (label, edgeType) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        defaultSubtitleEdgeType = edgeType
                                        sharedPreferences.edit().putString("default_subtitle_edge", edgeType.toString()).apply()
                                    }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (defaultSubtitleEdgeType == edgeType) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (defaultSubtitleEdgeType == edgeType) Color(0xFF7B3FE4) else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(text = label, color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 3. Profile Section Card
        ProfileSection(
            onLoginSuccess = onLoginSuccess,
            onLogout = onLogout
        )
        
        Spacer(modifier = Modifier.height(100.dp))
    }
}"""

profile_replacement = """@Composable
fun ProfileSection(
    onLoginSuccess: (String, String, String) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("JellyfinPrefs", android.content.Context.MODE_PRIVATE) }
    
    var serverUrl by remember { mutableStateOf(sharedPreferences.getString("server_url", "") ?: "") }
    var username by remember { mutableStateOf(sharedPreferences.getString("username", "") ?: "") }
    var password by remember { mutableStateOf("") }
    var isLoggedIn by remember { mutableStateOf(sharedPreferences.getBoolean("is_logged_in", false)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    var isProfileExpanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x16FFFFFF)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().clickable { isProfileExpanded = !isProfileExpanded }.padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isLoggedIn) "Detail Koneksi Nexfin" else "Login ke Nexfin", color = Color(0xFF7B3FE4), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Icon(imageVector = if (isProfileExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White)
            }
            AnimatedVisibility(visible = isProfileExpanded) {
                Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isLoggedIn) {
                        Text("Server URL:", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.align(Alignment.Start))
                        Text(serverUrl, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Start))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Username:", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.align(Alignment.Start))
                        Text(username, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Start))
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF4CAF50))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Terhubung ke Server", color = Color(0xFF4CAF50), fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                sharedPreferences.edit().apply {
                                    putBoolean("is_logged_in", false)
                                    putString("access_token", "")
                                    putString("user_id", "")
                                    apply()
                                }
                                isLoggedIn = false
                                password = ""
                                onLogout()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Keluar (Logout)", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("Nexfin Server URL", color = Color.Gray) },
                            placeholder = { Text("http://192.168.1.100:8096", color = Color.DarkGray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF7B3FE4),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedLabelColor = Color(0xFF7B3FE4),
                                unfocusedLabelColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username", color = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF7B3FE4),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedLabelColor = Color(0xFF7B3FE4),
                                unfocusedLabelColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password", color = Color.Gray) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF7B3FE4),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedLabelColor = Color(0xFF7B3FE4),
                                unfocusedLabelColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (serverUrl.isBlank() || username.isBlank()) {
                                    errorMessage = "URL dan Username wajib diisi"
                                    return@Button
                                }
                                isLoading = true
                                errorMessage = null
                                
                                val urlToUse = if (!serverUrl.startsWith("http")) "http://$serverUrl" else serverUrl
                                JellyfinService.login(urlToUse, username, password) { token, userId, error ->
                                    isLoading = false
                                    if (error != null) {
                                        errorMessage = error
                                    } else if (token != null && userId != null) {
                                        sharedPreferences.edit().apply {
                                            putString("server_url", urlToUse)
                                            putString("username", username)
                                            putString("access_token", token)
                                            putString("user_id", userId)
                                            putBoolean("is_logged_in", true)
                                            apply()
                                        }
                                        serverUrl = urlToUse
                                        isLoggedIn = true
                                        onLoginSuccess(urlToUse, token, userId)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B3FE4)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = "Login")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Masuk", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = errorMessage!!,
                                color = Color.Red,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}"""

content = re.sub(settings_screen_pattern, settings_replacement, content, flags=re.DOTALL)

# Need to find the end of ProfileSection properly
import sys
content_parts = content.split('@Composable\nfun ProfileSection(')
if len(content_parts) == 2:
    start_str = content_parts[0]
    rest_str = content_parts[1]
    # find the next @Composable or EOF
    next_idx = rest_str.find('@Composable')
    if next_idx != -1:
        end_str = rest_str[next_idx:]
    else:
        end_str = ""
    new_content = start_str + profile_replacement + "\n\n" + end_str
else:
    print("Error replacing ProfileSection")
    sys.exit(1)

with open('app/src/main/java/com/jellyfin/client/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(new_content)
