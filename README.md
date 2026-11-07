# Little Keys

A piano learning app for children for iPhone, iPad, Android phones and tablets, Mac and Windows. It's ready for the Apple App Store, Google Play and the Microsoft Store as a **paid app at US$4.99**.
It works fully offline and has no ads, no in-app purchases, no accounts and no links out of the app.

## What's in this folder

| Folder | What it is |
|---|---|
| `www/` | The app itself (one page, with fonts bundled so it works offline) |
| `android/` | Android Studio project (targets Android 16 / API 36, as Google Play now requires) |
| `ios/` | Xcode project for iPhone and iPad (also runs on Apple silicon Macs) |
| `desktop/` | Windows and Mac desktop app (Electron) |
| `assets/` | Source icon and splash screen; `npm run assets` regenerates every size from them |
| `.github/workflows/` | Cloud builds for Android, iPhone/iPad, Windows and Mac, so you don't need Android Studio or a Mac |
| `store/` | Store text, form answers, screenshots, feature graphic, 512px icon |
| `docs/privacy.html` | Privacy policy page (both stores require a public link to one) |

The app ID is `app.littlekeys.piano`. It is permanent once you upload, so change it now if you want your own (for example `uk.co.yourbrand.littlekeys`). Edit it in `capacitor.config.json`, `android/app/build.gradle` (two places), the Xcode project and `desktop/package.json`, then run `npx cap sync`.

## Screen sizes

The keyboard sizes itself to the screen, and the text scales up on tablets and computers:

| Screen | Keyboard |
|---|---|
| Phone, upright | 1 octave |
| Phone sideways, tablet upright | 2 octaves |
| Tablet sideways, laptop | 3 octaves |
| Large monitor | 4 octaves |

Middle C always stays on screen. Children (or parents) can pick a size under **Free play → Keyboard size**; sizes too small to tap on that screen are greyed out. On a computer, A to K play notes and Z and X move down or up an octave.

---

## Step 1: Put the project on GitHub (once)

1. Create a free account at github.com and a new **private** repository called `pianoplaytime`.
2. Upload this whole folder to the repository. The GitHub Desktop app is the easiest way; drag-and-drop on the website also works.
3. Open the **Actions** tab. The **Android build** runs by itself on every upload.

## Step 2: Test on an Android phone today (free, no accounts)

1. In **Actions**, open the latest green **Android build** run.
2. Under **Artifacts**, download `little-keys-test-apk` and unzip it to get `app-debug.apk`.
3. Send the file to the phone (email, Google Drive or USB), tap it, and allow "install unknown apps" when asked.

To test on an iPhone before release, use TestFlight (Step 4).

## Step 3: Google Play

**Account:** Google Play Console costs a one-off US$25. On a new *personal* account, Google requires a closed test with at least 12 testers for 14 days before you can publish to everyone. An *organisation* account (which needs a free D-U-N-S number for your business) skips this.

1. **Create your upload key** (once; keep the file and passwords safe for ever). On any computer with Java installed:
   ```
   keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Convert it to text: `base64 -i upload.jks` on a Mac, or `certutil -encode upload.jks out.txt` on Windows (copy only the lines between BEGIN and END).
3. In GitHub, go to **Settings → Secrets and variables → Actions** and add:
   - `ANDROID_KEYSTORE_BASE64`: the text from step 2
   - `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` (`upload`), `ANDROID_KEY_PASSWORD`
4. Re-run **Android build**. A second download appears: `little-keys-play-store-aab` (the `.aab` file is what Google wants).
5. **Set up payments first.** In Play Console go to **Settings → Payments profile** and create a payments profile with your bank account. Google won't let you set a price without one.
6. Choose **Create app**: name "Little Keys: Piano for Kids", type App, **Paid**.
   > Important: a Google Play app that has been published as free can never be changed to paid. Make sure it says **Paid** before you publish anything.
7. Set the price: **Monetize with Play → Products → App pricing**, enter **US$4.99**, and let Google convert it for other countries (you can round them, e.g. £4.99, €4.99). Google collects and pays VAT for you in the UK and most other countries.
8. Fill in the rest:
   - **Store listing:** paste the text from `store/STORE_LISTING.md` and add the images from `store/`.
   - **App content:** privacy policy link, Ads = No, Data safety = no data collected, Target audience = the child age groups, content rating questionnaire. All the answers are in `store/STORE_LISTING.md`.
   - **Testing → Closed testing:** upload the `.aab`, add your 12+ testers by email, and roll out.
   - So your testers don't pay $4.99, also add their Gmail addresses under **Settings → License testing**. They can then install the app free.
   - After 14 days, choose **Apply for production**, then submit.

## Step 4: Apple App Store (no Mac needed)

**Account:** Apple Developer Program, £79 (US$99) a year, at developer.apple.com.

**Before you can charge:** in App Store Connect go to **Business** and accept the **Paid Apps Agreement**, then add your bank account and tax forms (from the UK this is the W-8BEN form). Apple won't sell the app until these show as Active. Also join the **App Store Small Business Program** (free) so Apple's commission is 15% instead of 30%.

1. In **Certificates, Identifiers & Profiles → Identifiers**, register an App ID with bundle ID `app.littlekeys.piano`.
2. In **App Store Connect → Apps → +**, create a new app with that bundle ID (name "Little Keys: Piano for Kids", SKU `littlekeys`).
3. In **App Store Connect → Users and Access → Integrations → App Store Connect API**, create a key with the **Admin** role and download the `.p8` file. Admin lets the build create the signing certificate for you.
4. Add these GitHub secrets:
   - `APPSTORE_API_KEY_ID`: the key ID shown next to the key
   - `APPSTORE_API_ISSUER_ID`: shown above the key list
   - `APPSTORE_API_KEY_P8_BASE64`: the `.p8` file as text (`base64 -i AuthKey_XXXX.p8`)
   - `APPLE_TEAM_ID`: from developer.apple.com → Membership (10 characters)
5. In **Actions**, run **iOS build and TestFlight upload** (Run workflow). When it finishes, the build appears in App Store Connect under **TestFlight** within about 15 minutes. Install the TestFlight app on your iPhone to try it.
6. In App Store Connect, fill in the version page:
   - text from `store/STORE_LISTING.md`
   - screenshots from `store/screenshots` (6.9-inch iPhone and 13-inch iPad)
   - App Privacy = Data Not Collected
   - in **App Information**, choose the **Kids** category and an age band
   - in **Pricing and Availability**, set the base country to United States and the price to **US$4.99**; Apple sets matching prices in every other country (for example £4.99 in the UK) and handles VAT
7. Pick the TestFlight build and choose **Submit for Review**. Review usually takes one to three days.

**If you have a Mac:** run `npm ci && npx cap sync ios && npx cap open ios`, choose your team under **Signing & Capabilities**, then **Product → Archive → Distribute App**.

## Step 5: Mac

**To sell on the Mac App Store (recommended, no extra work or cost):** the iPad app runs on any Apple silicon Mac (2020 or newer).

1. In App Store Connect, open the app → **Pricing and Availability**.
2. Make sure **iPhone and iPad Apps on Apple Silicon Macs** is switched on.

The app then appears in the Mac App Store at the same price. One purchase works on a family's iPhone, iPad and Mac.

**Mac test version:** the **Desktop build** in Actions also makes `little-keys-mac-dmg`, a normal Mac app for Apple silicon and Intel Macs. It isn't signed by Apple, so the first time you open it, go to **System Settings → Privacy & Security** and click **Open Anyway**. Selling this version outside the App Store would need an Apple Developer ID certificate and notarisation, which can be added to the build later.

## Step 6: Windows (Microsoft Store)

**Account:** Microsoft Store developer registration is free for individuals at storedeveloper.microsoft.com.

1. In **Partner Center**, create a new MSIX/PWA app and reserve the name "Little Keys: Piano for Kids".
2. Open **Product management → Product identity** and copy three values into GitHub secrets:
   - `MS_IDENTITY_NAME`: Package/Identity/Name
   - `MS_PUBLISHER`: Package/Identity/Publisher (starts with `CN=`)
   - `MS_PUBLISHER_DISPLAY_NAME`: Package/Properties/PublisherDisplayName
3. Run **Desktop build (Windows and Mac)** from the Actions tab. It now also makes `little-keys-microsoft-store-appx`.
4. In Partner Center, start a submission:
   - **Pricing and availability:** US$4.99 (Microsoft converts it for other countries)
   - **Properties:** category Education, then the privacy policy link
   - **Age ratings:** the questionnaire; answer as for Google Play
   - **Packages:** upload the `.appx`
   - **Store listings:** text from `store/STORE_LISTING.md` plus desktop screenshots from `store/screenshots`
5. Submit. Microsoft signs the package for you, and certification usually takes a few days. Microsoft keeps 15% of app sales.

**Windows test version:** `little-keys-windows-installer` (from the same build) installs Little Keys straight away. Because it isn't signed, Windows SmartScreen may warn the first time: click **More info → Run anyway**. Store copies don't show this.

## Step 7: Publish the privacy policy

1. Put your contact email into `docs/privacy.html` in place of `YOUR-CONTACT-EMAIL`.
2. In GitHub, go to **Settings → Pages**, set Source to **Deploy from a branch**, then choose branch `main` and folder `/docs`.
3. Your link is `https://oje2407.github.io/pianoplaytime/privacy.html`. GitHub Pages on a private repository needs a paid GitHub plan; otherwise make the repository public or host the page anywhere.

## Updating the app later

Edit `www/index.html` and upload it to GitHub. Phones, tablets, Mac and Windows all use this same file. The builds number themselves automatically, so each upload can go straight to Play Console and TestFlight. For the Microsoft Store, raise `version` in `desktop/package.json` before each new submission. Version names are 1.0.(build number).

## What you'll receive per sale

Both stores keep 15% for small developers (Apple only after you join the Small Business Program; Google applies 15% to your first US$1 million automatically). Before that commission, sales tax or VAT is taken off the shopper's price. In the UK that leaves roughly £3.50 from each £4.99 sale. Apple pays monthly and Google monthly, into the bank account you added.

## Why this suits the Kids categories

Both stores have strict rules for children's apps. Little Keys meets them:

- no ads
- no analytics or third-party SDKs
- no data collection
- no in-app purchases (the one-time download price is fine)
- no external links (so no parental gate is needed)
- works offline

Keep it that way. If you ever add purchases or links out, Apple requires a parental gate in front of them.

## Keeping children in the app

Both phones can lock a child inside one app:

- **Windows:** use **Settings → Accounts → Family** to set up a child account with app and screen-time limits.
- **Mac:** use **System Settings → Screen Time** for app limits.
- **iPhone or iPad:** turn on **Settings → Accessibility → Guided Access**, open Little Keys, and triple-click the side button. You can also set a time limit there.
- **Android:** turn on **Settings → Security → App pinning** (some phones call it Screen pinning), open Little Keys from the recent apps view, and choose **Pin**.

## Local commands (optional)

```
npm ci                 # install
npx cap sync           # copy www/ into both native projects
npx cap open android   # open in Android Studio
npx cap open ios       # open in Xcode (Mac only)
npm run assets         # regenerate icons and splash screens from assets/
```
