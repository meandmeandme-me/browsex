# BrowseX — تثبيت APK واستكشاف الأخطاء

## اختيار الحزمة

لا تحتاج إلى تنزيل كل الملفات. نزّل artifact واحداً فقط من GitHub Actions:

| Artifact | الاستخدام |
|---|---|
| `browsex-armeabi-v7a-debug` | نظام Android ‏32-bit |
| `browsex-arm64-v8a-debug` | نظام Android ‏64-bit |
| `browsex-universal-debug` | لا تعرف معمارية الجهاز؛ يتضمن ARM32 وARM64 وحجمه أكبر |

يمكن معرفة ABI عبر ADB:

```bash
adb shell getprop ro.product.cpu.abi
```

القيمة `armeabi-v7a` تعني حزمة ARM32، والقيمة `arm64-v8a` تعني حزمة ARM64.

## خطوات التثبيت

1. افتح صفحة GitHub Actions لآخر تشغيل ناجح على فرع `rewrite-v2`.
2. نزّل artifact المناسب فقط.
3. فك ضغط ZIP على الهاتف أو الكمبيوتر.
4. ثبّت ملف APK بداخله، وليس ZIP.
5. اسمح بالتثبيت من المصدر الذي استخدمته، إذا طلب Android ذلك.

## حل رسائل الفشل

### INSTALL_FAILED_UPDATE_INCOMPATIBLE

هذا يعني غالباً أن التطبيق المثبت وAPK الجديد موقعان بشهادتين مختلفتين. Android لا يسمح بتحديث التطبيق بشهادة مختلفة.

GitHub-hosted runners مؤقتة، وGradle ينشئ debug keystore محلياً للبيئة. لذلك لا يمكن ضمان أن Debug APK من تشغيل جديد سيحدّث نسخة Debug من تشغيل سابق. الحل الصحيح هو keystore ثابت وآمن يملكه صاحب المشروع، ثم استخدامه لكل إصدار. لا نضع المفتاح الخاص أو كلمة مروره في المستودع ولا ننشئ مفتاح نشر عشوائياً نيابة عن صاحب المشروع.

**لا تحذف النسخة القديمة أو بياناتها لمجرد تجاوز هذه الرسالة.** استخدم نفس مفتاح التوقيع الذي بنيت به النسخة المثبتة، أو خطط لترحيل إصدار موقّع بمفتاح ثابت.

### INSTALL_FAILED_NO_MATCHING_ABIS

الحزمة لا تحتوي ABI الذي يتطلبه نظام الهاتف. جرّب حزمة ARM32 أو ARM64 الصحيحة؛ universal هو الخيار الاحتياطي الأكبر حجماً.

### INSTALL_PARSE_FAILED أو App not installed

تأكد أنك فككت ZIP وثبّت ملف `.apk` نفسه. تأكد أيضاً من عدم تلف التنزيل ومن أن Android 8 أو أحدث يعمل على الجهاز.

### INSTALL_FAILED_VERSION_DOWNGRADE

رقم إصدار APK أقل من رقم الإصدار المثبت. يجب زيادة `versionCode` قبل نشر ترقية؛ لا تتجاوز ذلك بحذف بيانات المستخدم.

## التوقيع الحالي وحدود الإصدار

الـworkflow الحالي ينتج Debug APKs ويتحقق من سلامة التوقيع والـABI والـSHA-256. هذا يثبت أن APK صالح وموقّع، لكنه لا يثبت أن شهادة Debug ستبقى نفسها في تشغيل مستقبلي. إصدار التحديث للمستخدمين يحتاج مفتاح Release ثابتاً، محفوظاً في مكان آمن، وإعداد CI لاستخدامه.


## إعداد توقيع Debug ثابت لتحديثات CI المستقبلية

هذا إعداد اختياري لكنه موصى به حتى لا تتغير شهادة APK بين تشغيلات GitHub Actions. أنشئ مفتاحاً واحداً على جهازك واحتفظ بنسخة احتياطية آمنة:

```bash
keytool -genkeypair -v -keystore browsex-debug.keystore -storetype PKCS12 -alias browsex-debug -keyalg RSA -keysize 2048 -validity 10000
```

حوّل ملف keystore إلى Base64. على Linux/macOS:

```bash
base64 -w 0 browsex-debug.keystore
```

على PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("browsex-debug.keystore"))
```

في GitHub افتح **Settings → Secrets and variables → Actions** وأضف الأسرار التالية:

- `BROWSEX_DEBUG_KEYSTORE_BASE64`: ناتج Base64 الكامل.
- `BROWSEX_DEBUG_STORE_PASSWORD`: كلمة مرور keystore.
- `BROWSEX_DEBUG_KEY_ALIAS`: استخدم `browsex-debug` إذا أبقيت الاسم كما في الأمر.
- `BROWSEX_DEBUG_KEY_PASSWORD`: كلمة مرور المفتاح.

بعدها سيستخدم CI الشهادة نفسها في كل Debug build. لا ترفع ملف keystore أو كلمات المرور إلى Git. عند فقدان المفتاح لن تستطيع تحديث APKs الموقّعة به. هذا المفتاح Debug للاختبار فقط؛ إصدار النشر العام يحتاج مفتاح Release مستقل وسياسة حفظ واستعادة خاصة به.

**تنبيه ترحيل:** إضافة مفتاح ثابت الآن لا تغيّر توقيع APKs القديمة التي بُنيت بمفاتيح runners المؤقتة. APK الجديد لن يحدّث تثبيتاً قديماً إلا إذا كان المفتاح هو نفسه المفتاح الذي وقّع ذلك التثبيت. لا تحذف التطبيق القديم وبياناته قبل اتخاذ قرار الترحيل.
