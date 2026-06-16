import re

with open('app/src/main/java/com/jellyfin/client/MainActivity.kt', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('streamUrl = local.streamUrl,', 'streamUrl = local.streamUrl ?: "",')
text = text.replace('visualTransformation = PasswordVisualTransformation()', 'visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()')
text = text.replace('keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)', 'keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password)')
text = text.replace('JellyfinService.login(urlToUse, username, password) { token, userId, error ->', 'JellyfinService.login(urlToUse, username, password) { token: String?, userId: String?, error: String? ->')

# Check onLoginSuccess signature
text = text.replace('onLoginSuccess(urlToUse, token, userId)', 'onLoginSuccess(urlToUse, token ?: "", userId ?: "")')

with open('app/src/main/java/com/jellyfin/client/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(text)
print('Fixed minor errors')
