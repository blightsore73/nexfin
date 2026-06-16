import re

with open('app/src/main/java/com/jellyfin/client/MainActivity.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Remove the old 'Pengaturan Subtitle Bawaan' card completely.
pattern = r'// 2\. Subtitle Defaults Section.*?// 3\. Profile Section Card'
text = re.sub(pattern, '// 3. Profile Section Card', text, flags=re.DOTALL)

# 2. Add 'nexfin V.1.3' at the bottom of SettingsScreen
settings_bottom_pattern = r'ProfileSection\(\s*onLoginSuccess = onLoginSuccess,\s*onLogout = onLogout\s*\)\s*Spacer\(modifier = Modifier\.height\(100\.dp\)\)\s*\}'
replacement = """ProfileSection(
            onLoginSuccess = onLoginSuccess,
            onLogout = onLogout
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "nexfin V.1.3",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 100.dp)
        )
    }"""
text = re.sub(settings_bottom_pattern, replacement, text, flags=re.DOTALL)

with open('app/src/main/java/com/jellyfin/client/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(text)
print('Patch applied successfully.')
