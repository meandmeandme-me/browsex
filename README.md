# BrowseX

متصفح أندرويد مبني على محرك Firefox (GeckoView) مع uBlock Origin.

## البناء
أي رفع (push) على فرع main يشغّل البناء تلقائياً في تبويب Actions.
بعد نجاحه: افتح البناء، انزل لقسم Artifacts، ونزّل BrowseX-debug-apk.

## لو فشل البناء
انسخ آخر 40 سطر من الخطأ الأحمر وأرسله.
إذا كان الخطأ عن حزمة GeckoView، جرّب في app/build.gradle.kts استبدال
`geckoview-omni` بـ `geckoview`.
