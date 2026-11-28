# Ultimate IPTV — التصميم المعماري (المرحلة الأولى)

## 1. التقنيات المختارة ولماذا

| الطبقة | التقنية | الإصدار | سبب الاختيار |
|---|---|---|---|
| اللغة | Kotlin | 2.1.21 | لغة Android الرسمية، Coroutines مدمجة |
| البناء | AGP / Gradle | 8.10.1 / 8.14.3 | متوافق مع compileSdk 36 |
| الواجهة | Jetpack Compose + Material 3 | BOM 2025.05.01 | واجهة واحدة للهاتف والتلفاز، دعم RTL تلقائي |
| المعمارية | MVVM + Repository | — | فصل واضح بين الواجهة والبيانات وقابلية الاختبار |
| حقن الاعتماديات | Hilt | 2.56.2 | ViewModels وWorkers وReceivers |
| قاعدة البيانات | Room + Paging 3 | 2.7.1 / 3.3.6 | قوائم قنوات بعشرات الآلاف دون استهلاك الذاكرة |
| الشبكة | OkHttp + Retrofit + kotlinx.serialization | 4.12 / 2.11 / 1.8.1 | عميل HTTP واحد مشترك (Connection pool) للمشغل والقوائم والصور |
| المشغل | AndroidX Media3 ExoPlayer | 1.6.1 | HLS وDASH وMPEG-TS، اختيار المسارات، DRM رسمي |
| الصور | Coil (+SVG) | 2.7.0 | شعارات القنوات والفرق، يستخدم نفس OkHttp |
| الإعدادات | DataStore | 1.1.7 | بديل SharedPreferences غير متزامن |
| المهام الخلفية | WorkManager + AlarmManager | 2.10.1 | تحديث القوائم/EPG، تذكيرات المباريات في وقتها |
| الأمان | Android Keystore (AES-256-GCM) | — | تشفير بيانات الاشتراكات ومفاتيح API |

## 2. هيكل المشروع

```
iptv/app/src/main/java/com/ultimateiptv/sports/
├── App.kt, MainActivity.kt        نقطة البداية، Splash، WorkManager، Coil
├── core/
│   ├── parser/   M3uParser, XmltvParser, XtreamParsers   (Kotlin خالص بدون Android → مختبر بوحدات اختبار)
│   ├── network/  HttpFetcher, ErrorMapper                رسائل أخطاء مفهومة للمستخدم
│   ├── security/ CredentialCipher                        Keystore
│   └── util/     TextUtils, SportsClassifier             تطبيع الأسماء العربية/اللاتينية وتصنيف القنوات الرياضية
├── data/
│   ├── db/       Entities, DAOs, AppDatabase
│   ├── iptv/     SubscriptionRepository, ChannelRepository, EpgRepository
│   ├── sports/   Competitions, Providers (API-Football / football-data.org / Demo), MatchRepository
│   └── settings/ SettingsRepository
├── di/           Hilt modules
├── player/       PlayerFactory, PlayerViewModel, PlayerActivity, PlayerScreen
├── notifications/ Notifier, ReminderScheduler, Receivers
├── work/         SubscriptionRefreshWorker, FavoriteTeamsWorker
└── ui/           theme, components, navigation, screens/*
```

كل قسم في الواجهة = Composable + ViewModel خاص به. إضافة ميزة جديدة لا تتطلب تعديل الأقسام الأخرى؛ مثلًا إضافة مزود بيانات رياضية جديد = كلاس يطبّق `SportsProvider` + سطر واحد في `SportsModule`.

## 3. تدفق البيانات

```
UI (Compose) ──events──▶ ViewModel ──▶ Repository ──▶ Room / Network
     ▲                       │
     └──── StateFlow / PagingData ◀── Flow من Room
```

* Room هو "مصدر الحقيقة": الشبكة تكتب في القاعدة والواجهة تقرأ منها فقط، فيعمل التطبيق بدون إنترنت على آخر بيانات محفوظة.

## 4. استيراد القنوات (الأداء)

* **M3U**: تحليل متدفق (Streaming) سطرًا بسطر، إدراج دفعات من 1000 قناة، فلا تُحمَّل القائمة كاملة في الذاكرة (تم اختبار 50,000 قناة).
* **Xtream Codes**: `player_api.php` → التحقق من الحساب (الحالة، تاريخ الانتهاء، عدد الاتصالات) → التصنيفات → القنوات عبر `decodeToSequence` (JSON متدفق).
* **معرّف ثابت للقناة**: `subscriptionId:hash(name|group)` أو `x<streamId>` لـ Xtream، حتى تبقى المفضلة والسجل بعد كل تحديث.
* **حذف القنوات القديمة** عبر رقم "جيل" (generation) بدل `NOT IN` ضخم.
* كلمة مرور Xtream **لا تُخزن في جدول القنوات**؛ رابط البث يُبنى لحظة التشغيل من البيانات المشفرة.

## 5. دليل البرامج EPG

* XMLTV عادي أو مضغوط `.xml.gz` (اكتشاف تلقائي).
* يُخزن فقط: قنوات المستخدم + نافذة زمنية (‎-6 ساعات → +4 أيام) لتقليل الحجم.
* القنوات بدون `tvg-id` تُربط تلقائيًا بمطابقة الاسم مع `display-name`.

## 6. المشغل

* `ExoPlayer` واحد لكل جلسة مشاهدة؛ التنقل بين القنوات = تغيير MediaItem فقط (بدون إعادة إنشاء المشغل).
* **تأخير 350ms عند التنقل السريع** (Debounce): يظهر اسم القناة فورًا، ويبدأ التحميل فقط عند التوقف.
* **ثلاثة أوضاع تخزين مؤقت** (بدء سريع / متوازن / أقصى استقرار) + حد أقل للأجهزة ضعيفة الذاكرة.
* **إعادة الاتصال**: Backoff أسّي (1، 2، 4، 8، 15 ثانية…)، إعادة فورية عند عودة الشبكة، معالجة `BEHIND_LIVE_WINDOW`، ومراقب "تجمّد" يعيد التحضير بعد 15 ثانية تحميل.
* **محاولات بديلة**: m3u8 ↔ ts لـ Xtream، وتلميحات صيغة (HLS/TS) للروابط بدون امتداد.
* **DRM**: Widevine/PlayReady فقط عبر رابط خادم الترخيص الذي يوفره المزود (`#KODIPROP license_key`). لا يوجد أي فك تشفير أو تجاوز للحماية.

## 7. المباريات والبيانات الرياضية

| المزود | التغطية | الحدود | الإعداد |
|---|---|---|---|
| **API-Football** (api-sports.io) | كل البطولات تقريبًا، ومنها أبطال أفريقيا والكونفدرالية وكأس آسيا | 100 طلب/يوم مجانًا، وقد تكون المواسم الحالية للخطط المدفوعة فقط | مفتاح `x-apisports-key` |
| **football-data.org** | 12 بطولة كبرى (الدوريات الخمس، أبطال أوروبا، كأس العالم…) | 10 طلبات/دقيقة مجانًا | توكن `X-Auth-Token` |
| **Demo** | بيانات افتراضية بعلامة "تجريبي" | — | لا شيء |

* التخزين المؤقت: دقيقة واحدة أثناء المباريات المباشرة (دقيقتان لـ API-Football لتوفير الحصة)، 15 دقيقة لليوم، ساعة للأيام القادمة، 6 ساعات للأيام الماضية.
* **القناة الناقلة**: لا توجد واجهة مجانية موثوقة لحقوق البث، لذلك يحددها التطبيق من مصادر المستخدم فقط:
  1. ربط يدوي: البطولة ← قنوات من اشتراكك (مرة واحدة لكل بطولة).
  2. اكتشاف من EPG: برامج حول موعد المباراة تحتوي على أسماء الفريقين (مؤكد) أو أحدهما (محتمل).

## 8. الأمان والخصوصية

* بيانات الاشتراك (الخادم، المستخدم، كلمة المرور، رابط EPG) ومفاتيح API مشفرة بـ AES-GCM بمفتاح غير قابل للتصدير من Keystore.
* `allowBackup=false` وقواعد نقل البيانات تستثني كل شيء.
* سجلات الشبكة معطّلة في نسخة Release.
* حركة HTTP غير المشفرة مسموحة لأن كثيرًا من مزودي IPTV لا يدعمون HTTPS.

## 9. Android TV

* APK واحد للهاتف والتلفاز (`LEANBACK_LAUNCHER` + Banner).
* شريط تنقل جانبي على التلفاز والشاشات العريضة، وشريط سفلي على الهاتف.
* حدود تركيز (Focus ring) واضحة لكل عنصر قابل للضغط.
* المشغل: أعلى/أسفل أو CH+/CH- للتنقل بين القنوات، يسار لقائمة القنوات، OK لإظهار المعلومات، Menu للجودة والصوت والترجمة.

## 10. قابلية التوسع المستقبلي

* Catch-up/Timeshift: الحقل `catchupDays` محفوظ بالفعل من M3U وXtream.
* VOD/Series لـ Xtream: إضافة `get_vod_streams` في Repository جديد دون المساس بالقنوات.
* تقسيم المشروع إلى وحدات Gradle (`:core:parser`, `:data`, `:feature:*`) ممكن مباشرة لأن الحزم مفصولة بالفعل.
