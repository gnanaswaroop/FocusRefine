#!/usr/bin/env python3
"""
Generates testdata/corpus/corpus_notifications.json: a few hundred synthetic notifications built from
hand-written templates, labelled for six simulated users.

Every item records its `category` and `template`, so experiments can split by template (new *kinds* of
notification) or within a template (new *instances* of a kind the user has seen). `labels[persona]` is the list
of modes in which that simulated user wants the notification Shown; `urgentIn` is the "standard" persona and uses
the same mode names and rules as the golden set (testdata/README.md).

The output is deterministic (fixed seed). It never overlaps golden/holdout (title + text) and contains no duplicates.
Run:  python scripts/generate_corpus.py
"""
import itertools
import json
import os
import random

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GOLDEN = os.path.join(ROOT, "testdata", "golden")
OUT = os.path.join(ROOT, "testdata", "corpus")

C, F, D, S, W = "Commute", "Deep Work / Focus", "Do Not Disturb (DND)", "Sleeping / Rest", "Workout / Gym"
ALL = [C, F, D, S, W]

# category -> modes in which the standard user wants it Shown
STANDARD = {
    "nav_prompt": [C], "transit": [C], "rideshare": [C], "flight": [C], "road_incident": [C],
    "family_distress": [C, D, S], "vip_ping": [C, F],
    "work_incident": [F], "build_failure": [F], "meeting_soon": [F], "deadline": [F],
    "public_emergency": ALL, "home_alarm": ALL, "medical_critical": [D, S, W],
    "school_alert": [C, D, S], "workout_timer": [W], "workout_done": [W],
}
NEGATIVE = ["promo", "social", "chat_casual", "newsletter", "news", "game", "app_update", "rating", "finance_info",
            "delivery_status", "weather_info", "health_summary", "calendar_routine", "payment_reminder", "music",
            "system_info", "travel_deals", "job_alerts", "forum", "near_miss"]
for cat in NEGATIVE:
    STANDARD[cat] = []

WORK = {"work_incident", "build_failure", "meeting_soon", "deadline"}
SAFETY = {"family_distress", "public_emergency", "home_alarm", "medical_critical"}


def persona_labels(persona, category):
    base = list(STANDARD[category])
    if persona == "standard":
        return base
    if persona == "chatty":  # also wants casual chat while travelling, working or exercising
        return sorted(set(base) | ({C, F, W} if category == "chat_casual" else set()), key=ALL.index)
    if persona == "workaholic":  # work alerts everywhere except while asleep or in DND
        return sorted(set(base) | ({C, F, W} if category in WORK else set()), key=ALL.index)
    if persona == "minimalist":  # only genuine safety matters, in every mode
        return list(ALL) if category in SAFETY else []
    if persona == "news_fan":  # headlines and weather while travelling or exercising
        return sorted(set(base) | ({C, W} if category in {"news", "weather_info"} else set()), key=ALL.index)
    if persona == "parent":  # school alerts always, parcels while commuting
        extra = set(ALL) if category == "school_alert" else ({C} if category == "delivery_status" else set())
        return sorted(set(base) | extra, key=ALL.index)
    raise ValueError(persona)


PERSONAS = ["standard", "chatty", "workaholic", "minimalist", "news_fan", "parent"]

NAMES = ["Priya", "Arjun", "Meera", "Rahul", "Sana", "Vikram", "Neha", "Karan", "Isha", "Dev", "Anita", "Rohan"]
CITIES = ["Pune", "Mumbai", "Delhi", "Bengaluru", "Chennai", "Hyderabad", "Kolkata", "Jaipur"]
SVC = ["payments API", "login service", "search API", "checkout service", "notification worker", "auth gateway", "billing service", "image resizer"]
BRANCH = ["main", "release/2.4", "hotfix/login", "develop", "release/3.0", "feature/cart"]

# (category, [titles], text template, {slot: [values]})
T = []


def t(cat, titles, text, **slots):
    T.append((cat, titles, text, slots))


# ---- commute ----
t("nav_prompt", ["Waze", "TomTom GO", "HERE WeGo", "Sygic", "Citymapper"], "In {d} {u}, {act}", d=["300", "500", "800", "1.2", "2"], u=["metres", "km"], act=["bear left onto {r}", "take the exit toward {r}", "keep right at the fork", "take the roundabout exit", "continue straight on {r}"], r=["Ring Road", "MG Road", "Station Road", "Link Road", "Airport Road"])
t("nav_prompt", ["Waze", "HERE WeGo", "Sygic"], "Arriving at {place} in {m} min", place=["your destination", "the office", "the airport", "Gate 3 parking"], m=["2", "4", "6", "9", "12"])
t("nav_prompt", ["TomTom GO", "Waze", "Citymapper"], "A faster way is available, saves {m} minutes", m=["3", "5", "8", "11", "14", "16"])
t("transit", ["Moovit", "Citymapper", "Transit"], "Bus {n} arrives at {stop} in {m} min", n=["12", "42", "101", "7A", "335", "9"], stop=["Central Stop", "Park Street", "Tech Park", "Lake Road", "Station Plaza"], m=["2", "3", "5", "7"])
t("transit", ["IRCTC", "Moovit", "Trainline"], "Your train to {city} leaves from platform {p} in {m} minutes", city=CITIES, p=["1", "2", "4", "6", "9"], m=["10", "15", "20", "25"])
t("transit", ["Metro Rail", "Moovit", "Transit"], "Line {l} is delayed by {m} minutes at {stop}", l=["1", "2", "3", "Blue", "Green", "Purple"], m=["8", "10", "15", "20", "25"], stop=["Central", "Market", "Tech Park", "Old Town"])
t("rideshare", ["Uber", "Lyft", "Ola", "Bolt", "Grab"], "Your driver {n} is arriving in {m} min", n=NAMES, m=["1", "2", "3", "4", "5"])
t("rideshare", ["Uber", "Ola", "Lyft", "Bolt"], "Your driver is waiting outside, {car} {plate}", car=["white Swift", "grey Innova", "black Sedan", "silver Dzire", "blue Hatchback"], plate=["KA01AB1234", "MH12XY8821", "DL4CAB7765", "TN09CD4410", "TS08EF9902"])
t("flight", ["Lufthansa", "IndiGo", "Air India", "Emirates", "United", "Vistara"], "Boarding has started for {f} at gate {g}", f=["LH 760", "6E 2174", "AI 505", "EK 501", "UA 88", "UK 944"], g=["A12", "B4", "C7", "D19", "E2", "F9"])
t("flight", ["IndiGo", "Air India", "Delta", "Qatar Airways", "British Airways"], "Gate change: flight {f} now departs from gate {g}", f=["6E 331", "AI 101", "DL 402", "QR 570", "BA 118", "6E 912"], g=["B3", "C11", "A6", "D4", "E8", "F1"])
t("flight", ["Vistara", "Lufthansa", "United", "Emirates"], "Final boarding call for {f}, gate closes in {m} minutes", f=["UK 810", "LH 756", "UA 902", "EK 513", "UK 995", "LH 761"], m=["5", "8", "10", "12"])
t("road_incident", ["Waze", "Highway Patrol", "Road Alerts", "Google Traffic"], "Accident reported ahead on {road}, expect {m} min delay", road=["NH 48", "Ring Road", "the expressway", "Outer Ring Road", "Airport Road", "Bridge Street"], m=["10", "15", "20", "30", "40"])
t("road_incident", ["Waze", "Road Alerts", "Highway Patrol"], "{what} blocking {road}, use an alternate road", what=["Stalled truck", "Flooding", "Fallen tree", "Road work", "Police activity"], road=["the left lane on NH 48", "Lake Road", "the flyover", "Station Road", "the toll plaza"])

# ---- family distress / VIP ----
t("family_distress", ["Sister", "Brother", "Aunt", "Uncle", "Cousin", "Neighbour"], "Please pick up, it is about {rel}", rel=["grandpa", "our uncle", "the baby", "grandma", "aunt Rita", "our cousin"])
t("family_distress", ["Brother", "Sister", "Uncle", "Aunt"], "{rel} was taken to the hospital, please come when you can", rel=["Grandpa", "Grandma", "Uncle Raj", "Aunt Lata", "Our neighbour", "Little Arya"])
t("family_distress", ["Sister", "Cousin", "Brother", "Neighbour"], "There was an accident, {rel} is hurt, ring me back right away", rel=["grandpa", "uncle", "our cousin", "the driver", "aunt", "baby Riya"])
t("family_distress", ["Hospital", "City Hospital", "Apollo Clinic", "St. John's Hospital"], "{rel} was admitted a few minutes ago, please come immediately", rel=["Your sister", "Your brother", "Your uncle", "Your cousin", "Your grandfather", "Your aunt"])
t("vip_ping", ["CEO", "Director", "VP Engineering", "Client", "Head of Product"], "{ask}", ask=["Need you on a ring in five minutes, it is urgent", "Please phone me as soon as you see this", "Can you join the escalation right now?", "Urgent: reply when you see this, customer is waiting", "Need your decision immediately on the contract", "Pull up the dashboard, I need you now"])

# ---- focus ----
t("work_incident", ["PagerDuty", "Opsgenie", "Datadog", "Sentry", "Grafana", "StatusCake"], "P1 alert: {svc} returning {e} errors", svc=SVC, e=["500", "502", "503", "504", "timeout"])
t("work_incident", ["PagerDuty", "Datadog", "Grafana", "New Relic"], "{svc} latency above SLO for {m} minutes", svc=SVC, m=["5", "10", "15", "20", "30"])
t("work_incident", ["Opsgenie", "PagerDuty", "Sentry", "Statuspage"], "Production {svc} is down, {n} customers affected", svc=SVC, n=["120", "450", "1,200", "3,000", "9,800"])
t("work_incident", ["Datadog", "Grafana", "PagerDuty"], "Database {db} replication lag is {n} seconds", db=["orders-db", "users-db", "ledger-db", "events-db", "cache-db"], n=["45", "90", "180", "300", "600"])
t("build_failure", ["Jenkins", "CircleCI", "GitHub Actions", "GitLab CI", "Buildkite"], "Pipeline failed on {b}: {suite} tests are broken", b=BRANCH, suite=["unit", "integration", "payment", "end-to-end", "smoke"])
t("build_failure", ["GitLab", "Jenkins", "Argo CD", "Spinnaker"], "Production deployment of {svc} failed", svc=SVC)
t("build_failure", ["Bitrise", "CircleCI", "GitHub Actions"], "Build #{n} failed: {err}", n=["4412", "5530", "6178", "7021", "8845", "9103"], err=["compilation error in the checkout module", "linker error on release build", "missing signing key", "out of memory in the test runner", "flaky test blocked the merge queue"])
t("meeting_soon", ["Calendar", "Outlook", "Google Meet", "Zoom", "Teams"], "{event} starts in {m} minutes", event=["Sprint planning", "Design review", "Client sync", "Architecture review", "Quarterly review", "Incident review"], m=["5", "10", "15"])
t("meeting_soon", ["Google Meet", "Zoom", "Teams"], "{p} is waiting for you in the meeting", p=["Your interviewer", "The panel", "Your manager", "The client", "Priya", "The hiring committee"])
t("deadline", ["Asana", "Jira", "Trello", "Slack"], "Reminder: {thing} is due {when}", thing=["the quarterly review", "the release notes", "the audit report", "the budget draft", "the security review sign-off"], when=["today", "tomorrow morning", "by end of day", "in two hours"])
t("deadline", ["Slack", "Email", "Teams"], "Manager: need the {thing} by end of day", thing=["client deck", "status report", "pricing sheet", "roadmap update", "incident summary"])
t("deadline", ["Jira", "Asana", "Linear"], "Launch blocker needs your decision: {thing}", thing=["the payment SDK upgrade", "the data migration plan", "the rollback procedure", "the app store submission", "the feature flag default"])

# ---- emergencies / health / school ----
t("public_emergency", ["Public Safety", "Weather Service", "Civil Defense", "Disaster Alert", "Met Office"], "{what}", what=["Flash flood warning in your area, move to higher ground", "Tornado warning in effect, take shelter now", "Evacuation order for your neighbourhood, leave immediately", "Earthquake alert: strong shaking expected, drop and cover", "Tsunami warning for the coast, move inland now", "Wildfire approaching, evacuate now"])
t("home_alarm", ["Ring Alarm", "Nest Protect", "ADT Home", "SimpliSafe", "Kidde"], "{what}", what=["Smoke detected in the {room}", "Carbon monoxide alarm triggered at home", "Water leak detected in the {room}", "Fire alarm triggered at home", "Glass break detected in the {room}"], room=["kitchen", "basement", "garage", "bedroom", "living room"])
t("medical_critical", ["Dexcom", "Apple Watch", "Fitbit Health", "Medtronic", "Omron Heart"], "Urgent low glucose alert: {n} mg/dL", n=["48", "52", "55", "60", "44"])
t("medical_critical", ["Apple Watch", "Fitbit Health", "Galaxy Watch", "Garmin Health"], "High heart rate notification: {n} BPM while inactive", n=["130", "140", "150", "155", "165"])
t("medical_critical", ["Omron Heart", "Withings", "Apple Watch"], "Irregular heart rhythm detected, please seek care if you feel unwell", )
t("school_alert", ["Greenwood School", "Oakview Elementary", "St. Mary's Academy", "City High"], "Early dismissal today due to {why}", why=["a water main break", "severe weather", "a power outage", "a gas leak nearby", "a staff shortage"])
t("school_alert", ["Greenwood School", "Oakview Elementary", "St. Mary's Academy", "Little Stars Daycare"], "Your child {n} was sent to the nurse, please check in", n=NAMES)
t("school_alert", ["Greenwood School", "City High", "Oakview Elementary"], "School is closed tomorrow: {why}", why=["heavy rain", "a heat wave", "an unplanned holiday", "a strike", "a burst pipe"])

# ---- workout ----
t("workout_timer", ["Peloton", "Interval Timer", "Garmin", "Nike Training", "Fitbit", "Strava"], "Round {n} starts in {s} seconds", n=["2", "3", "4", "5", "6", "8"], s=["5", "10", "15", "30"])
t("workout_timer", ["Garmin", "Strava", "Interval Timer", "Peloton"], "Rest period over, start the next set of {ex}", ex=["squats", "push-ups", "lunges", "deadlifts", "pull-ups", "burpees"])
t("workout_timer", ["Strava", "Garmin", "Nike Run Club"], "Lap {n} done: {p} per km", n=["1", "2", "3", "4", "5", "6"], p=["4:52", "5:10", "5:31", "4:48", "5:02", "5:20"])
t("workout_timer", ["Peloton", "Fitbit", "Garmin", "Apple Fitness"], "Heart rate zone {z} reached, keep this pace", z=["2", "3", "4", "5"])
t("workout_done", ["Nike Training", "Peloton", "Fitbit", "Strava", "Garmin"], "Workout finished, great job! {n} minutes", n=["20", "30", "35", "45", "50", "60"])
t("workout_done", ["Fitbit", "Garmin", "Apple Fitness"], "Active zone minutes goal reached for today")

# ---- hard negatives: everyday noise ----
t("promo", ["Amazon", "Flipkart", "Myntra", "Zomato", "Swiggy", "Nykaa"], "Lightning deal: {p}% off {item}", p=["30", "40", "50", "60", "70"], item=["smart watches", "headphones", "sneakers", "backpacks", "kitchen appliances", "skincare sets"])
t("promo", ["Zomato", "Swiggy", "Uber Eats", "Domino's"], "Craving {food}? Order now and get free {side}", food=["pizza", "biryani", "burgers", "noodles", "tacos"], side=["fries", "dessert", "a drink", "garlic bread", "delivery"])
t("promo", ["Myntra", "Amazon", "Flipkart", "Ajio"], "Your cart is waiting: {item} is almost sold out", item=["running shoes", "a wireless mouse", "a denim jacket", "a yoga mat", "a water bottle"])
t("promo", ["Flipkart", "Amazon", "Meesho"], "Big sale starts {when}: up to {p}% off", when=["tomorrow", "tonight", "this weekend", "on Friday"], p=["40", "50", "60", "70", "80"])
t("social", ["Instagram", "TikTok", "Facebook", "Snapchat", "Pinterest"], "{n} liked your photo", n=NAMES)
t("social", ["Instagram", "TikTok", "X", "Facebook"], "You have {n} new followers", n=["3", "7", "12", "25", "41"])
t("social", ["Snapchat", "Instagram", "Facebook"], "{n} sent you a snap", n=NAMES)
t("social", ["Facebook", "Instagram", "LinkedIn"], "{n} and {k} others commented on your post", n=NAMES, k=["2", "4", "9", "14"])
t("chat_casual", ["WhatsApp", "Messenger", "Telegram", "Slack", "Signal"], "{n}: haha that was so funny", n=NAMES)
t("chat_casual", ["WhatsApp", "Messenger", "Telegram", "Signal"], "{n}: dinner {when}?", n=NAMES, when=["tomorrow", "on Friday", "tonight", "this weekend", "next week"])
t("chat_casual", ["WhatsApp", "Messenger", "Slack", "Telegram"], "{n}: check out this photo from the {trip}", n=NAMES, trip=["trip", "party", "hike", "wedding", "beach"])
t("chat_casual", ["WhatsApp", "Slack", "Messenger", "Signal"], "{n}: are we still on for {plan}?", n=NAMES, plan=["movie night", "the football match", "brunch", "the board game evening", "coffee"])
t("newsletter", ["Substack", "Medium", "Gmail", "Morning Brew", "LinkedIn Pulse"], "New post from {w}: {topic}", w=["Ben Thompson", "Anna Lee", "Rohit Das", "The Pragmatic Engineer", "Lenny"], topic=["why startups slow down", "notes on remote teams", "ten tools I use", "what I read this month", "a field guide to caching"])
t("newsletter", ["Gmail", "Substack", "Medium"], "Weekly digest: your top {n} stories", n=["3", "5", "8", "10", "12"])
t("news", ["BBC News", "NYT", "Reuters", "The Hindu", "Inshorts"], "Top story: {h}", h=["markets close higher as inflation cools", "city council approves new metro line", "scientists report breakthrough in battery storage", "local team wins the regional final", "new telescope images released", "central bank holds rates steady"])
t("news", ["Reuters", "BBC News", "The Hindu"], "Markets {dir} as {why}", dir=["rise", "slip", "close flat", "hit a record"], why=["tech earnings beat estimates", "oil prices ease", "investors await policy decision", "retail sales surprise"])
t("game", ["Play Games", "Clash of Clans", "Candy Crush", "Pokemon GO", "Chess.com"], "Your friend {n} beat your high score", n=NAMES)
t("game", ["Candy Crush", "Clash of Clans", "Subway Surfers", "Pokemon GO"], "Your lives are full, come back and play", )
t("game", ["Chess.com", "Play Games", "Duolingo"], "Daily reward is waiting for you, {n} day streak", n=["3", "7", "12", "30"])
t("app_update", ["Google Play", "App Store", "Samsung", "Play Store"], "{app} update is available to install", app=["Chrome", "Maps Go", "Photos", "Gmail", "YouTube", "Calendar"])
t("app_update", ["Samsung", "Android System", "Software Update"], "Software update ready to install, restart to finish")
t("rating", ["Uber", "Zomato", "Google Play", "Swiggy", "Amazon"], "Rate your recent {x}", x=["order", "ride", "experience", "delivery", "purchase"])
t("finance_info", ["Paytm", "PhonePe", "Mint", "Robinhood", "Revolut"], "Cashback of Rs {n} credited to your wallet", n=["20", "35", "50", "75", "120"])
t("finance_info", ["Mint", "Revolut", "PhonePe"], "Your monthly statement is available for {month}", month=["March", "April", "May", "June", "July", "August"])
t("finance_info", ["Robinhood", "Zerodha", "Mint"], "Your portfolio is {dir} {p}% this week", dir=["up", "down"], p=["1.2", "2.4", "0.8", "3.1"])
t("delivery_status", ["Amazon", "FedEx", "DHL", "BlueDart", "Swiggy"], "Your order is being prepared", )
t("delivery_status", ["FedEx", "DHL", "BlueDart", "Amazon", "Flipkart"], "Package shipped: arriving {day}", day=["Monday", "Tuesday", "Thursday", "Friday", "this weekend"])
t("delivery_status", ["Amazon", "Swiggy", "Zomato", "Flipkart"], "Delivered: your parcel was left at the front door")
t("weather_info", ["Weather", "AccuWeather", "Weather Channel"], "Tomorrow: {c}, high of {h} degrees", c=["partly cloudy", "sunny", "light showers", "windy", "hazy"], h=["24", "28", "31", "19", "34"])
t("weather_info", ["Weather", "AccuWeather"], "Light rain expected this evening around {t}", t=["6 pm", "7 pm", "8 pm", "9 pm"])
t("health_summary", ["Fitbit", "Apple Health", "MyFitnessPal", "Headspace", "Garmin Connect"], "Your sleep score for last night: {n}", n=["68", "74", "81", "85", "90"])
t("health_summary", ["Strava", "Fitbit", "Apple Health"], "Weekly summary: {n} runs, {km} km", n=["2", "3", "4", "5"], km=["12", "18", "24", "31"])
t("health_summary", ["MyFitnessPal", "Headspace", "Fitbit"], "Do not forget to {act}", act=["log your lunch", "take a mindful minute", "drink some water", "stretch for five minutes"])
t("calendar_routine", ["Calendar", "Google Calendar", "Outlook"], "Reminder: {n}'s birthday {when}", n=NAMES, when=["tomorrow", "on Saturday", "next week"])
t("calendar_routine", ["Calendar", "Google Calendar"], "{ev} next {when}", ev=["Dentist checkup", "Car service", "Gym membership renewal", "Book club", "Parent teacher meeting"], when=["week", "month", "Friday"])
t("payment_reminder", ["Electricity Board", "Paytm", "PhonePe", "Netflix", "Spotify"], "{what} of Rs {n} due {when}", what=["Bill", "Subscription renewal", "Premium"], n=["399", "649", "1,250", "2,100"], when=["on the 5th", "next week", "in 3 days", "on Friday"])
t("music", ["Spotify", "YouTube Music", "Apple Music"], "{what}", what=["Your Daily Mix is ready", "New album from {a}", "Your weekly discoveries are here", "{a} just released a new single"], a=["Arijit Singh", "Coldplay", "Taylor Swift", "AR Rahman", "Dua Lipa"])
t("system_info", ["Android System", "Settings", "Files"], "{what}", what=["Battery at {n}%, connect a charger soon", "Storage is almost full", "Wi-Fi connected to {ssid}", "Screenshot saved to gallery"], n=["20", "15", "10"], ssid=["HomeNet", "OfficeWiFi", "CafeGuest"])
t("travel_deals", ["Booking.com", "Airbnb", "MakeMyTrip", "Skyscanner"], "{what}", what=["Price drop on your saved hotel", "Places to stay for your next getaway", "Flights to {city} from Rs {n}", "Your wishlist stay is {p}% cheaper"], city=CITIES, n=["3,499", "4,299", "5,100"], p=["10", "15", "20"])
t("job_alerts", ["LinkedIn", "Indeed", "Naukri"], "{what}", what=["{n} new jobs match your search", "{n} people viewed your profile", "A recruiter viewed your application"], n=["4", "8", "15", "23"])
t("forum", ["Reddit", "Quora", "Stack Overflow", "Hacker News"], "{what}", what=["Your post is trending in r/{sub}", "New answers to a question you follow", "Someone replied to your comment"], sub=["gaming", "india", "androiddev", "cooking", "movies"])
# near misses that look urgent but are not
t("near_miss", ["Jenkins", "CircleCI", "GitHub Actions", "GitLab CI"], "Build #{n} succeeded on {b}", n=["4413", "5531", "6179", "7022"], b=BRANCH)
t("near_miss", ["Google Meet", "Zoom", "Teams", "Notion"], "Meeting notes from {ev} are ready to read", ev=["Sprint planning", "Design review", "Client sync", "Standup"])
t("near_miss", ["Uber", "Ola", "Lyft", "Bolt"], "Your trip receipt: Rs {n}", n=["184", "256", "412", "98", "330"])
t("near_miss", ["IndiGo", "Air India", "Lufthansa", "Emirates"], "Check-in for {f} opens tomorrow", f=["6E 2174", "AI 505", "LH 760", "EK 501", "6E 331"])
t("near_miss", ["Jenkins", "Datadog", "Buildkite"], "Smoke test passed for {svc}", svc=SVC)
t("near_miss", ["Amazon", "Flipkart", "Myntra", "Ajio"], "Fire sale: {p}% off {item}", p=["30", "50", "70"], item=["sneakers", "watches", "laptops", "sunglasses"])
t("near_miss", ["Apple Health", "Fitbit", "Garmin Connect"], "Heart rate summary for last week is ready")
t("near_miss", ["Apollo Clinic", "City Hospital", "Practo"], "Annual checkup reminder: book your visit", )
t("near_miss", ["Instagram", "TikTok", "X"], "A storm of likes: your post is trending")


def realisations(slots, rng, k):
    names = list(slots)
    if not names:
        return [{}]
    combos = list(itertools.product(*[slots[n] for n in names]))
    rng.shuffle(combos)
    return [dict(zip(names, c)) for c in combos[:k]]


def fill(text, vals):
    for _ in range(3):  # slot values may contain further slots
        text = text.format(**vals) if "{" in text else text
    return text


def main():
    rng = random.Random(20261019)
    seen = set()
    for name in ("golden_notifications.json", "holdout_notifications.json"):
        for it in json.load(open(os.path.join(GOLDEN, name), encoding="utf-8")):
            seen.add((it["title"], it["text"]))
    items = []
    for ti, (cat, titles, text, slots) in enumerate(T):
        per_template = 8
        for j, vals in enumerate(realisations(slots, rng, per_template)):
            # values that contain slots of their own are expanded with the same dictionary
            local = dict(vals)
            for kk, vv in slots.items():
                local.setdefault(kk, vv[0])
            rendered = fill(text, local)
            title = titles[(j + ti) % len(titles)]
            if (title, rendered) in seen:
                continue
            seen.add((title, rendered))
            items.append({
                "title": title, "text": rendered, "category": cat, "template": f"t{ti:03d}",
                "urgentIn": persona_labels("standard", cat),
                "labels": {p: persona_labels(p, cat) for p in PERSONAS},
            })
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, "corpus_notifications.json")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(items, f, ensure_ascii=False, indent=1)
        f.write("\n")
    pos = sum(1 for i in items if i["urgentIn"])
    print(f"{len(items)} items, {len({i['template'] for i in items})} templates, {len({i['category'] for i in items})} categories; "
          f"{pos} urgent somewhere under the standard user")
    for m in ALL:
        print(f"  {m}: {sum(1 for i in items if m in i['urgentIn'])} urgent")


if __name__ == "__main__":
    main()
