# Coverage and weakness report

Generated from `testdata\golden_v2\pilot_agreed`: **1072 items**, 18 languages. This is a count-based check of what the data covers; it does not involve any model.

## 1. Size against the plan

Plan per language: 300 test + 150 dev + about 3800 training (the training set and challenge sets are not authored yet).

| lang | language | items now | test | dev | of test+dev plan |
|---|---|---|---|---|---|
| ar | Arabic | 60 | 40 | 20 | 13% |
| bn | Bengali | 60 | 40 | 20 | 13% |
| de | German | 60 | 40 | 20 | 13% |
| en | English | 59 | 40 | 19 | 13% |
| es | Spanish | 60 | 40 | 20 | 13% |
| fr | French | 60 | 40 | 20 | 13% |
| gu | Gujarati | 60 | 40 | 20 | 13% |
| hi | Hindi | 60 | 40 | 20 | 13% |
| hi-Latn | Hinglish | 59 | 39 | 20 | 13% |
| id | Indonesian | 58 | 39 | 19 | 13% |
| ja | Japanese | 60 | 40 | 20 | 13% |
| kn | Kannada | 60 | 41 | 19 | 13% |
| mr | Marathi | 59 | 39 | 20 | 13% |
| pt-BR | Portuguese (Brazil) | 60 | 40 | 20 | 13% |
| ru | Russian | 58 | 39 | 19 | 13% |
| ta | Tamil | 60 | 40 | 20 | 13% |
| te | Telugu | 59 | 40 | 19 | 13% |
| zh-Hans | Chinese (Simplified) | 60 | 40 | 20 | 13% |

## 2. Use-case coverage across languages

`urgent in` uses C F D S W for Commute, Focus, DND, Sleeping, Workout. Use cases that appear in fewer than half the languages are flagged below.

| use case | family | urgent in | items | languages with it | missing in |
|---|---|---|---|---|---|
| chat_casual | personal_life | - | 19 | 17 | te |
| chat_family_routine | personal_life | - | 16 | 15 | en,hi-Latn,id |
| family_distress | personal_life | C,D,S | 25 | 18 |  |
| vip_ping | personal_life | C,F | 16 | 16 | en,mr |
| group_chatter | community_group | - | 19 | 18 |  |
| group_forward | community_group | - | 21 | 18 |  |
| group_event_info | community_group | - | 10 | 10 | 8 languages |
| community_safety_notice | community_group | C,F,D,S,W | 19 | 18 |  |
| visitor_gate | community_group | - | 7 | 7 | 11 languages |
| call_missed_family | phone_calls | C,D,S | 25 | 18 |  |
| call_missed_contact | phone_calls | - | 17 | 17 | en |
| call_unknown | phone_calls | - | 19 | 18 |  |
| call_spam_likely | phone_calls | - | 20 | 18 |  |
| call_scam | phone_calls | - | 19 | 18 |  |
| voicemail_routine | phone_calls | - | 6 | 6 | 12 languages |
| promo_sale | spam_scam | - | 26 | 18 |  |
| marketing_push | spam_scam | - | 15 | 15 | es,hi-Latn,mr |
| scam_phishing | spam_scam | - | 24 | 18 |  |
| scam_prize_loan | spam_scam | - | 20 | 18 |  |
| scam_fake_delivery | spam_scam | - | 16 | 16 | en,te |
| weather_severe | emergency_broadcast | C,F,D,S,W | 20 | 18 |  |
| earthquake_alert | emergency_broadcast | C,F,D,S,W | 21 | 18 |  |
| tsunami_flood_wildfire | emergency_broadcast | C,F,D,S,W | 13 | 13 | en,es,hi-Latn,id,ru |
| public_safety_alert | emergency_broadcast | C,F,D,S,W | 15 | 15 | en,es,hi-Latn |
| civic_advisory | emergency_broadcast | - | 14 | 14 | bn,en,hi-Latn,id |
| road_closure | emergency_broadcast | C | 16 | 16 | en,hi-Latn |
| payment_routine | banking_payments | - | 21 | 18 |  |
| investment_alert | banking_payments | - | 6 | 6 | 12 languages |
| ride_arriving | delivery_ride | C | 21 | 18 |  |
| ride_other | delivery_ride | - | 16 | 16 | en,mr |
| delivery_update | delivery_ride | - | 19 | 18 |  |
| sub_renewal | subscriptions | - | 19 | 18 |  |
| sub_billing_issue | subscriptions | - | 20 | 18 |  |
| rec_music | media_recommendations | - | 19 | 18 |  |
| rec_video | media_recommendations | - | 18 | 18 |  |
| sound_alert_critical | live_captions_accessibility | C,F,D,S,W | 19 | 18 |  |
| sound_alert_routine | live_captions_accessibility | - | 21 | 18 |  |
| caption_status | live_captions_accessibility | - | 1 | 1 | 17 languages |
| meeting_soon | work | F | 18 | 18 |  |
| deadline | work | F | 15 | 15 | en,hi-Latn,id |
| build_failure | work | F | 15 | 15 | en,es,hi-Latn |
| work_incident | work | F | 21 | 18 |  |
| work_chat_routine | work | - | 19 | 16 | en,kn |
| email_routine | work | - | 12 | 11 | 7 languages |
| home_security | smart_home_security | D,S | 19 | 18 |  |
| home_alarm_critical | smart_home_security | C,F,D,S,W | 16 | 16 | en,id |
| home_routine | smart_home_security | - | 16 | 16 | bn,kn |
| travel_urgent | travel | C | 18 | 16 | id,ru |
| travel_routine | travel | - | 13 | 13 | es,id,pt-BR,ta,te |
| navigation_prompt | travel | C | 20 | 18 |  |
| news_breaking_general | news_weather_sports | - | 13 | 13 | es,hi-Latn,id,mr,ta |
| weather_routine | news_weather_sports | - | 15 | 15 | de,en,id |
| sports_update | news_weather_sports | - | 16 | 16 | hi-Latn,id |
| social_activity | social | - | 14 | 14 | en,es,hi-Latn,id |
| workout_prompt | health_fitness | W | 16 | 15 | bn,hi-Latn,id |
| health_critical | health_fitness | D,S,W | 19 | 18 |  |
| health_routine | health_fitness | - | 21 | 18 |  |
| game_notice | games_education | - | 17 | 17 | hi-Latn |
| learning_notice | games_education | - | 10 | 10 | 8 languages |
| system_routine | system | - | 7 | 7 | 11 languages |
| telecom_gov_routine | government_telecom | - | 4 | 4 | 14 languages |
| school_urgent | school_family | C,D,S | 19 | 18 |  |
| school_info | school_family | - | 11 | 11 | 7 languages |
| medical_urgent | medical | C,D,S | 18 | 17 | ru |
| medical_routine | medical | - | 12 | 12 | ar,en,es,gu,hi,ja |

## 3. Use-case family share against the plan

| family | now | plan | difference (points) |
|---|---|---|---|
| personal_life | 7.1% | 13% | -5.9 |
| community_group | 7.1% | 9% | -1.9 |
| phone_calls | 9.9% | 8% | +1.9 |
| spam_scam | 9.4% | 13% | -3.6 |
| emergency_broadcast | 9.2% | 6% | +3.2 |
| banking_payments | 2.5% | 7% | -4.5 |
| delivery_ride | 5.2% | 8% | -2.8 |
| subscriptions | 3.6% | 4% | -0.4 |
| media_recommendations | 3.5% | 5% | -1.5 |
| social | 1.3% | 5% | -3.7 |
| work | 9.3% | 6% | +3.3 |
| smart_home_security | 4.8% | 3% | +1.8 |
| live_captions_accessibility | 3.8% | 2% | +1.8 |
| news_weather_sports | 4.1% | 4% | +0.1 |
| travel | 4.8% | 2% | +2.8 |
| health_fitness | 5.2% | 1.5% | +3.7 |
| games_education | 2.5% | 2% | +0.5 |
| system | 0.7% | 1.5% | -0.8 |

Families not in the plan: medical (30), school_family (30), government_telecom (4)

## 4. Label balance (share of items shown)

| lang | urgent in any mode | Commute | Deep Work / Focus | Do Not Disturb | Sleeping / Rest | Workout / Gym |
|---|---|---|---|---|---|---|
| ar | 38% | 27% | 20% | 22% | 22% | 15% |
| bn | 37% | 27% | 20% | 22% | 22% | 13% |
| de | 38% | 27% | 20% | 22% | 22% | 15% |
| en | 42% | 31% | 15% | 27% | 27% | 14% |
| es | 47% | 32% | 17% | 23% | 23% | 15% |
| fr | 42% | 30% | 20% | 25% | 25% | 15% |
| gu | 38% | 27% | 20% | 22% | 22% | 15% |
| hi | 38% | 27% | 20% | 22% | 22% | 15% |
| hi-Latn | 41% | 32% | 19% | 25% | 25% | 14% |
| id | 34% | 26% | 16% | 22% | 22% | 10% |
| ja | 45% | 33% | 23% | 28% | 28% | 18% |
| kn | 38% | 27% | 20% | 22% | 22% | 15% |
| mr | 39% | 27% | 19% | 24% | 24% | 15% |
| pt-BR | 43% | 32% | 22% | 27% | 27% | 17% |
| ru | 34% | 22% | 19% | 19% | 19% | 14% |
| ta | 38% | 27% | 20% | 22% | 22% | 15% |
| te | 39% | 27% | 20% | 22% | 22% | 15% |
| zh-Hans | 38% | 27% | 20% | 22% | 22% | 15% |

## 5. Difficulty and sender mix

Difficulty: easy 634 (59%), hard_negative 227 (21%), near_miss 211 (20%).

Sender type: service 541, system 165, contact 155, spam 118, group 71, unknown 22.

## 6. Safety-critical coverage per language (items per use case)

| lang | weather_severe | earthquake_alert | tsunami_flood_wildfire | public_safety_alert | community_safety_notice | home_alarm_critical | sound_alert_critical |
|---|---|---|---|---|---|---|---|
| ar | 1 | 1 | 1 | 1 | 1 | 1 | 1 |
| bn | 1 | 1 | 1 | 1 | 1 | 1 | 1 |
| de | 1 | 1 | 1 | 1 | 1 | 1 | 1 |
| en | 1 | 2 | 0 | 0 | 1 | 0 | 2 |
| es | 1 | 1 | 0 | 0 | 1 | 1 | 1 |
| fr | 1 | 1 | 1 | 1 | 1 | 1 | 1 |
| gu | 1 | 1 | 1 | 1 | 1 | 1 | 1 |
| hi | 1 | 1 | 1 | 1 | 1 | 1 | 1 |
| hi-Latn | 1 | 2 | 0 | 0 | 2 | 1 | 1 |
| id | 1 | 1 | 0 | 1 | 1 | 0 | 1 |
| ja | 2 | 2 | 1 | 1 | 1 | 1 | 1 |
| kn | 1 | 1 | 1 | 1 | 1 | 1 | 1 |
| mr | 1 | 1 | 1 | 1 | 1 | 1 | 1 |
| pt-BR | 2 | 1 | 1 | 1 | 1 | 1 | 1 |
| ru | 1 | 1 | 0 | 1 | 1 | 1 | 1 |
| ta | 1 | 1 | 1 | 1 | 1 | 1 | 1 |
| te | 1 | 1 | 1 | 1 | 1 | 1 | 1 |
| zh-Hans | 1 | 1 | 1 | 1 | 1 | 1 | 1 |

## 7. Phone-call types per language

| lang | call_missed_family | call_missed_contact | call_unknown | call_spam_likely | call_scam |
|---|---|---|---|---|---|
| ar | 1 | 1 | 1 | 1 | 1 |
| bn | 1 | 1 | 1 | 1 | 1 |
| de | 1 | 1 | 1 | 1 | 1 |
| en | 2 | 0 | 2 | 2 | 1 |
| es | 2 | 1 | 1 | 1 | 1 |
| fr | 2 | 1 | 1 | 1 | 1 |
| gu | 1 | 1 | 1 | 1 | 1 |
| hi | 1 | 1 | 1 | 1 | 1 |
| hi-Latn | 2 | 1 | 1 | 1 | 2 |
| id | 2 | 1 | 1 | 2 | 1 |
| ja | 2 | 1 | 1 | 1 | 1 |
| kn | 1 | 1 | 1 | 1 | 1 |
| mr | 1 | 1 | 1 | 1 | 1 |
| pt-BR | 2 | 1 | 1 | 1 | 1 |
| ru | 1 | 1 | 1 | 1 | 1 |
| ta | 1 | 1 | 1 | 1 | 1 |
| te | 1 | 1 | 1 | 1 | 1 |
| zh-Hans | 1 | 1 | 1 | 1 | 1 |

## 8. App coverage and shortcut risk

Distinct apps used: **102 of 252** in the catalogue. Per language: ar 38, bn 40, de 34, en 34, es 35, fr 42, gu 40, hi 42, hi-Latn 37, id 30, ja 34, kn 37, mr 40, pt-BR 37, ru 38, ta 38, te 36, zh-Hans 36.

App categories with no items: none.

Apps whose items all carry one label (a model could learn the app instead of the text): 25. Largest: Spotify (25, muted), Netflix (23, muted), Google Maps (22, urgent), Amazon Shopping (16, muted), Waze (16, urgent), GitHub (15, urgent), Clash of Clans (13, muted), Todoist (11, urgent), Instagram (11, muted), Duolingo (10, muted).

## 9. Text length (characters)

median 65, 90th percentile 102, longest 158; under 40 characters: 197 items, over 200: 0 items.

## 10. Ranked gaps and proposed fixes

| severity | kind | gap | proposed fix |
|---|---|---|---|
| high | safety-critical | Safety use case `tsunami_flood_wildfire` has no items in 5 languages: en, es, hi-Latn, id, ru | add about 20 per language across 4+ apps (100 items) |
| high | safety-critical | Safety use case `public_safety_alert` has no items in 3 languages: en, es, hi-Latn | add about 20 per language across 4+ apps (60 items) |
| high | safety-critical | Safety use case `home_alarm_critical` has no items in 2 languages: en, id | add about 20 per language across 4+ apps (40 items) |
| high | shortcut | 25 apps always carry the same label (Spotify (muted), Netflix (muted), Google Maps (urgent), Amazon Shopping (muted), Waze (urgent), GitHub (urgent), Clash of Clans (muted), Todoist (urgent)...), so a model could learn the app instead of the text | add counter-examples per app (a muted Maps or GitHub notification, a Spotify item that is not a recommendation) and hold some apps out |
| high | structural | No training set yet (about 72,000 items planned) | author about 24,000 base items, then deterministic augmentation |
| high | structural | Test and dev are at 1072 items against about 8,100 planned | author about 450 per language (300 test, 150 dev) in rounds with blind relabeling |
| high | structural | No held-out apps (about 15% of apps never used in training) and no challenge sets yet | split the catalogue now, then author the 2,100-item challenge sets |
| medium | calls | `call_unknown` has fewer than 2 items in 17 languages: ar, bn, de, es, fr, gu, hi, hi-Latn, id, ja, kn, mr, pt-BR, ru, ta, te, zh-Hans | add about 15 per language (255 items) |
| medium | calls | `call_spam_likely` has fewer than 2 items in 16 languages: ar, bn, de, es, fr, gu, hi, hi-Latn, ja, kn, mr, pt-BR, ru, ta, te, zh-Hans | add about 15 per language (240 items) |
| medium | calls | `call_scam` has fewer than 2 items in 17 languages: ar, bn, de, en, es, fr, gu, hi, id, ja, kn, mr, pt-BR, ru, ta, te, zh-Hans | add about 15 per language (255 items) |
| medium | calls | `call_missed_family` has fewer than 2 items in 11 languages: ar, bn, de, gu, hi, kn, mr, ru, ta, te, zh-Hans | add about 15 per language (165 items) |
| medium | coverage | Use case `visitor_gate` appears in only 7 of 18 languages (7 items) | add about 12 per language in the missing languages |
| medium | coverage | Use case `voicemail_routine` appears in only 6 of 18 languages (6 items) | add about 12 per language in the missing languages |
| medium | coverage | Use case `investment_alert` appears in only 6 of 18 languages (6 items) | add about 12 per language in the missing languages |
| medium | coverage | Use case `caption_status` appears in only 1 of 18 languages (1 items) | add about 12 per language in the missing languages |
| medium | coverage | Use case `system_routine` appears in only 7 of 18 languages (7 items) | add about 12 per language in the missing languages |
| medium | coverage | Use case `telecom_gov_routine` appears in only 4 of 18 languages (4 items) | add about 12 per language in the missing languages |
| medium | diversity | Texts are short: median 65 characters, 4% over 120, none over 200 | add longer, group-chat and multi-sentence items (the plan's 300-item long and group challenge set) |
| medium | diversity | Only 102 of 252 catalogue apps are used; about 95 regional apps were added after authoring started | author round 2 around the regional apps for each language (Japan, Russia, Brazil, Indonesia, Germany, France, Gulf, China, India) |
| medium | quality | Near-miss items are often filed under the urgent use case (found in the blind relabel) | add a validator warning for near_miss rows whose use case is urgent, and brief authors again |
| medium | rubric | Rubric conflicts: vip_ping (Commute+Focus) vs 'workplace urgency is Focus only'; booking disruption; clinic 'urgent consult' wording | decide the label for these boundary cases (see contested file) and update rubric.json |
| medium | diagnosis | Model baselines not run on golden_v2 yet (keyword and current embedding model) | port the keyword engine and run both on dev/test to find areas where both fail |

## 11. Contested items

7 items are kept out of scoring until you decide their label (file `testdata\golden_v2\contested_pilot.jsonl`).

