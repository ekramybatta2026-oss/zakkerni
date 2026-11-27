# Ultimate IPTV — تطبيق IPTV رياضي لأندرويد وAndroid TV

مشغّل IPTV احترافي (Kotlin + Jetpack Compose + Media3 ExoPlayer) لمشاهدة القنوات والمباريات من **اشتراكاتك المرخّصة**.
التطبيق لا يحتوي ولا يبيع أي قنوات.

- التصميم المعماري الكامل: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- دليل الاستخدام والإعداد: [`docs/SETUP.md`](docs/SETUP.md)

## المميزات

| القسم | ما يقدمه |
|---|---|
| الاشتراكات | Xtream Codes، رابط M3U/M3U8، ملف M3U من الجهاز، عدة اشتراكات، اختبار الاتصال، حالة الحساب وتاريخ الانتهاء، تحديث تلقائي، تشفير البيانات بـ Keystore |
| القنوات | مجموعات، قنوات رياضية مصنفة تلقائيًا، البرنامج الحالي مع شريط التقدم، المفضلة، آخر المشاهدات، بحث |
| EPG | XMLTV و`.xml.gz`، الحالي/التالي، جدول يومي لكل قناة، ربط تلقائي بالاسم |
| المشغل | HLS، DASH، MPEG-TS، اختيار الجودة/الصوت/الترجمة، نسبة العرض، السطوع والصوت بالإيماءات، إعادة اتصال تلقائية، قائمة القنوات أثناء المشاهدة، PiP، مشغل خارجي، Widevine عبر خادم ترخيص المزود |
| المباريات | أمس/اليوم/غدًا +5 أيام، مباشر/قادمة/منتهية، فلترة بالبطولة والدولة والفرق المفضلة، التوقيت المحلي، النتيجة والدقيقة، "أين تشاهد" من قنواتك |
| البطولات | الدوريات الخمس الكبرى، أبطال أوروبا، الدوري الأوروبي، أبطال أفريقيا، الكونفدرالية، كأس العالم، أمم أفريقيا، كأس آسيا، كأس العالم للأندية + بطولات أخرى |
| الإشعارات | قبل المباراة (5–60 دقيقة)، عند البداية، تذكير تلقائي لمباريات الفرق المفضلة، قابلة للتعطيل |
| اللغة | العربية (RTL) والإنجليزية، تغيير اللغة من داخل التطبيق |
| التلفاز | APK واحد، Banner، تنقل كامل بالريموت |

## البناء

المتطلبات: Android Studio (Ladybug أو أحدث) أو JDK 17 + Android SDK 36.

```bash
cd iptv
./gradlew testDebugUnitTest        # اختبارات المحللات
./gradlew assembleDebug            # APK للتجربة: app/build/outputs/apk/debug/
./gradlew assembleRelease bundleRelease
```

كل دفعة (push) على الفرع تُبنى تلقائيًا عبر GitHub Actions (`.github/workflows/iptv.yml`):
اختبارات الوحدات ← Lint ← APK/AAB ← اختبار كامل على محاكي Android (تشغيل بث HLS حقيقي).

### التوقيع للنشر

بدون مفاتيح، تُوقع نسخة Release بمفتاح "sideload" الموجود في المستودع (للتثبيت المباشر فقط).
للنشر على Google Play:

```bash
keytool -genkeypair -v -keystore release.jks -alias iptv -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks   # انسخ الناتج
```

ثم أضف في GitHub ← Settings ← Secrets: `IPTV_KS_B64` و`IPTV_KS_PASS` و`IPTV_KS_ALIAS` (واختياريًا `IPTV_KEY_PASS`).
محليًا: `IPTV_KS_FILE=/path/release.jks IPTV_KS_PASS=... IPTV_KS_ALIAS=iptv ./gradlew bundleRelease`.

## البيانات التجريبية (قانونية)

زر **"تجربة القنوات التجريبية القانونية"** يضيف `assets/demo/demo_playlist.m3u`: بث اختباري عام من Unified Streaming وAkamai وApple وMux ومحتوى Widevine الاختباري من Google، مع دليل برامج مُولّد على الجهاز. مزود المباريات "Demo" يعرض مباريات افتراضية مُعلَّمة بوضوح بـ "تجريبي".
