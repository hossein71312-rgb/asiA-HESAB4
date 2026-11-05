# راهنمای انتشار «آسیا حساب» در کافه بازار / مایکت

## ۱) مخزن GitHub را «خصوصی» کنید
Settings ← General ← Danger Zone ← Change visibility ← Private

## ۲) ساخت کلید امضا (فقط یک‌بار در عمر برنامه)
1. در GitHub به Actions بروید، گردش‌کار **Create Keystore (run ONCE)** را انتخاب و Run workflow بزنید.
2. بعد از پایان، از پایین صفحه‌ی اجرا فایل **asia-keystore-SECRET** را دانلود و از حالت زیپ خارج کنید.
3. **این دو فایل را همین حالا در چند جای امن ذخیره کنید** (فلش، ایمیل خودتان، حافظه ابری): `asia-release.jks` و `keystore-info.txt`.
   اگر این کلید گم شود، دیگر نمی‌توانید برنامه را در بازار آپدیت کنید.
4. فایل `keystore-info.txt` را با Notepad باز کنید. در GitHub: Settings ← Secrets and variables ← Actions ← New repository secret.
   چهار Secret با همین نام‌ها بسازید و مقدار هر کدام را از فایل کپی کنید:
   - `KEYSTORE_BASE64`
   - `KEYSTORE_PASSWORD`
   - `KEY_ALIAS`
   - `KEY_PASSWORD`
5. (اختیاری) بعد از ذخیره شدن Secretها، آرتیفکت را از GitHub حذف کنید.

## ۳) ساخت APK امضاشده
Actions ← **Build Release APK (for Bazaar)** ← Run workflow.
در پایان از بخش Artifacts فایل `Asia-Hesab-release-apk` را دانلود کنید؛ داخل آن `app-release.apk` همان فایل انتشار است.
قبل از آپلود، حتماً آن را روی گوشی خودتان نصب و همه بخش‌ها را امتحان کنید.

## ۴) آپلود در بازار
- در پنل توسعه‌دهندگان بازار یک برنامه‌ی جدید بسازید و `app-release.apk` را آپلود کنید.
- آیکون: `store-assets/icon-512.png`
- متن معرفی و سیاست حریم خصوصی: فایل `store-assets/store-listing-FA.md`
- اسکرین‌شات: برنامه را روی گوشی نصب کنید و از صفحه‌های اصلی (خانه، حساب‌ها، بودجه، چک‌ها) عکس بگیرید.
- ملزومات دقیق فرم‌ها و اندازه‌ی تصاویر را خود پنل بازار نشان می‌دهد؛ اگر چیزی خواسته بود که اینجا نیست، به من بگویید.

## ۵) نسخه‌های بعدی
قبل از هر آپدیت در `app/build.gradle.kts` عدد `versionCode` را یکی بیشتر کنید (۳ ← ۴ ← ...) و `versionName` را عوض کنید. همیشه با همان کلید قبلی بسازید.

## نکته‌ی مهم
شناسه‌ی برنامه `ir.asiahesab.app` است. بعد از اولین انتشار قابل تغییر نیست.
