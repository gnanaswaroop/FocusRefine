# Coverage and weakness report

Generated from `ml/review/combined_items`: **7976 items**, 18 languages. This is a count-based check of what the data covers; it does not involve any model.

## 1. Size against the plan

Plan per language: 300 test + 150 dev + about 3800 training (the training set and challenge sets are not authored yet).

| lang | language | items now | test | dev | of test+dev plan |
|---|---|---|---|---|---|
| ar | Arabic | 439 | 299 | 140 | 98% |
| bn | Bengali | 447 | 299 | 148 | 99% |
| de | German | 449 | 299 | 150 | 100% |
| en | English | 450 | 300 | 150 | 100% |
| es | Spanish | 450 | 300 | 150 | 100% |
| fr | French | 437 | 300 | 137 | 97% |
| gu | Gujarati | 449 | 300 | 149 | 100% |
| hi | Hindi | 448 | 299 | 149 | 100% |
| hi-Latn | Hinglish | 434 | 298 | 136 | 96% |
| id | Indonesian | 438 | 297 | 141 | 97% |
| ja | Japanese | 444 | 297 | 147 | 99% |
| kn | Kannada | 447 | 297 | 150 | 99% |
| mr | Marathi | 444 | 297 | 147 | 99% |
| pt-BR | Portuguese (Brazil) | 450 | 300 | 150 | 100% |
| ru | Russian | 439 | 299 | 140 | 98% |
| ta | Tamil | 432 | 295 | 137 | 96% |
| te | Telugu | 433 | 285 | 148 | 96% |
| zh-Hans | Chinese (Simplified) | 446 | 296 | 150 | 99% |

## 2. Use-case coverage across languages

`urgent in` uses C F D S W for Commute, Focus, DND, Sleeping, Workout. Use cases that appear in fewer than half the languages are flagged below.

| use case | family | urgent in | items | languages with it | missing in |
|---|---|---|---|---|---|
| chat_casual | personal_life | - | 93 | 18 |  |
| chat_family_routine | personal_life | - | 81 | 18 |  |
| family_distress | personal_life | C,D,S | 92 | 18 |  |
| vip_ping | personal_life | C,F | 66 | 18 |  |
| group_chatter | community_group | - | 198 | 18 |  |
| group_forward | community_group | - | 158 | 18 |  |
| group_event_info | community_group | - | 151 | 18 |  |
| community_safety_notice | community_group | C,F,D,S,W | 158 | 18 |  |
| visitor_gate | community_group | - | 182 | 18 |  |
| call_missed_family | phone_calls | C,D,S | 329 | 18 |  |
| call_missed_contact | phone_calls | - | 86 | 18 |  |
| call_unknown | phone_calls | - | 325 | 18 |  |
| call_spam_likely | phone_calls | - | 330 | 18 |  |
| call_scam | phone_calls | - | 338 | 18 |  |
| voicemail_routine | phone_calls | - | 144 | 18 |  |
| promo_sale | spam_scam | - | 145 | 18 |  |
| marketing_push | spam_scam | - | 100 | 18 |  |
| scam_phishing | spam_scam | - | 91 | 18 |  |
| scam_prize_loan | spam_scam | - | 81 | 18 |  |
| scam_fake_delivery | spam_scam | - | 65 | 18 |  |
| weather_severe | emergency_broadcast | C,F,D,S,W | 110 | 18 |  |
| earthquake_alert | emergency_broadcast | C,F,D,S,W | 80 | 18 |  |
| tsunami_flood_wildfire | emergency_broadcast | C,F,D,S,W | 131 | 18 |  |
| public_safety_alert | emergency_broadcast | C,F,D,S,W | 105 | 18 |  |
| civic_advisory | emergency_broadcast | - | 92 | 18 |  |
| road_closure | emergency_broadcast | C | 99 | 18 |  |
| payment_routine | banking_payments | - | 161 | 18 |  |
| investment_alert | banking_payments | - | 138 | 18 |  |
| ride_arriving | delivery_ride | C | 119 | 18 |  |
| ride_other | delivery_ride | - | 104 | 18 |  |
| delivery_update | delivery_ride | - | 146 | 18 |  |
| sub_renewal | subscriptions | - | 97 | 18 |  |
| sub_billing_issue | subscriptions | - | 100 | 18 |  |
| rec_music | media_recommendations | - | 104 | 18 |  |
| rec_video | media_recommendations | - | 116 | 18 |  |
| sound_alert_critical | live_captions_accessibility | C,F,D,S,W | 95 | 18 |  |
| sound_alert_routine | live_captions_accessibility | - | 87 | 18 |  |
| caption_status | live_captions_accessibility | - | 169 | 18 |  |
| meeting_soon | work | F | 80 | 18 |  |
| deadline | work | F | 88 | 18 |  |
| build_failure | work | F | 66 | 18 |  |
| work_incident | work | F | 70 | 18 |  |
| work_chat_routine | work | - | 109 | 18 |  |
| email_routine | work | - | 79 | 18 |  |
| home_security | smart_home_security | D,S | 85 | 18 |  |
| home_alarm_critical | smart_home_security | C,F,D,S,W | 98 | 18 |  |
| home_routine | smart_home_security | - | 77 | 18 |  |
| travel_urgent | travel | C | 112 | 18 |  |
| travel_routine | travel | - | 87 | 18 |  |
| navigation_prompt | travel | C | 100 | 18 |  |
| maps_routine | travel | - | 132 | 18 |  |
| work_tool_routine | work | - | 138 | 18 |  |
| news_breaking_general | news_weather_sports | - | 89 | 18 |  |
| weather_routine | news_weather_sports | - | 83 | 18 |  |
| sports_update | news_weather_sports | - | 90 | 18 |  |
| social_activity | social | - | 92 | 18 |  |
| workout_prompt | health_fitness | W | 78 | 17 | id |
| health_critical | health_fitness | D,S,W | 80 | 18 |  |
| health_routine | health_fitness | - | 120 | 18 |  |
| game_notice | games_education | - | 100 | 18 |  |
| learning_notice | games_education | - | 78 | 18 |  |
| system_routine | system | - | 131 | 18 |  |
| telecom_gov_routine | government_telecom | - | 112 | 18 |  |
| school_urgent | school_family | C,D,S | 87 | 18 |  |
| school_info | school_family | - | 74 | 18 |  |
| medical_urgent | medical | C,D,S | 82 | 18 |  |
| medical_routine | medical | - | 93 | 18 |  |

## 3. Use-case family share against the plan

| family | now | plan | difference (points) |
|---|---|---|---|
| personal_life | 4.2% | 13% | -8.8 |
| community_group | 10.6% | 9% | +1.6 |
| phone_calls | 19.5% | 8% | +11.5 |
| spam_scam | 6.0% | 13% | -7.0 |
| emergency_broadcast | 7.7% | 6% | +1.7 |
| banking_payments | 3.7% | 7% | -3.3 |
| delivery_ride | 4.6% | 8% | -3.4 |
| subscriptions | 2.5% | 4% | -1.5 |
| media_recommendations | 2.8% | 5% | -2.2 |
| social | 1.2% | 5% | -3.8 |
| work | 7.9% | 6% | +1.9 |
| smart_home_security | 3.3% | 3% | +0.3 |
| live_captions_accessibility | 4.4% | 2% | +2.4 |
| news_weather_sports | 3.3% | 4% | -0.7 |
| travel | 5.4% | 2% | +3.4 |
| health_fitness | 3.5% | 1.5% | +2.0 |
| games_education | 2.2% | 2% | +0.2 |
| system | 1.6% | 1.5% | +0.1 |

Families not in the plan: medical (175), school_family (161), government_telecom (112)

## 4. Label balance (share of items shown)

| lang | urgent in any mode | Commute | Deep Work / Focus | Do Not Disturb | Sleeping / Rest | Workout / Gym |
|---|---|---|---|---|---|---|
| ar | 28% | 21% | 11% | 17% | 17% | 9% |
| bn | 29% | 22% | 13% | 18% | 18% | 11% |
| de | 29% | 23% | 12% | 20% | 20% | 12% |
| en | 31% | 26% | 19% | 23% | 23% | 16% |
| es | 30% | 24% | 16% | 18% | 18% | 14% |
| fr | 29% | 22% | 13% | 16% | 16% | 9% |
| gu | 31% | 24% | 14% | 19% | 19% | 10% |
| hi | 27% | 21% | 11% | 17% | 17% | 9% |
| hi-Latn | 33% | 28% | 19% | 24% | 24% | 16% |
| id | 31% | 26% | 17% | 21% | 21% | 13% |
| ja | 30% | 22% | 13% | 19% | 19% | 11% |
| kn | 30% | 22% | 13% | 20% | 20% | 10% |
| mr | 32% | 23% | 14% | 19% | 19% | 11% |
| pt-BR | 33% | 24% | 14% | 19% | 19% | 10% |
| ru | 29% | 23% | 15% | 19% | 19% | 13% |
| ta | 31% | 22% | 15% | 19% | 19% | 12% |
| te | 28% | 21% | 14% | 18% | 18% | 12% |
| zh-Hans | 33% | 25% | 16% | 20% | 20% | 12% |

## 5. Difficulty and sender mix

Difficulty: easy 3768 (47%), near_miss 2206 (28%), hard_negative 2002 (25%).

Sender type: service 3802, contact 1136, system 1031, spam 985, group 630, unknown 392.

## 6. Safety-critical coverage per language (items per use case)

| lang | weather_severe | earthquake_alert | tsunami_flood_wildfire | public_safety_alert | community_safety_notice | home_alarm_critical | sound_alert_critical |
|---|---|---|---|---|---|---|---|
| ar | 6 | 4 | 3 | 2 | 8 | 3 | 3 |
| bn | 9 | 3 | 3 | 7 | 8 | 3 | 4 |
| de | 8 | 5 | 5 | 4 | 9 | 6 | 4 |
| en | 6 | 3 | 16 | 14 | 7 | 16 | 5 |
| es | 6 | 1 | 16 | 15 | 9 | 2 | 3 |
| fr | 3 | 2 | 3 | 2 | 11 | 7 | 5 |
| gu | 6 | 5 | 6 | 5 | 9 | 4 | 3 |
| hi | 6 | 5 | 3 | 2 | 7 | 3 | 7 |
| hi-Latn | 8 | 5 | 17 | 17 | 7 | 2 | 10 |
| id | 4 | 6 | 15 | 5 | 6 | 15 | 4 |
| ja | 8 | 8 | 3 | 1 | 8 | 4 | 6 |
| kn | 7 | 4 | 2 | 3 | 7 | 5 | 9 |
| mr | 5 | 3 | 5 | 3 | 8 | 6 | 9 |
| pt-BR | 7 | 4 | 4 | 3 | 9 | 3 | 6 |
| ru | 4 | 6 | 16 | 7 | 10 | 3 | 2 |
| ta | 5 | 4 | 4 | 7 | 11 | 4 | 3 |
| te | 6 | 6 | 4 | 4 | 10 | 9 | 6 |
| zh-Hans | 6 | 6 | 6 | 4 | 14 | 3 | 6 |

## 7. Phone-call types per language

| lang | call_missed_family | call_missed_contact | call_unknown | call_spam_likely | call_scam |
|---|---|---|---|---|---|
| ar | 21 | 7 | 18 | 19 | 17 |
| bn | 17 | 4 | 20 | 17 | 19 |
| de | 16 | 2 | 19 | 16 | 19 |
| en | 16 | 2 | 20 | 18 | 17 |
| es | 18 | 6 | 16 | 17 | 21 |
| fr | 20 | 2 | 18 | 22 | 18 |
| gu | 20 | 7 | 20 | 19 | 18 |
| hi | 21 | 3 | 18 | 19 | 25 |
| hi-Latn | 17 | 4 | 19 | 18 | 16 |
| id | 21 | 2 | 15 | 19 | 17 |
| ja | 16 | 6 | 16 | 16 | 21 |
| kn | 17 | 6 | 19 | 20 | 16 |
| mr | 19 | 6 | 17 | 17 | 18 |
| pt-BR | 18 | 9 | 18 | 18 | 17 |
| ru | 17 | 8 | 18 | 19 | 18 |
| ta | 19 | 6 | 20 | 18 | 22 |
| te | 15 | 4 | 16 | 21 | 19 |
| zh-Hans | 21 | 2 | 18 | 17 | 20 |

## 8. App coverage and shortcut risk

Distinct apps used: **252 of 252** in the catalogue. Per language: ar 99, bn 94, de 78, en 125, es 96, fr 101, gu 91, hi 88, hi-Latn 96, id 91, ja 84, kn 88, mr 88, pt-BR 94, ru 93, ta 90, te 87, zh-Hans 88.

App categories with no items: none.

Apps whose items all carry one label (a model could learn the app instead of the text): 87. Largest: ShareChat (62, muted), Zerodha Kite (61, muted), Free Fire (58, muted), Disney+ (50, muted), Cricbuzz (47, muted), Binance (45, muted), Disney+ Hotstar (41, muted), Wynk Music (39, muted), Google Play Store (37, muted), Snapchat (36, muted).

## 9. Text length (characters)

median 86, 90th percentile 127, longest 389; under 40 characters: 669 items, over 200: 150 items.

## 10. Ranked gaps and proposed fixes

| severity | kind | gap | proposed fix |
|---|---|---|---|
| high | shortcut | 87 apps always carry the same label (ShareChat (muted), Zerodha Kite (muted), Free Fire (muted), Disney+ (muted), Cricbuzz (muted), Binance (muted), Disney+ Hotstar (muted), Wynk Music (muted)...), so a model could learn the app instead of the text | add counter-examples per app (a muted Maps or GitHub notification, a Spotify item that is not a recommendation) and hold some apps out |
| high | structural | No training set yet (about 72,000 items planned) | author about 24,000 base items, then deterministic augmentation |
| high | structural | Test and dev are at 7976 items against about 8,100 planned | author about 450 per language (300 test, 150 dev) in rounds with blind relabeling |
| high | structural | No held-out apps (about 15% of apps never used in training) and no challenge sets yet | split the catalogue now, then author the 2,100-item challenge sets |
| medium | diversity | Texts are short: median 86 characters, 13% over 120, 150 over 200 | add longer, group-chat and multi-sentence items (the plan's 300-item long and group challenge set) |
| medium | quality | Near-miss items are often filed under the urgent use case (found in the blind relabel) | add a validator warning for near_miss rows whose use case is urgent, and brief authors again |
| medium | rubric | Rubric conflicts: vip_ping (Commute+Focus) vs 'workplace urgency is Focus only'; booking disruption; clinic 'urgent consult' wording | decide the label for these boundary cases (see contested file) and update rubric.json |
| medium | diagnosis | Model baselines not run on golden_v2 yet (keyword and current embedding model) | port the keyword engine and run both on dev/test to find areas where both fail |

## 11. Contested items

0 items are kept out of scoring until you decide their label (file `ml/review/contested_combined.jsonl`).

