# Publishing on Google Play

How to put Watchlist on Google Play, from an empty Play Console to a public release. The store
texts for every language are in [`fastlane/metadata/android/`](../fastlane/metadata/android/)
(one folder per Play locale: `title.txt`, `short_description.txt`, `full_description.txt`);
copy them into the Play Console as they are.

## Before you charge money

- **SIMKL:** the API is free for commercial apps that make less than $150 a month. Above that
  you need a commercial licence from SIMKL (ask on their Discord). See
  [SIMKL's API rules](https://api.simkl.org/api-rules), section 5.
- **TMDB:** TMDB's API is free for non-commercial use only; commercial use needs a licence
  ([TMDB FAQ](https://developer.themoviedb.org/docs/faq)). Each user enters their own token, but
  the app itself is sold, so ask TMDB (the address is on that page) before launching the paid
  version.
- **The subscription isn't in the app yet.** The "Price" paragraph of the store texts describes
  the planned 14-day trial and €3.99 a year. Leave it out of the listing until a version with
  Google Play Billing is in production.

## 1. Developer account

1. Sign up at [play.google.com/console](https://play.google.com/console) (a one-time $25 fee)
   and verify your identity.
2. Pick the account type:
   - **Personal:** quickest, but a new personal account must run a **closed test with at least
     12 testers for 14 days in a row** before it can publish to production
     ([Play Console Help](https://support.google.com/googleplay/android-developer/answer/14151465)).
   - **Organization:** needs a D-U-N-S number for a registered business (free to request from
     Dun & Bradstreet; check whether your sole proprietorship qualifies) and skips the 12-tester
     rule.
3. To sell a subscription: **Settings › Payments profile**, add your bank account. Google
   charges and pays the VAT for buyers in the EU.

## 2. Create the app

**Home › Create app**:

| Field | Value |
| --- | --- |
| App name | Watchlist for Simkl |
| Default language | English (United States) – en-US |
| App or game | App |
| Free or paid | **Free**. The subscription is sold inside the app. A paid app can't have a free trial, and a free app can never be changed to paid later. |

## 3. Store listing

**Grow users › Store presence › Main store listing**, then **Manage translations** for the
other languages (nl-NL, fr-FR, de-DE, es-ES, it-IT):

- App name, short description, full description: from `fastlane/metadata/android/<locale>/`.
- App icon: 512 × 512 PNG (export the launcher icon on its white background).
- Feature graphic: 1024 × 500 PNG or JPG.
- Phone screenshots: at least 2 (Up next, a details page, Discover and the calendar work
  well). Take them on your phone with the release build.
- Category: **Entertainment**. Contact e-mail: required; website optional.

## 4. App content

**Policy and programs › App content**, every item needs an answer:

| Item | Answer |
| --- | --- |
| Privacy policy | `https://github.com/wimvdon/watchlist/blob/main/PRIVACY.md` |
| Ads | No ads |
| App access | All functions need a sign-in: give the reviewers a SIMKL test account (e-mail and password) and the steps: tap **Sign in with SIMKL**, sign in on simkl.com, approve. |
| Content rating | Fill in the questionnaire (no violence, no user-to-user chat, no gambling). |
| Target audience | 13 and older (the app shows ratings and posters of every age rating). |
| Data safety | See below. |
| Account deletion | Not applicable: accounts are created on simkl.com, not in the app. |
| Government, financial, health, news | No |

**Data safety**, based on `PRIVACY.md`:

- Data is encrypted in transit: yes (HTTPS only).
- Collected (Google counts anything sent off the phone): **App activity › Other actions**
  (what you mark watched, ratings, lists), sent to SIMKL for the app to work; required; not
  shared with third parties for other purposes.
- No location, contacts, financial data, analytics, crash reports or advertising ID.
- Deletion: users delete their data on SIMKL; signing out deletes the copy on the phone.

## 5. App signing

The first upload sets up **Play App Signing**: Google keeps the key that signs what Play
delivers, and the release workflow's key becomes the *upload key*. Accept the default.

Consequence: a Watchlist installed from GitHub Releases and one from Google Play have different
signatures, so Android can't update one into the other. Once the app is on Play, point people
to Play and keep GitHub Releases for testing.

## 6. Test tracks and first upload

1. Run **Actions › Release** (see [installation.md](installation.md), "Making a release") and
   download `watchlist-<version>.aab` from the GitHub Release.
2. **Test and release › Testing › Internal testing › Create new release**, upload the `.aab`,
   add yourself as a tester and install it from the link. This is the quickest check that the
   Play build signs in and syncs.
3. **Closed testing** (personal accounts): create a track, add at least 12 testers (a Google
   Group or e-mail list), share the opt-in link and keep them opted in for 14 days. Testers have
   to install and open the app.
4. After 14 days: **Dashboard › Apply for production** and answer the questions about the test.

## 7. Subscription (once Play Billing is in the app)

**Monetize with Play › Products › Subscriptions › Create subscription**:

1. Product ID `watchlist_yearly`, name "Watchlist".
2. **Add base plan**: ID `yearly`, auto-renewing, billing period 1 year, price **€3.99**. Play
   suggests prices for the other countries; check them.
3. **Add offer** on that base plan: ID `free-trial`, eligibility *New customer acquisition*
   (never had this subscription), phase **Free trial, 14 days**. Activate the plan and the
   offer.
4. **Monetization setup › License testing**: add your own Google account, so test purchases
   cost nothing.

The products can only be activated after a version that uses Google Play Billing has been
uploaded to a test track.

## 8. Production release

**Test and release › Production › Create new release**, upload the same `.aab` (or promote it
from closed testing), write the release notes, and start a staged rollout (for example 20 %,
then 100 % a few days later). Google's review usually takes a few days for a new app.
