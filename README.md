# Scroll Counter (Android)

Counts, per day:

- **Instagram reels** you watch (Instagram app). By default a reel counts once it has
  stayed on screen for 5 seconds, so reels you swipe past quickly don't count
- **Facebook pages** scrolled in the Facebook app (and Facebook Lite)
- **Facebook pages** scrolled on facebook.com in a mobile browser

A "page" is one screen height scrolled downward. Scrolling back up is not counted.

## Floating counter

While Instagram, Facebook or facebook.com is on screen, a small badge floats on
top: 🎬 reels in Instagram, 📄 pages in Facebook (app + web combined). The large
number is this session's count and the small line below is today's. Drag it to move it.

A **session** starts when you open the app after being away from it for at least
5 minutes (configurable). Instagram and Facebook each have their own session.

In the app you can:

- turn the badge on or off
- set **daily** and **per-session** limits for reels and for Facebook pages. The badge
  shows `count / limit`, turns red once a limit is reached, and a message pops up at that
  moment. Leave a limit empty for no limit.
- change how many seconds a reel must play before it counts (default 5; 0 counts every swipe)

The badge is an accessibility overlay, so it needs no extra "Display over other apps" permission.

Everything stays on the phone: counts go in the app's private storage, and nothing is sent anywhere.

## How it works

Android doesn't let one app see what happens in another, except through an
**Accessibility Service**. `ScrollCounterService` receives window-change events from all
apps, but for other apps it only notes which app is open so it can hide the badge.
It counts scrolls only in Instagram, Facebook, Facebook Lite and the browsers listed in
`ScrollCounterService.BROWSERS`. It never reads screen text,
except for the browser's address bar, which it checks to see whether you're on facebook.com.

| What | How it's detected | Code |
|---|---|---|
| Instagram reels | Scroll events from the full-screen reels pager (view id containing `clips`) tell when a new reel is on screen. If it's still on screen after the minimum watch time, it counts. Each reel counts once, so swiping back and forth doesn't inflate the count. | `ReelDetector.kt`, `ScrollCounterService.kt` |
| Facebook pages | Downward scroll distance ÷ visible height, from pixel deltas, `scrollY`, or feed item positions, whichever the app reports | `PageScrollTracker.kt` |
| facebook.com in a browser | The same page counting, only while the address bar shows `facebook.com` / `fb.com` | `ScrollCounterService.kt`, `FacebookUrl.kt` |

Supported browsers: Chrome, Samsung Internet, Firefox, Edge, Brave, Opera, Kiwi,
DuckDuckGo, Vivaldi and Mi Browser. To add another one, add its package name to
`BROWSERS` in `ScrollCounterService.kt`.

## Build

**From GitHub:** each push runs the *Build APK* workflow. Download
`scroll-counter-debug-apk` from the run's **Artifacts** section.

**Locally:** Open the project in Android Studio, or run:

```sh
./gradlew assembleDebug     # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest # detector unit tests
```

Requires JDK 17+ and the Android SDK (compileSdk 35). Minimum Android version: 8.0 (API 26).

## Set up on the phone

1. Install the APK. You may need to allow installs from unknown sources.
2. Open **Scroll Counter** and tap **Open accessibility settings**. Then open
   *Installed apps / Downloaded apps → Scroll Counter* and turn it on.
3. **Android 13 and later:** if the switch is greyed out ("Restricted setting"), tap **Open app info**,
   tap the ⋮ menu, choose **Allow restricted settings**, and repeat step 2.
4. Some phones (Xiaomi, Huawei, Oppo, Samsung and others) stop background services to save battery. If counting stops,
   set the app's battery usage to *Unrestricted*.

## Accuracy and adjusting it

Instagram and Facebook don't provide an official way to do this, so the app uses
heuristics based on how their screens are built. Those screens can change when
the apps update. If a count looks wrong:

1. Turn on **Diagnostics** in the app.
2. Scroll in Instagram or Facebook, then tap **Refresh log**.
3. Each line shows the app, view id, item indexes, scroll offset, and view size of the
   scrolling view, plus `+1 reel` / `+1 page` when something was counted. Use the view ids you see to update
   `ReelDetector.isReelsPager` or the filters in `PageScrollTracker`.

Known limitations:

- The reel you open first isn't counted unless you come back to it after swiping away.
- The app can't tell whether a reel is paused. A reel left on screen for 5 seconds counts even if it was paused.
- Reels watched inside the Facebook app count as Facebook pages, not Instagram reels.
- If the browser's address bar is hidden, the last known site is kept until the bar shows again.
- In a browser, the badge appears when you open facebook.com or once you start scrolling it
  (within about 1.5 seconds).
