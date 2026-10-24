# Coverage and weakness report

Generated from `ml/review/combined_items`: **3028 items**, 18 languages. This is a count-based check of what the data covers; it does not involve any model.

## 1. Size against the plan

Plan per language: 300 test + 150 dev + about 3800 training (the training set and challenge sets are not authored yet).

| lang | language | items now | test | dev | of test+dev plan |
|---|---|---|---|---|---|
| ar | Arabic | 158 | 108 | 50 | 35% |
| bn | Bengali | 160 | 110 | 50 | 36% |
| de | German | 159 | 112 | 47 | 35% |
| en | English | 213 | 147 | 66 | 47% |
| es | Spanish | 192 | 130 | 62 | 43% |
| fr | French | 156 | 109 | 47 | 35% |
| gu | Gujarati | 161 | 112 | 49 | 36% |
| hi | Hindi | 158 | 110 | 48 | 35% |
| hi-Latn | Hinglish | 201 | 138 | 63 | 45% |
| id | Indonesian | 190 | 128 | 62 | 42% |
| ja | Japanese | 153 | 106 | 47 | 34% |
| kn | Kannada | 165 | 111 | 54 | 37% |
| mr | Marathi | 153 | 108 | 45 | 34% |
| pt-BR | Portuguese (Brazil) | 156 | 109 | 47 | 35% |
| ru | Russian | 179 | 127 | 52 | 40% |
| ta | Tamil | 157 | 106 | 51 | 35% |
| te | Telugu | 159 | 105 | 54 | 35% |
| zh-Hans | Chinese (Simplified) | 158 | 110 | 48 | 35% |

## 2. Use-case coverage across languages

`urgent in` uses C F D S W for Commute, Focus, DND, Sleeping, Workout. Use cases that appear in fewer than half the languages are flagged below.

| use case | family | urgent in | items | languages with it | missing in |
|---|---|---|---|---|---|
| chat_casual | personal_life | - | 22 | 17 | te |
| chat_family_routine | personal_life | - | 27 | 16 | en,id |
| family_distress | personal_life | C,D,S | 36 | 18 |  |
| vip_ping | personal_life | C,F | 22 | 18 |  |
| group_chatter | community_group | - | 59 | 18 |  |
| group_forward | community_group | - | 38 | 18 |  |
| group_event_info | community_group | - | 30 | 18 |  |
| community_safety_notice | community_group | C,F,D,S,W | 31 | 18 |  |
| visitor_gate | community_group | - | 8 | 8 | 10 languages |
| call_missed_family | phone_calls | C,D,S | 262 | 18 |  |
| call_missed_contact | phone_calls | - | 24 | 17 | en |
| call_unknown | phone_calls | - | 268 | 18 |  |
| call_spam_likely | phone_calls | - | 267 | 18 |  |
| call_scam | phone_calls | - | 269 | 18 |  |
| voicemail_routine | phone_calls | - | 6 | 6 | 12 languages |
| promo_sale | spam_scam | - | 68 | 18 |  |
| marketing_push | spam_scam | - | 26 | 17 | mr |
| scam_phishing | spam_scam | - | 28 | 18 |  |
| scam_prize_loan | spam_scam | - | 25 | 18 |  |
| scam_fake_delivery | spam_scam | - | 17 | 16 | en,te |
| weather_severe | emergency_broadcast | C,F,D,S,W | 28 | 18 |  |
| earthquake_alert | emergency_broadcast | C,F,D,S,W | 21 | 18 |  |
| tsunami_flood_wildfire | emergency_broadcast | C,F,D,S,W | 83 | 18 |  |
| public_safety_alert | emergency_broadcast | C,F,D,S,W | 56 | 18 |  |
| civic_advisory | emergency_broadcast | - | 28 | 17 | bn |
| road_closure | emergency_broadcast | C | 22 | 16 | en,hi-Latn |
| payment_routine | banking_payments | - | 106 | 18 |  |
| investment_alert | banking_payments | - | 8 | 8 | 10 languages |
| ride_arriving | delivery_ride | C | 47 | 18 |  |
| ride_other | delivery_ride | - | 23 | 17 | en |
| delivery_update | delivery_ride | - | 73 | 18 |  |
| sub_renewal | subscriptions | - | 29 | 18 |  |
| sub_billing_issue | subscriptions | - | 29 | 18 |  |
| rec_music | media_recommendations | - | 39 | 18 |  |
| rec_video | media_recommendations | - | 49 | 18 |  |
| sound_alert_critical | live_captions_accessibility | C,F,D,S,W | 19 | 18 |  |
| sound_alert_routine | live_captions_accessibility | - | 22 | 18 |  |
| caption_status | live_captions_accessibility | - | 1 | 1 | 17 languages |
| meeting_soon | work | F | 20 | 18 |  |
| deadline | work | F | 18 | 16 | hi-Latn,id |
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
| work_tool_routine | work | - | 74 | 18 |  |
| news_breaking_general | news_weather_sports | - | 18 | 15 | id,mr,ta |
| weather_routine | news_weather_sports | - | 24 | 17 | de |
| sports_update | news_weather_sports | - | 18 | 16 | hi-Latn,id |
| social_activity | social | - | 30 | 16 | es,id |
| workout_prompt | health_fitness | W | 16 | 15 | bn,hi-Latn,id |
| health_critical | health_fitness | D,S,W | 19 | 18 |  |
| health_routine | health_fitness | - | 47 | 18 |  |
| game_notice | games_education | - | 29 | 18 |  |
| learning_notice | games_education | - | 13 | 11 | 7 languages |
| system_routine | system | - | 7 | 7 | 11 languages |
| telecom_gov_routine | government_telecom | - | 31 | 13 | ar,de,en,es,fr |
| school_urgent | school_family | C,D,S | 24 | 18 |  |
| school_info | school_family | - | 17 | 13 | ar,en,hi,kn,pt-BR |
| medical_urgent | medical | C,D,S | 23 | 18 |  |
| medical_routine | medical | - | 19 | 15 | ar,en,ja |

## 3. Use-case family share against the plan

| family | now | plan | difference (points) |
|---|---|---|---|
| personal_life | 3.5% | 13% | -9.5 |
| community_group | 5.5% | 9% | -3.5 |
| phone_calls | 36.2% | 8% | +28.2 |
| spam_scam | 5.4% | 13% | -7.6 |
| emergency_broadcast | 7.9% | 6% | +1.9 |
| banking_payments | 3.8% | 7% | -3.2 |
| delivery_ride | 4.7% | 8% | -3.3 |
| subscriptions | 1.9% | 4% | -2.1 |
| media_recommendations | 2.9% | 5% | -2.1 |
| social | 1.0% | 5% | -4.0 |
| work | 7.2% | 6% | +1.2 |
| smart_home_security | 2.9% | 3% | -0.1 |
| live_captions_accessibility | 1.4% | 2% | -0.6 |
| news_weather_sports | 2.0% | 4% | -2.0 |
| travel | 5.6% | 2% | +3.6 |
| health_fitness | 2.7% | 1.5% | +1.2 |
| games_education | 1.4% | 2% | -0.6 |
| system | 0.2% | 1.5% | -1.3 |

Families not in the plan: medical (42), school_family (41), government_telecom (31)

## 4. Label balance (share of items shown)

| lang | urgent in any mode | Commute | Deep Work / Focus | Do Not Disturb | Sleeping / Rest | Workout / Gym |
|---|---|---|---|---|---|---|
| ar | 30% | 25% | 9% | 20% | 20% | 6% |
| bn | 27% | 23% | 9% | 19% | 19% | 6% |
| de | 28% | 23% | 10% | 19% | 19% | 8% |
| en | 39% | 36% | 25% | 34% | 34% | 24% |
| es | 38% | 33% | 20% | 29% | 29% | 19% |
| fr | 27% | 22% | 8% | 17% | 17% | 6% |
| gu | 25% | 21% | 8% | 17% | 17% | 6% |
| hi | 27% | 22% | 8% | 18% | 18% | 6% |
| hi-Latn | 35% | 32% | 20% | 29% | 29% | 18% |
| id | 35% | 32% | 19% | 29% | 29% | 18% |
| ja | 30% | 25% | 11% | 21% | 21% | 8% |
| kn | 26% | 22% | 8% | 18% | 18% | 6% |
| mr | 27% | 22% | 9% | 18% | 18% | 7% |
| pt-BR | 28% | 24% | 8% | 20% | 20% | 6% |
| ru | 33% | 28% | 15% | 23% | 23% | 12% |
| ta | 26% | 22% | 9% | 17% | 17% | 7% |
| te | 25% | 21% | 8% | 17% | 17% | 6% |
| zh-Hans | 30% | 24% | 11% | 20% | 20% | 7% |

## 5. Difficulty and sender mix

Difficulty: easy 2276 (75%), near_miss 409 (14%), hard_negative 343 (11%).

Sender type: service 1216, spam 651, contact 460, unknown 271, system 259, group 171.

## 6. Safety-critical coverage per language (items per use case)

| lang | weather_severe | earthquake_alert | tsunami_flood_wildfire | public_safety_alert | community_safety_notice | home_alarm_critical | sound_alert_critical |
|---|---|---|---|---|---|---|---|
| ar | 1 | 1 | 1 | 1 | 2 | 1 | 1 |
| bn | 1 | 1 | 1 | 1 | 2 | 1 | 1 |
| de | 2 | 1 | 1 | 1 | 3 | 1 | 1 |
| en | 1 | 2 | 14 | 14 | 2 | 14 | 2 |
| es | 1 | 1 | 14 | 13 | 2 | 1 | 1 |
| fr | 1 | 1 | 1 | 1 | 1 | 1 | 1 |
| gu | 2 | 1 | 1 | 1 | 1 | 1 | 1 |
| hi | 1 | 1 | 1 | 1 | 2 | 1 | 1 |
| hi-Latn | 2 | 2 | 14 | 14 | 2 | 1 | 1 |
| id | 1 | 1 | 14 | 1 | 1 | 14 | 1 |
| ja | 3 | 2 | 1 | 1 | 2 | 1 | 1 |
| kn | 2 | 1 | 1 | 1 | 1 | 1 | 1 |
| mr | 2 | 1 | 1 | 1 | 2 | 1 | 1 |
| pt-BR | 2 | 1 | 1 | 1 | 1 | 1 | 1 |
| ru | 1 | 1 | 14 | 1 | 1 | 1 | 1 |
| ta | 2 | 1 | 1 | 1 | 2 | 1 | 1 |
| te | 1 | 1 | 1 | 1 | 2 | 1 | 1 |
| zh-Hans | 2 | 1 | 1 | 1 | 2 | 1 | 1 |

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

Distinct apps used: **230 of 252** in the catalogue. Per language: ar 50, bn 72, de 59, en 80, es 62, fr 59, gu 75, hi 72, hi-Latn 77, id 59, ja 46, kn 71, mr 68, pt-BR 63, ru 62, ta 70, te 68, zh-Hans 60.

App categories with no items: none.

Apps whose items all carry one label (a model could learn the app instead of the text): 47. Largest: Netflix (27, muted), Spotify (25, muted), Amazon Shopping (19, muted), Clash of Clans (16, muted), Disney+ Hotstar (14, muted), Snapchat (12, muted), Flipkart (12, muted), Instagram (11, muted), Duolingo (10, muted), Gaana (9, muted).

## 9. Text length (characters)

median 65, 90th percentile 116, longest 286; under 40 characters: 518 items, over 200: 33 items.

## 10. Ranked gaps and proposed fixes

| severity | kind | gap | proposed fix |
|---|---|---|---|
| high | shortcut | 47 apps always carry the same label (Netflix (muted), Spotify (muted), Amazon Shopping (muted), Clash of Clans (muted), Disney+ Hotstar (muted), Snapchat (muted), Flipkart (muted), Instagram (muted)...), so a model could learn the app instead of the text | add counter-examples per app (a muted Maps or GitHub notification, a Spotify item that is not a recommendation) and hold some apps out |
| high | structural | No training set yet (about 72,000 items planned) | author about 24,000 base items, then deterministic augmentation |
| high | structural | Test and dev are at 3028 items against about 8,100 planned | author about 450 per language (300 test, 150 dev) in rounds with blind relabeling |
| high | structural | No held-out apps (about 15% of apps never used in training) and no challenge sets yet | split the catalogue now, then author the 2,100-item challenge sets |
| medium | coverage | Use case `visitor_gate` appears in only 8 of 18 languages (8 items) | add about 12 per language in the missing languages |
| medium | coverage | Use case `voicemail_routine` appears in only 6 of 18 languages (6 items) | add about 12 per language in the missing languages |
| medium | coverage | Use case `investment_alert` appears in only 8 of 18 languages (8 items) | add about 12 per language in the missing languages |
| medium | coverage | Use case `caption_status` appears in only 1 of 18 languages (1 items) | add about 12 per language in the missing languages |
| medium | coverage | Use case `system_routine` appears in only 7 of 18 languages (7 items) | add about 12 per language in the missing languages |
| medium | diversity | Texts are short: median 65 characters, 9% over 120, none over 200 | add longer, group-chat and multi-sentence items (the plan's 300-item long and group challenge set) |
| medium | diversity | Only 230 of 252 catalogue apps are used; about 95 regional apps were added after authoring started | author round 2 around the regional apps for each language (Japan, Russia, Brazil, Indonesia, Germany, France, Gulf, China, India) |
| medium | quality | Near-miss items are often filed under the urgent use case (found in the blind relabel) | add a validator warning for near_miss rows whose use case is urgent, and brief authors again |
| medium | rubric | Rubric conflicts: vip_ping (Commute+Focus) vs 'workplace urgency is Focus only'; booking disruption; clinic 'urgent consult' wording | decide the label for these boundary cases (see contested file) and update rubric.json |
| medium | diagnosis | Model baselines not run on golden_v2 yet (keyword and current embedding model) | port the keyword engine and run both on dev/test to find areas where both fail |

## 11. Contested items

0 items are kept out of scoring until you decide their label (file `ml/review/contested_combined.jsonl`).

