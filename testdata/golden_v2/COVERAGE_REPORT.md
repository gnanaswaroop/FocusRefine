# Coverage and weakness report

Generated from `ml/review/combined_items`: **3740 items**, 18 languages. This is a count-based check of what the data covers; it does not involve any model.

## 1. Size against the plan

Plan per language: 300 test + 150 dev + about 3800 training (the training set and challenge sets are not authored yet).

| lang | language | items now | test | dev | of test+dev plan |
|---|---|---|---|---|---|
| ar | Arabic | 187 | 128 | 59 | 42% |
| bn | Bengali | 189 | 130 | 59 | 42% |
| de | German | 200 | 140 | 60 | 44% |
| en | English | 305 | 203 | 102 | 68% |
| es | Spanish | 239 | 162 | 77 | 53% |
| fr | French | 197 | 138 | 59 | 44% |
| gu | Gujarati | 190 | 133 | 57 | 42% |
| hi | Hindi | 188 | 131 | 57 | 42% |
| hi-Latn | Hinglish | 248 | 170 | 78 | 55% |
| id | Indonesian | 231 | 157 | 74 | 51% |
| ja | Japanese | 201 | 141 | 60 | 45% |
| kn | Kannada | 194 | 132 | 62 | 43% |
| mr | Marathi | 182 | 128 | 54 | 40% |
| pt-BR | Portuguese (Brazil) | 197 | 137 | 60 | 44% |
| ru | Russian | 214 | 151 | 63 | 48% |
| ta | Tamil | 185 | 125 | 60 | 41% |
| te | Telugu | 193 | 128 | 65 | 43% |
| zh-Hans | Chinese (Simplified) | 200 | 141 | 59 | 44% |

## 2. Use-case coverage across languages

`urgent in` uses C F D S W for Commute, Focus, DND, Sleeping, Workout. Use cases that appear in fewer than half the languages are flagged below.

| use case | family | urgent in | items | languages with it | missing in |
|---|---|---|---|---|---|
| chat_casual | personal_life | - | 23 | 17 | te |
| chat_family_routine | personal_life | - | 27 | 16 | en,id |
| family_distress | personal_life | C,D,S | 37 | 18 |  |
| vip_ping | personal_life | C,F | 22 | 18 |  |
| group_chatter | community_group | - | 135 | 18 |  |
| group_forward | community_group | - | 100 | 18 |  |
| group_event_info | community_group | - | 84 | 18 |  |
| community_safety_notice | community_group | C,F,D,S,W | 85 | 18 |  |
| visitor_gate | community_group | - | 122 | 18 |  |
| call_missed_family | phone_calls | C,D,S | 262 | 18 |  |
| call_missed_contact | phone_calls | - | 24 | 17 | en |
| call_unknown | phone_calls | - | 268 | 18 |  |
| call_spam_likely | phone_calls | - | 267 | 18 |  |
| call_scam | phone_calls | - | 269 | 18 |  |
| voicemail_routine | phone_calls | - | 78 | 18 |  |
| promo_sale | spam_scam | - | 71 | 18 |  |
| marketing_push | spam_scam | - | 27 | 17 | mr |
| scam_phishing | spam_scam | - | 29 | 18 |  |
| scam_prize_loan | spam_scam | - | 25 | 18 |  |
| scam_fake_delivery | spam_scam | - | 17 | 16 | en,te |
| weather_severe | emergency_broadcast | C,F,D,S,W | 34 | 18 |  |
| earthquake_alert | emergency_broadcast | C,F,D,S,W | 22 | 18 |  |
| tsunami_flood_wildfire | emergency_broadcast | C,F,D,S,W | 83 | 18 |  |
| public_safety_alert | emergency_broadcast | C,F,D,S,W | 56 | 18 |  |
| civic_advisory | emergency_broadcast | - | 28 | 17 | bn |
| road_closure | emergency_broadcast | C | 22 | 16 | en,hi-Latn |
| payment_routine | banking_payments | - | 109 | 18 |  |
| investment_alert | banking_payments | - | 70 | 18 |  |
| ride_arriving | delivery_ride | C | 48 | 18 |  |
| ride_other | delivery_ride | - | 26 | 18 |  |
| delivery_update | delivery_ride | - | 75 | 18 |  |
| sub_renewal | subscriptions | - | 30 | 18 |  |
| sub_billing_issue | subscriptions | - | 29 | 18 |  |
| rec_music | media_recommendations | - | 40 | 18 |  |
| rec_video | media_recommendations | - | 51 | 18 |  |
| sound_alert_critical | live_captions_accessibility | C,F,D,S,W | 19 | 18 |  |
| sound_alert_routine | live_captions_accessibility | - | 22 | 18 |  |
| caption_status | live_captions_accessibility | - | 103 | 18 |  |
| meeting_soon | work | F | 20 | 18 |  |
| deadline | work | F | 23 | 16 | hi-Latn,id |
| build_failure | work | F | 15 | 15 | en,es,hi-Latn |
| work_incident | work | F | 22 | 18 |  |
| work_chat_routine | work | - | 50 | 18 |  |
| email_routine | work | - | 20 | 13 | de,es,id,kn,te |
| home_security | smart_home_security | D,S | 20 | 18 |  |
| home_alarm_critical | smart_home_security | C,F,D,S,W | 44 | 18 |  |
| home_routine | smart_home_security | - | 24 | 16 | bn,kn |
| travel_urgent | travel | C | 40 | 18 |  |
| travel_routine | travel | - | 29 | 16 | es,pt-BR |
| navigation_prompt | travel | C | 26 | 18 |  |
| maps_routine | travel | - | 75 | 18 |  |
| work_tool_routine | work | - | 76 | 18 |  |
| news_breaking_general | news_weather_sports | - | 22 | 15 | id,mr,ta |
| weather_routine | news_weather_sports | - | 24 | 17 | de |
| sports_update | news_weather_sports | - | 18 | 16 | hi-Latn,id |
| social_activity | social | - | 34 | 16 | es,id |
| workout_prompt | health_fitness | W | 17 | 15 | bn,hi-Latn,id |
| health_critical | health_fitness | D,S,W | 19 | 18 |  |
| health_routine | health_fitness | - | 48 | 18 |  |
| game_notice | games_education | - | 29 | 18 |  |
| learning_notice | games_education | - | 17 | 12 | es,fr,hi-Latn,ja,kn,te |
| system_routine | system | - | 74 | 18 |  |
| telecom_gov_routine | government_telecom | - | 31 | 13 | ar,de,en,es,fr |
| school_urgent | school_family | C,D,S | 24 | 18 |  |
| school_info | school_family | - | 18 | 14 | ar,hi,kn,pt-BR |
| medical_urgent | medical | C,D,S | 23 | 18 |  |
| medical_routine | medical | - | 19 | 15 | ar,en,ja |

## 3. Use-case family share against the plan

| family | now | plan | difference (points) |
|---|---|---|---|
| personal_life | 2.9% | 13% | -10.1 |
| community_group | 14.1% | 9% | +5.1 |
| phone_calls | 31.2% | 8% | +23.2 |
| spam_scam | 4.5% | 13% | -8.5 |
| emergency_broadcast | 6.6% | 6% | +0.6 |
| banking_payments | 4.8% | 7% | -2.2 |
| delivery_ride | 4.0% | 8% | -4.0 |
| subscriptions | 1.6% | 4% | -2.4 |
| media_recommendations | 2.4% | 5% | -2.6 |
| social | 0.9% | 5% | -4.1 |
| work | 6.0% | 6% | +0.0 |
| smart_home_security | 2.4% | 3% | -0.6 |
| live_captions_accessibility | 3.9% | 2% | +1.9 |
| news_weather_sports | 1.7% | 4% | -2.3 |
| travel | 4.5% | 2% | +2.5 |
| health_fitness | 2.2% | 1.5% | +0.7 |
| games_education | 1.2% | 2% | -0.8 |
| system | 2.0% | 1.5% | +0.5 |

Families not in the plan: medical (42), school_family (42), government_telecom (31)

## 4. Label balance (share of items shown)

| lang | urgent in any mode | Commute | Deep Work / Focus | Do Not Disturb | Sleeping / Rest | Workout / Gym |
|---|---|---|---|---|---|---|
| ar | 27% | 23% | 9% | 18% | 18% | 7% |
| bn | 24% | 21% | 9% | 17% | 17% | 6% |
| de | 24% | 20% | 10% | 17% | 17% | 8% |
| en | 32% | 28% | 21% | 27% | 27% | 19% |
| es | 32% | 28% | 17% | 25% | 25% | 17% |
| fr | 23% | 19% | 8% | 15% | 15% | 6% |
| gu | 24% | 20% | 9% | 17% | 17% | 7% |
| hi | 24% | 21% | 9% | 18% | 18% | 7% |
| hi-Latn | 29% | 27% | 17% | 25% | 25% | 16% |
| id | 30% | 27% | 17% | 26% | 26% | 16% |
| ja | 25% | 21% | 10% | 17% | 17% | 8% |
| kn | 24% | 21% | 9% | 17% | 17% | 7% |
| mr | 24% | 20% | 9% | 17% | 17% | 8% |
| pt-BR | 24% | 20% | 8% | 17% | 17% | 7% |
| ru | 29% | 25% | 14% | 21% | 21% | 12% |
| ta | 24% | 20% | 9% | 16% | 16% | 8% |
| te | 22% | 19% | 8% | 16% | 16% | 7% |
| zh-Hans | 26% | 20% | 10% | 17% | 17% | 7% |

## 5. Difficulty and sender mix

Difficulty: easy 2345 (63%), near_miss 798 (21%), hard_negative 597 (16%).

Sender type: service 1476, spam 652, contact 502, system 429, group 378, unknown 303.

## 6. Safety-critical coverage per language (items per use case)

| lang | weather_severe | earthquake_alert | tsunami_flood_wildfire | public_safety_alert | community_safety_notice | home_alarm_critical | sound_alert_critical |
|---|---|---|---|---|---|---|---|
| ar | 1 | 1 | 1 | 1 | 5 | 1 | 1 |
| bn | 1 | 1 | 1 | 1 | 5 | 1 | 1 |
| de | 2 | 1 | 1 | 1 | 6 | 1 | 1 |
| en | 4 | 3 | 14 | 14 | 5 | 14 | 2 |
| es | 1 | 1 | 14 | 13 | 5 | 1 | 1 |
| fr | 1 | 1 | 1 | 1 | 4 | 1 | 1 |
| gu | 3 | 1 | 1 | 1 | 4 | 1 | 1 |
| hi | 2 | 1 | 1 | 1 | 5 | 1 | 1 |
| hi-Latn | 2 | 2 | 14 | 14 | 5 | 1 | 1 |
| id | 1 | 1 | 14 | 1 | 4 | 14 | 1 |
| ja | 3 | 2 | 1 | 1 | 5 | 1 | 1 |
| kn | 3 | 1 | 1 | 1 | 4 | 1 | 1 |
| mr | 2 | 1 | 1 | 1 | 5 | 1 | 1 |
| pt-BR | 2 | 1 | 1 | 1 | 4 | 1 | 1 |
| ru | 1 | 1 | 14 | 1 | 4 | 1 | 1 |
| ta | 2 | 1 | 1 | 1 | 5 | 1 | 1 |
| te | 1 | 1 | 1 | 1 | 5 | 1 | 1 |
| zh-Hans | 2 | 1 | 1 | 1 | 5 | 1 | 1 |

## 7. Phone-call types per language

| lang | call_missed_family | call_missed_contact | call_unknown | call_spam_likely | call_scam |
|---|---|---|---|---|---|
| ar | 15 | 2 | 15 | 15 | 15 |
| bn | 15 | 1 | 15 | 14 | 15 |
| de | 15 | 1 | 15 | 15 | 15 |
| en | 15 | 0 | 15 | 15 | 15 |
| es | 15 | 1 | 15 | 15 | 15 |
| fr | 14 | 2 | 15 | 15 | 15 |
| gu | 15 | 3 | 15 | 15 | 15 |
| hi | 15 | 1 | 15 | 15 | 15 |
| hi-Latn | 15 | 1 | 15 | 15 | 15 |
| id | 15 | 1 | 13 | 15 | 14 |
| ja | 15 | 1 | 15 | 15 | 15 |
| kn | 14 | 2 | 15 | 15 | 15 |
| mr | 13 | 2 | 15 | 14 | 15 |
| pt-BR | 15 | 1 | 15 | 15 | 15 |
| ru | 15 | 1 | 15 | 15 | 15 |
| ta | 13 | 2 | 15 | 15 | 15 |
| te | 13 | 1 | 15 | 15 | 15 |
| zh-Hans | 15 | 1 | 15 | 14 | 15 |

## 8. App coverage and shortcut risk

Distinct apps used: **252 of 252** in the catalogue. Per language: ar 53, bn 76, de 66, en 109, es 71, fr 65, gu 82, hi 76, hi-Latn 83, id 63, ja 53, kn 78, mr 69, pt-BR 68, ru 68, ta 73, te 74, zh-Hans 66.

App categories with no items: none.

Apps whose items all carry one label (a model could learn the app instead of the text): 49. Largest: Binance (34, muted), Netflix (27, muted), Spotify (25, muted), Google Play Store (25, muted), Amazon Shopping (19, muted), Zerodha Kite (19, muted), Clash of Clans (16, muted), Disney+ Hotstar (14, muted), Google One (13, muted), Instagram (12, muted).

## 9. Text length (characters)

median 70, 90th percentile 148, longest 389; under 40 characters: 549 items, over 200: 150 items.

## 10. Ranked gaps and proposed fixes

| severity | kind | gap | proposed fix |
|---|---|---|---|
| high | shortcut | 49 apps always carry the same label (Binance (muted), Netflix (muted), Spotify (muted), Google Play Store (muted), Amazon Shopping (muted), Zerodha Kite (muted), Clash of Clans (muted), Disney+ Hotstar (muted)...), so a model could learn the app instead of the text | add counter-examples per app (a muted Maps or GitHub notification, a Spotify item that is not a recommendation) and hold some apps out |
| high | structural | No training set yet (about 72,000 items planned) | author about 24,000 base items, then deterministic augmentation |
| high | structural | Test and dev are at 3740 items against about 8,100 planned | author about 450 per language (300 test, 150 dev) in rounds with blind relabeling |
| high | structural | No held-out apps (about 15% of apps never used in training) and no challenge sets yet | split the catalogue now, then author the 2,100-item challenge sets |
| medium | diversity | Texts are short: median 70 characters, 15% over 120, 150 over 200 | add longer, group-chat and multi-sentence items (the plan's 300-item long and group challenge set) |
| medium | quality | Near-miss items are often filed under the urgent use case (found in the blind relabel) | add a validator warning for near_miss rows whose use case is urgent, and brief authors again |
| medium | rubric | Rubric conflicts: vip_ping (Commute+Focus) vs 'workplace urgency is Focus only'; booking disruption; clinic 'urgent consult' wording | decide the label for these boundary cases (see contested file) and update rubric.json |
| medium | diagnosis | Model baselines not run on golden_v2 yet (keyword and current embedding model) | port the keyword engine and run both on dev/test to find areas where both fail |

## 11. Contested items

0 items are kept out of scoring until you decide their label (file `ml/review/contested_combined.jsonl`).

