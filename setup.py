import os 
import pathlib 
 
BASE = pathlib.Path("C:/Users/Windows 10/dmvp-v3") 
 
folders = [ 
    "dmvp-backend/prisma", 
    "dmvp-backend/src/config", 
    "dmvp-backend/src/middleware", 
    "dmvp-backend/src/routes", 
    "dmvp-backend/src/services", 
    "dmvp-backend/src/utils", 
    "dmvp-android/app/src/main/java/com/dmvp/app/data/model", 
    "dmvp-android/app/src/main/java/com/dmvp/app/data/repository", 
    "dmvp-android/app/src/main/java/com/dmvp/app/data/remote", 
    "dmvp-android/app/src/main/java/com/dmvp/app/security", 
    "dmvp-android/app/src/main/java/com/dmvp/app/utils", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/screens", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/components", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/theme", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/viewmodel", 
    "dmvp-android/app/src/main/java/com/dmvp/app/navigation", 
    "dmvp-android/app/src/main/res/values", 
] 
 
files = [ 
    "README.md", 
    ".gitignore", 
    "dmvp-backend/package.json", 
    "dmvp-backend/.env.example", 
    "dmvp-backend/prisma/schema.prisma", 
    "dmvp-backend/src/config/database.js", 
    "dmvp-backend/src/app.js", 
    "dmvp-backend/src/middleware/rateLimit.js", 
    "dmvp-backend/src/middleware/validation.js", 
    "dmvp-backend/src/middleware/auth.js", 
    "dmvp-backend/src/middleware/signatureVerify.js", 
    "dmvp-backend/src/utils/hashUtils.js", 
    "dmvp-backend/src/services/evidenceService.js", 
    "dmvp-backend/src/routes/evidence.js", 
    "dmvp-backend/src/services/verifyService.js", 
    "dmvp-backend/src/routes/verify.js", 
    "dmvp-backend/src/services/searchService.js", 
    "dmvp-backend/src/routes/search.js", 
    "dmvp-backend/src/services/deviceService.js", 
    "dmvp-backend/src/routes/devices.js", 
    "dmvp-backend/src/routes/ownership.js", 
    "dmvp-android/app/build.gradle.kts", 
    "dmvp-android/app/src/main/AndroidManifest.xml", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/theme/Color.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/theme/Theme.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/theme/Type.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/data/model/CEE.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/data/model/MultiAxisVerdict.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/data/model/DeviceTrust.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/data/model/RobustFingerprint.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/data/model/ApiResponses.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/security/HashUtils.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/security/DeviceKeyManager.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/security/FingerprintUtils.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/security/SignatureUtils.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/utils/CEEBuilder.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/utils/Constants.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/utils/Extensions.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/data/remote/ApiService.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/data/remote/RetrofitClient.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/data/repository/DMVPRepository.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/viewmodel/CaptureViewModel.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/viewmodel/VerifyViewModel.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/viewmodel/RegisterViewModel.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/viewmodel/SearchViewModel.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/viewmodel/DeviceViewModel.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/navigation/NavGraph.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/components/VerdictCard.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/components/MediaPicker.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/components/TrustTierBadge.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/components/CEEPreview.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/components/LoadingOverlay.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/screens/HomeScreen.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/screens/CaptureScreen.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/screens/RegisterScreen.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/screens/VerifyScreen.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/screens/SearchScreen.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/ui/screens/DeviceScreen.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/MainActivity.kt", 
    "dmvp-android/app/src/main/java/com/dmvp/app/DMVPApplication.kt", 
    "dmvp-android/app/src/main/res/values/colors.xml", 
    "dmvp-android/app/src/main/res/values/strings.xml", 
] 
 
for folder in folders: 
    path = BASE / folder 
    path.mkdir(parents=True, exist_ok=True) 
    print(f"  [+] Folder: {path}") 
 
for file in files: 
    path = BASE / file 
    path.parent.mkdir(parents=True, exist_ok=True) 
    if not path.exists(): 
        path.write_text("", encoding="utf-8") 
    print(f"  [+] File: {path}") 
 
print("Done! All folders and files created.") 
