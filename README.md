# BrowseX

متصفح Android مبني على Kotlin وMozilla GeckoView. التطوير النشط على الفرع `rewrite-v2`، مع إبقاء `main` دون تغييرات تجريبية مباشرة.

## الموجود في الفرع

- متصفح GeckoView مع شريط العنوان، التنقل، التبويبات وحد 20 تبويباً، وقبول أسماء النطاقات الدولية (IDN) مع التحقق من السلطة والمنفذ ورفض بيانات الاعتماد.
- حفظ واستعادة التبويبات العادية؛ التبويبات الخاصة تستخدم Gecko private mode، ولا تدخل في السجل أو لقطة الاستعادة، وتُغلق بياناتها عند إغلاقها.
- صفحة بداية محلية، بحث، مفضلة، سجل، BrowseX Recall للبحث الموحد في التبويبات العادية والمفضلة والسجل، Reader mode، مشاركة، بحث داخل الصفحة ومعلومات الموقع.
- أذونات الموقع عبر GeckoView مع فصل موافقة أندرويد عن موافقة الموقع؛ تتضمن الكاميرا والميكروفون والموقع.
- مركز خصوصية لمسح ملفات الارتباط وتخزين المواقع والذاكرة المؤقتة وأذونات المواقع وجلسات المصادقة، مع إغلاق الجلسات وإعادة فتح التبويبات بعد المسح، ومسح بيانات المضيف الحالي من معلومات الموقع.
- سجل محلي يحفظ عنوان الصفحة واسمها ووقت زيارتها، مع ترحيل تلقائي من سجل الروابط القديم.
- إشعارات المواقع عبر NotificationManager وقناة أندرويد، مع طلب إذن Android 13+، وأحداث الضغط والإغلاق.
- إعدادات Light/Dark/System والعربية/الإنجليزية، واتجاه RTL للشاشات الأساسية وصفحة البداية.
- تنزيلات DownloadManager مع الحالات والأسباب والتقدم والإلغاء، وتنزيلات Gecko المباشرة مع حفظ جسم الاستجابة الأصلي، وتقدم البايتات وإلغاء التنزيل وحذف الملف الجزئي عند الإلغاء.
- التنزيلات المباشرة غير قابلة للاستكمال تلقائيًا بعد موت العملية، ولا نعد بدعم Resume للطلبات أحادية الاستخدام أو POST والمصادقة.
- مدير كلمات مرور محلي مشفر بـAndroid Keystore، مع تعبئة داخل GeckoView وخدمة Android Autofill اختيارية للتطبيقات الأخرى: تتطلب موافقة المستخدم، تحقق الجهاز، واختيار الحساب، وتملأ HTTPS فقط بتطابق أصل دقيق. الخدمة Fill-only ولا تجمع كلمات مرور من التطبيقات الأخرى.
- طبقة Room 2.8.5 ومخطط v1 ومستورد غير هدّام لنسخة SharedPreferences موجودة كمرحلة تحضيرية، مع اختبارات Robolectric/SQLite للاستيراد وإعادة المحاولة والتراجع؛ ما زالت SharedPreferences مصدر التخزين التشغيلي ولم يُفعّل تحويل القراءة/الكتابة إلى Room أو DataStore بعد.
- أساس OIDC Authorization Code + PKCE مختبر: إنشاء S256/state/nonce وبناء رابط HTTPS والتحقق الصارم من callback؛ لا يوجد تسجيل دخول Google أو Microsoft فعلي أو تبادل tokens أو مزامنة سحابية حتى الآن.
- اختبارات URL واستعادة التبويبات، وCI لاختبارات JVM وAndroid Lint وبناء APK والتحقق من التوقيع والـABI والـSHA-256.

## حدود يجب معرفتها

- البناء الحالي من نوع Debug وليس إصدار Release موقعاً بمفتاح ثابت.
- GitHub Actions يعمل على runner مؤقت. أُضيف دعم اختياري لـkeystore Debug ثابت عبر GitHub Secrets (`BROWSEX_DEBUG_*`) حتى تستخدم التشغيلات المستقبلية الشهادة نفسها. من دون هذه الأسرار سيبني CI بشهادة مؤقتة، وقد يرفض Android APK الجديد كتحديث فوق تثبيت سابق. طريقة إنشاء المفتاح وإعداد الأسرار موضحة في [دليل التثبيت](docs/INSTALLING.md). لا تضع keystore أو كلمات المرور داخل المستودع.
- DownloadManager يعيد جلب الرابط؛ لذلك لا نضمن نقل POST body أو كل رؤوس Authorization أو الطلبات المؤقتة كما أرسلها GeckoView. تظهر هذه الحدود للمستخدم بدلاً من ادعاء تطابق التنزيل.
- منح إذن persistent storage للموقع لا يمكن إلغاؤه من GeckoView بعد منحه.
- تم ربط uBlock Origin عبر GeckoView WebExtensions: يُنزّل ملف XPI الرسمي الموقّع من Mozilla Add-ons عند موافقة المستخدم، ولا يُضمَّن كود الإضافة داخل APK. يتوفر زر لفتح نافذة uBlock والتحكم بالموقع الحالي، مع خيار مستقل للسماح بها في التصفح الخاص. التوافق التكيفي يعتمد على قواعد uBlock المضادة لرسائل اكتشاف الحجب، ويمكن إيقاف الحجب للموقع المتعطل وحده من زر الطاقة داخل الإضافة؛ لا يوجد ادعاء بأن كل موقع أو كل رسالة anti-adblock تُكتشف تلقائيًا. ما زال اختبار التثبيت والنافذة وسلوك المواقع على جهاز فعلي مطلوبًا.
- نجاح CI لا يثبت اختبار الهاتف الفعلي. ما زال اختبار Android 10 والأجهزة منخفضة الذاكرة وتدفق الأذونات والإشعارات مطلوباً.

## التثبيت من GitHub Actions

افتح آخر تشغيل ناجح على `rewrite-v2`، ثم نزّل artifact واحداً فقط:
- `browsex-armeabi-v7a-debug` لأجهزة Android ذات ABI ‏32-bit.
- `browsex-arm64-v8a-debug` لأجهزة Android ذات ABI ‏64-bit.
- `browsex-universal-debug` إذا لم تعرف ABI؛ حجمه أكبر لأنه يحتوي معماريتي ARM.

ملف artifact ZIP ليس APK. فك الضغط أولاً ثم ثبّت ملف `.apk` الموجود داخله. لا تنزّل الحزم الثلاث معاً على الهاتف.

الأخطاء الشائعة:
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: شهادة APK الجديد مختلفة عن شهادة التطبيق المثبت. لا تحذف التطبيق وبياناته لتجاوز المشكلة؛ يلزم البناء بمفتاح التوقيع الأصلي نفسه.
- `INSTALL_FAILED_NO_MATCHING_ABIS`: اختر حزمة ABI المناسبة أو universal.
- `INSTALL_PARSE_FAILED` أو رسالة أن الحزمة غير صالحة: غالباً جرت محاولة تثبيت ZIP بدلاً من فك ضغطه وتثبيت APK.
- Android 7 أو أقدم غير مدعوم؛ الحد الأدنى API 26 (Android 8).

## البناء محلياً

المتطلبات: JDK 17 وAndroid SDK 36 وGradle 8.13. المشروع يستخدم AGP 8.13.2 وKotlin 2.3.20 وKSP 2.3.12.

```bash
gradle testDebugUnitTest lintDebug assembleDebug
```

ملفات APK تظهر تحت `app/build/outputs/apk/debug/`.

## الفروع والتوثيق

- `main`: لا تغييرات تجريبية مباشرة.
- `rewrite-v2`: التطوير والفحص.
- [طريقة التثبيت واستكشاف أخطاء APK](docs/INSTALLING.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Project tree and dependency contracts](docs/PROJECT_TREE.md)
- [Roadmap](docs/ROADMAP.md)
- [QA checklist](docs/QA_CHECKLIST.md)


## Product and architecture planning

- [Open-source browser audit and product gaps](docs/OPEN_SOURCE_BROWSER_AUDIT.md) — source-tree findings, competitor patterns and recommended rebuild priorities.
- [Localization workflow](docs/LOCALIZATION.md) — BCP 47 locale packs, RTL and translation quality gates.
- [Storage migration decision](docs/decisions/ADR-001-storage-boundaries.md) — preserve legacy data while moving to focused stores.
- [Browser engine decision](docs/decisions/ADR-002-browser-engine.md) — GeckoView is provisional and subject to capability review.
- [Release signing](docs/release/RELEASE_SIGNING.md) — secure signing identity and optional signed-APK CI path.
- [World-browser product requirements](docs/WORLD_BROWSER_PRODUCT_REQUIREMENTS.md) — competitor research, user-behavior evidence, global requirements and Uizard brief.
- [Account and sync foundation](docs/ACCOUNT_SYNC_FOUNDATION.md) — provider-neutral identity, security boundaries and opt-in sync design.
- [Uizard screen map](docs/design/UIDIZARD_SCREEN_MAP.md) — responsive screens, interaction flows and required UI states.
- [Implementation roadmap](docs/ROADMAP.md) — verified implementation versus remaining device QA.
## البنية المستقبلية للحسابات والمنصات

- أساس نماذج محايدة لمزودي الحسابات والمزامنة في [AccountDomainModels.kt](app/src/main/java/com/browsex/app/AccountDomainModels.kt)؛ لا يوجد تسجيل دخول أو خادم مزامنة فعلي بعد.
- [أساس الحسابات والمزامنة](docs/ACCOUNT_SYNC_FOUNDATION.md) يحدد الموافقات المنفصلة والتشفير وإدارة الأجهزة وحذف البيانات.
- [خريطة واجهات الحساب والمزامنة](docs/ACCOUNT_SYNC_UX_MAP.md) جاهزة كمرجع لتصميم الشاشات.
- [معمارية المنصات المستقبلية](docs/MULTIPLATFORM_ARCHITECTURE.md) تحدد مسار Windows وmacOS وLinux وAndroid TV مع إبقاء محرك كل منصة خلف adapter.
## دراسة المتصفحات مفتوحة المصدر

- [نتائج دراسة Firefox وBrave وCromite وDuckDuckGo وقرارات الاستفادة والتراخيص](docs/OPEN_SOURCE_BROWSER_RESEARCH.md).
- [خطة ترحيل التخزين الآمن إلى Room وDataStore](docs/STORAGE_MIGRATION_PLAN.md) — Room schema ومستورِد snapshot جاهزان، لكن محرك التخزين التشغيلي ما زال SharedPreferences.
- [تقييم واعتماد اتجاه أولي لخادم الحسابات والمزامنة](docs/decisions/ADR-004-account-sync-backend.md) — Supabase مرشح موصى به، وليس خدمة منشورة أو مهيأة بعد.
