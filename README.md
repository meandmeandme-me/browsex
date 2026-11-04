# BrowseX

متصفح Android مبني على Kotlin وMozilla GeckoView. المشروع حالياً في مرحلة إعادة بناء تدريجية على الفرع `rewrite-v2`.

## حالة المشروع

- شاشة البداية ومساحة التبويبات الأساسية قيد التطوير.
- الإشارات المرجعية والسجل محفوظان محلياً.
- خيار Downloads الحالي يفتح مدير تنزيلات Android؛ اعتراض تنزيلات المواقع وواجهة التنزيلات داخل BrowseX لم يكتمل بعد.
- Reader mode، شاشة الإعدادات الكاملة، تنبيهات المواقع وتخطيطات الأجهزة اللوحية/الوضع الأفقي ما زالت ضمن خطة التنفيذ.
- مانع الإعلانات مؤجل ولا ينبغي اعتبار زر أو إعداد الحماية دليلاً على حجب فعلي.

## البناء محلياً

المتطلبات: JDK 17 وAndroid SDK 36 وGradle 8.11.1.

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

ملفات APK تظهر تحت `app/build/outputs/apk/`.

## الفروع وCI

- `main`: لا تغييرات تجريبية مباشرة.
- `rewrite-v2`: التطوير والفحص.
- كل build يجب ربطه بـ commit SHA محدد، والتحقق من نجاح Actions قبل مشاركة APK.
- لا تستخدم توقيع debug متغيراً كآلية تحديث للمستخدم؛ جهّز مفتاح release ثابتاً قبل النشر العام.

## توثيق التطوير

- [Architecture](docs/ARCHITECTURE.md)
- [Project tree and dependency contracts](docs/PROJECT_TREE.md)
- [Roadmap](docs/ROADMAP.md)
- [QA checklist](docs/QA_CHECKLIST.md)

التوثيق يميز بين ما يعمل حالياً وما لم يكتمل؛ لا تعتبر عناصر الخطة ميزات منفذة.
